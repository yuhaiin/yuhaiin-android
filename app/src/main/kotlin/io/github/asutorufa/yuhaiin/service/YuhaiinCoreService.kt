package io.github.asutorufa.yuhaiin.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.RemoteCallbackList
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.IYuhaiinVpnCallback
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import yuhaiin.App
import yuhaiin.Closer
import yuhaiin.Opts
import yuhaiin.TUN
import yuhaiin.Yuhaiin

/**
 * Owns the Go core in the ":bg" process: DNS, FakeIP, routing, nodes, inbounds and the HTTP/HTTPS
 * management server run here whether or not the Android VPN is up. The VPN is the core's TUN
 * inbound; enabling it makes the core ask [VpnCoordinator] for an interface.
 *
 * The service is foreground while the core runs. With the "while_needed" policy the core stops
 * when the VPN is off and no UI is attached; "always" keeps it running for LAN/proxy use.
 */
class YuhaiinCoreService : android.app.Service() {
    companion object {
        const val START_CORE = "io.github.asutorufa.yuhaiin.START_CORE"
        const val STOP_CORE = "io.github.asutorufa.yuhaiin.STOP_CORE"
        private const val NOTIFICATION_ID = 1
        private const val IDLE_STOP_MS = 60_000L
        private val RESTART_BACKOFF_MS = longArrayOf(2_000, 5_000, 15_000)

        fun intent(context: Context, action: String): Intent =
            Intent(context, YuhaiinCoreService::class.java).setAction(action)

        /** Starts the core service from a context that may start a foreground service. */
        fun start(context: Context, action: String = START_CORE) {
            runCatching { ContextCompat.startForegroundService(context, intent(context, action)) }
                .onFailure { Log.w("YuhaiinCoreService", "start $action failed", it) }
        }

        /** Pure policy, unit tested: whether the core must keep running. */
        internal fun coreNeeded(
            policy: String,
            uiAttached: Boolean,
            vpnEnabled: Boolean,
            paused: Boolean,
        ): Boolean = policy == "always" || uiAttached || vpnEnabled || paused
    }

    private val tag = "YuhaiinCoreService"
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val commands = Mutex()
    private val app = App()
    private val notification by lazy { VpnNotification(this) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val alarm by lazy { getSystemService(AlarmManager::class.java) }
    private val pausePreferences by lazy { getSharedPreferences("vpn_pause", MODE_PRIVATE) }
    private val sessionPreferences by lazy {
        getSharedPreferences("vpn_session_summary", MODE_PRIVATE)
    }

    @Volatile private var status = VpnStatus()
    @Volatile private var coreError: String? = null
    @Volatile private var lastError: String? = null
    private var coreEpoch = 0L
    private var userStopped = false
    private var restartAttempt = 0
    private var restartJob: Job? = null
    private var idleJob: Job? = null
    private var resumeJob: Job? = null
    private var statusJob: Job? = null
    private var healthJob: Job? = null
    private var healthGeneration = 0L
    private var lastHealthAttempt = 0L
    private var samplingInterval = 0L
    private var foreground = false
    private var publishedWidget: VpnWidget.Companion.Display? = null

    private val callbacks =
        object : RemoteCallbackList<IYuhaiinVpnCallback>() {
            override fun onCallbackDied(callback: IYuhaiinVpnCallback) {
                scope.launch {
                    refreshMonitoring()
                    evaluateIdle()
                }
            }
        }

    private val monitoringReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshMonitoring()
                publish()
            }
        }

    // ---- lifecycle ----

    override fun onCreate() {
        super.onCreate()
        VpnCoordinator.init(application)
        Yuhaiin.setAppCatalog(InstalledAppCatalog)
        Yuhaiin.setPlatformActions(
            AndroidPlatformActions(
                this,
                object : AndroidPlatformActions.CoreControl {
                    override fun restart(between: (suspend () -> Unit)?) {
                        scope.launch { commands.withLock { restartCore(between) } }
                    }

                    override fun disableVpn() {
                        scope.launch {
                            commands.withLock {
                                setResumeAt(0)
                                if (app.running()) setVpn(false)
                            }
                        }
                    }
                },
            )
        )
        val sameBoot = pausePreferences.getInt("bootCount", -1) == bootCount()
        status =
            status.copy(
                nativeStatus = sessionPreferences.getString("summary", "").orEmpty(),
                resumeAt = if (sameBoot) pausePreferences.getLong("resumeAt", 0) else 0,
            )
        if (!sameBoot) pausePreferences.edit().clear().apply()
        ContextCompat.registerReceiver(
            this,
            monitoringReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(VpnActions.REFRESH_MONITORING)
                if (Build.VERSION.SDK_INT >= 28) {
                    addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
                    addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                }
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { VpnCoordinator.status.collect { onPlatformStatus(it) } }
        VpnCoordinator.networkListener = { scope.launch { onNetworkChanged() } }
    }

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() callers require prompt promotion, even for STOP_CORE.
        enterForeground()
        val action = intent?.action ?: START_CORE
        scope.launch { commands.withLock { handle(action) } }
        return if (action == STOP_CORE) START_NOT_STICKY else START_STICKY
    }

    private suspend fun handle(action: String) {
        Log.d(tag, "command $action")
        // As in v1, no VPN request from a background entry point bypasses the disclosure.
        if (
            action in setOf(VpnActions.CONNECT, VpnActions.CONNECT_ALWAYS_ON, VpnActions.RECONNECT, VpnActions.RESUME) &&
                !PrivacyConsent.forContext(this).has(Purpose.VPN)
        ) {
            setResumeAt(0)
            sendMessage(getString(R.string.vpn_disclosure_required))
            ensureCore()
            evaluateIdle()
            return
        }
        when (action) {
            STOP_CORE -> stopCore(user = true)
            VpnActions.DISCONNECT -> {
                setResumeAt(0)
                if (ensureCore()) setVpn(false)
            }
            VpnActions.CONNECT,
            VpnActions.CONNECT_ALWAYS_ON -> {
                setResumeAt(0)
                if (ensureCore()) setVpn(true)
            }
            VpnActions.RECONNECT -> {
                setResumeAt(0)
                if (ensureCore()) {
                    if (app.vpnEnabled()) Yuhaiin.requestVpnReconfigure() else setVpn(true)
                }
            }
            VpnActions.RESUME -> {
                if (status.resumeAt > 0 && System.currentTimeMillis() >= status.resumeAt) {
                    setResumeAt(0)
                    if (ensureCore()) setVpn(true)
                }
            }
            else -> {
                if (status.resumeAt > System.currentTimeMillis()) setResumeAt(status.resumeAt)
                // Always report the current state: a caller may be waiting for it even
                // when the core was already running.
                if (ensureCore()) setCore(CoreState.RUNNING)
            }
        }
        evaluateIdle()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(monitoringReceiver) }
        VpnCoordinator.networkListener = null
        restartJob?.cancel()
        idleJob?.cancel()
        resumeJob?.cancel()
        statusJob?.cancel()
        healthJob?.cancel()
        coreEpoch++
        if (app.running()) {
            saveSummaryBlocking()
            runCatching { app.stop() }.onFailure { Log.w(tag, "stop core during destroy", it) }
        }
        VpnCoordinator.releaseAll()
        VpnCoordinator.stopMonitoring()
        VpnWidget.update(this, VpnStatus())
        callbacks.kill()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun enterForeground() {
        notification.createChannel()
        val built = notification.builder(status).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(NOTIFICATION_ID, built, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, built)
        foreground = true
        notification.posted(status)
    }

    // ---- core ----

    /** Starts the core if needed. Returns whether it is running. */
    private suspend fun ensureCore(): Boolean {
        if (app.running()) return true
        userStopped = false
        restartJob?.cancel()
        coreError = null
        setCore(CoreState.STARTING)
        val epoch = ++coreEpoch
        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    app.start(
                        Opts().apply {
                            tun =
                                TUN().apply {
                                    socketProtect = VpnCoordinator.socketProtect
                                    socketBind = VpnCoordinator.socketBind
                                }
                            closeFallback = Closer {
                                scope.launch { commands.withLock { onCoreClosed(epoch) } }
                            }
                        }
                    )
                }
            }
        if (epoch != coreEpoch) return app.running()
        return result.fold(
            onSuccess = {
                restartAttempt = 0
                VpnCoordinator.startMonitoring()
                setCore(CoreState.RUNNING)
                refreshMonitoring()
                true
            },
            onFailure = {
                Log.e(tag, "core start failed", it)
                coreError = it.message ?: it.toString()
                VpnCoordinator.releaseAll()
                setCore(CoreState.FAILED)
                false
            },
        )
    }

    private suspend fun stopCore(user: Boolean) {
        userStopped = user
        restartJob?.cancel()
        idleJob?.cancel()
        setResumeAt(0)
        if (app.running()) {
            setCore(CoreState.STOPPING)
            saveSummary()
            coreEpoch++
            withContext(Dispatchers.IO) { runCatching { app.stop() } }
                .onFailure { Log.w(tag, "stop core", it) }
        }
        // The core closes its TUN inbound; release anything a crash left behind.
        VpnCoordinator.releaseAll()
        VpnCoordinator.stopMonitoring()
        setCore(CoreState.STOPPED)
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    /**
     * Stops and starts the core in place (the service and its notification stay), running
     * [between] while the core is down, e.g. a backup restore that needs the configuration lock.
     * The persisted TUN inbound brings the VPN back.
     */
    private suspend fun restartCore(between: (suspend () -> Unit)?) {
        if (app.running()) {
            setCore(CoreState.STOPPING)
            saveSummary()
            coreEpoch++
            withContext(Dispatchers.IO) { runCatching { app.stop() } }
                .onFailure { Log.w(tag, "stop core for restart", it) }
            VpnCoordinator.releaseAll()
        }
        if (between != null)
            withContext(Dispatchers.IO) { runCatching { between() } }
                .onFailure {
                    Log.w(tag, "work while the core was stopped failed", it)
                    sendMessage(it.message ?: it.toString())
                }
        ensureCore()
    }

    /** The core exited without a stop request (crash or fatal listener error). */
    private fun onCoreClosed(epoch: Long) {
        if (epoch != coreEpoch || userStopped) return
        Log.w(tag, "core closed unexpectedly")
        VpnCoordinator.releaseAll()
        coreError = getString(R.string.core_start_failed)
        setCore(CoreState.FAILED)
        if (restartAttempt >= RESTART_BACKOFF_MS.size) return
        val wait = RESTART_BACKOFF_MS[restartAttempt++]
        restartJob = scope.launch {
            delay(wait)
            commands.withLock {
                // The persisted TUN inbound state restores the VPN if it was enabled.
                if (!userStopped) ensureCore()
            }
        }
    }

    private fun setCore(next: CoreState) {
        status = status.copy(coreState = next)
        publish()
        val port = if (next == CoreState.RUNNING) app.httpPort() else 0
        broadcast { it.onCoreChanged(next.ordinal, port) }
    }

    // ---- VPN ----

    private suspend fun setVpn(enabled: Boolean) {
        if (enabled) {
            val missing =
                when {
                    !PrivacyConsent.forContext(this).has(Purpose.VPN) ->
                        getString(R.string.vpn_disclosure_required)
                    VpnService.prepare(this) != null -> getString(R.string.vpn_permission_needed)
                    else -> null
                }
            if (missing != null) {
                // Never show a permission dialog from the background; the UI asks the user.
                sendMessage(missing)
                return
            }
        }
        lastError = null
        // Keep v1's end-of-session summary for the dashboard/widget when the VPN stops.
        if (!enabled && status.state == State.CONNECTED) saveSummary()
        withContext(Dispatchers.IO) { runCatching { app.setVpnEnabled(enabled) } }
            .onFailure {
                Log.w(tag, "set VPN $enabled failed", it)
                sendMessage(it.message ?: it.toString())
            }
    }

    private var connectedGeneration = 0L

    private fun onPlatformStatus(platform: PlatformVpnStatus) {
        val next = platform.vpnState()
        val before = status.state
        val applied = platform.applied
        status =
            status.copy(
                state = next,
                connectedAt =
                    when {
                        next != State.CONNECTED -> 0
                        // A new interface generation is a new connection (reconnect, rebuild).
                        before == State.CONNECTED &&
                            status.connectedAt > 0 &&
                            platform.generation == connectedGeneration -> status.connectedAt
                        else -> SystemClock.elapsedRealtime()
                    },
                mtu = applied?.mtu ?: 0,
                ipv4 = applied?.ipv4.orEmpty(),
                ipv6 = applied?.ipv6.orEmpty(),
                route = MainApplication.store.getString(Constants.ROUTE_KEY).ifBlank {
                    Constants.ALL_ROUTE
                },
            )
        connectedGeneration = if (next == State.CONNECTED) platform.generation else 0
        refreshNetworkStatus()
        if (platform.state == "failed" || platform.state == "revoked") {
            if (platform.lastError.isNotBlank()) sendMessage(platform.lastError)
        }
        if (before != next) {
            broadcast { it.onStateChanged(next.ordinal) }
            if (next == State.CONNECTED) {
                invalidateHealth()
                lastHealthAttempt = 0
            }
        }
        publish()
        refreshMonitoring()
        scope.launch { evaluateIdle() }
    }

    private fun onNetworkChanged() {
        refreshNetworkStatus()
        publish()
        if (status.state == State.CONNECTED) {
            invalidateHealth()
            if (hasVisibleUi()) checkHealth()
        }
    }

    private fun refreshNetworkStatus() {
        val connectivity = (application as MainApplication).connectivity
        val caps =
            VpnCoordinator.underlyingNetwork?.let(connectivity::getNetworkCapabilities)
                ?: connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val network =
            when {
                caps == null -> ""
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    getString(R.string.network_wifi)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    getString(R.string.network_cellular)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    getString(R.string.network_ethernet)
                else -> getString(R.string.network_other)
            }
        status = status.copy(network = network)
    }

    // ---- pause / resume ----

    private fun bootCount() =
        android.provider.Settings.Global.getInt(
            contentResolver,
            android.provider.Settings.Global.BOOT_COUNT,
            -1,
        )

    private fun setResumeAt(deadline: Long) {
        resumeJob?.cancel()
        resumeJob = null
        alarm.cancel(VpnActions.service(this, VpnActions.RESUME))
        pausePreferences
            .edit()
            .putLong("resumeAt", deadline)
            .putInt("bootCount", bootCount())
            .apply()
        status = status.copy(resumeAt = deadline)
        if (deadline > 0) {
            // An inexact alarm wakes the timer in idle without Alarms & reminders access.
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadline,
                VpnActions.service(this, VpnActions.RESUME),
            )
            resumeJob = scope.launch {
                delay((deadline - System.currentTimeMillis()).coerceAtLeast(0))
                commands.withLock {
                    if (status.resumeAt == deadline) {
                        // Detach first: setResumeAt cancels resumeJob, which is this job,
                        // and the suspending reconnect below would then be cancelled.
                        resumeJob = null
                        setResumeAt(0)
                        if (ensureCore()) setVpn(true)
                    }
                }
            }
        }
        publish()
    }

    private fun snooze(minutes: Int) {
        if (minutes !in listOf(5, 15, 30)) return
        scope.launch {
            commands.withLock {
                if (status.state != State.CONNECTED) return@withLock
                setResumeAt(System.currentTimeMillis() + minutes * 60_000L)
                setVpn(false)
            }
        }
    }

    // ---- background policy ----

    private suspend fun evaluateIdle() {
        val policy =
            MainApplication.store.getString(Yuhaiin.CoreBackgroundPolicyKey).ifBlank {
                "while_needed"
            }
        val vpnEnabled =
            app.running() &&
                withContext(Dispatchers.IO) { runCatching { app.vpnEnabled() }.getOrDefault(false) }
        val needed =
            coreNeeded(
                policy,
                callbacks.registeredCallbackCount > 0,
                vpnEnabled,
                status.resumeAt > 0,
            )
        if (needed || !app.running()) {
            idleJob?.cancel()
            idleJob = null
            return
        }
        if (idleJob?.isActive == true) return
        idleJob = scope.launch {
            delay(IDLE_STOP_MS)
            commands.withLock {
                Log.i(tag, "stopping idle core (VPN off, no UI)")
                stopCore(user = false)
            }
        }
    }

    // ---- status publishing ----

    private fun publish() {
        val widget = VpnWidget.display(this, status)
        if (widget != publishedWidget) {
            VpnWidget.update(this, status)
            publishedWidget = widget
        }
        if (foreground) notification.update(status)
    }

    private fun sendMessage(msg: String) {
        lastError = msg
        broadcast { it.onMsg(msg) }
    }

    private fun broadcast(block: (IYuhaiinVpnCallback) -> Unit) {
        val n = callbacks.beginBroadcast()
        try {
            for (i in 0 until n) runCatching { block(callbacks.getBroadcastItem(i)) }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private fun hasVisibleUi() = power.isInteractive && callbacks.registeredCallbackCount > 0

    private fun refreshMonitoring() {
        val interval =
            when {
                !app.running() || !power.isInteractive -> 0L
                hasVisibleUi() -> 2500L
                status.state == State.CONNECTED &&
                    (notification.needsSpeedUpdates() || VpnWidget.isInstalled(this)) -> 10_000L
                else -> 0L
            }
        if (interval == samplingInterval) return
        samplingInterval = interval
        statusJob?.cancel()
        statusJob = null
        status = status.copy(speed = "")
        publish()
        if (interval == 0L) return
        val epoch = coreEpoch
        statusJob = scope.launch {
            val rates = TrafficRateSampler()
            while (epoch == coreEpoch && app.running()) {
                val snapshot = withContext(Dispatchers.IO) { runCatching { app.nativeStatus() } }
                if (epoch != coreEpoch) break
                snapshot
                    .onSuccess { value ->
                        val after = decodeNativeStatus(value)
                        val speed = after?.let { rates.sample(it, SystemClock.elapsedRealtime()) }
                        status =
                            status.copy(
                                nativeStatus = if (hasVisibleUi()) value else status.nativeStatus,
                                speed = speed ?: status.speed,
                            )
                        publish()
                    }
                    .onFailure { Log.w(tag, "Unable to read core status", it) }
                if (
                    hasVisibleUi() &&
                        (status.nativeHealth.isBlank() ||
                            SystemClock.elapsedRealtime() - lastHealthAttempt >= 60_000)
                ) checkHealth()
                delay(interval)
            }
        }
    }

    private fun invalidateHealth() {
        healthGeneration++
        healthJob?.cancel()
        healthJob = null
        status = status.copy(nativeHealth = "", healthError = "", healthChecking = false)
    }

    private fun checkHealth() {
        if (!app.running() || !hasVisibleUi()) return
        if (healthJob?.isActive == true) return
        lastHealthAttempt = SystemClock.elapsedRealtime()
        val generation = ++healthGeneration
        val epoch = coreEpoch
        status = status.copy(healthChecking = true, healthError = "")
        healthJob = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { app.checkHealth() } }
            if (epoch != coreEpoch || generation != healthGeneration) return@launch
            status =
                result.fold(
                    onSuccess = { status.copy(nativeHealth = it, healthChecking = false) },
                    onFailure = {
                        status.copy(
                            nativeHealth = "",
                            healthChecking = false,
                            healthError = it.message.orEmpty(),
                        )
                    },
                )
        }
    }

    private suspend fun saveSummary() {
        if (!app.running()) return
        val summary = withContext(Dispatchers.IO) { runCatching { app.nativeStatus() }.getOrNull() }
        if (summary != null) {
            status = status.copy(nativeStatus = summary)
            withContext(Dispatchers.IO) {
                sessionPreferences.edit().putString("summary", summary).commit()
            }
        }
    }

    private fun saveSummaryBlocking() {
        runCatching { sessionPreferences.edit().putString("summary", app.nativeStatus()).commit() }
    }

    // ---- Binder ----

    private val binder =
        object : IYuhaiinVpnBinder.Stub() {
            override fun registerCallback(cb: IYuhaiinVpnCallback?) {
                if (cb == null || !callbacks.register(cb)) return
                scope.launch {
                    refreshMonitoring()
                    evaluateIdle()
                }
                runCatching {
                    cb.onStateChanged(status.state.ordinal)
                    cb.onCoreChanged(
                        status.coreState.ordinal,
                        if (status.coreRunning) app.httpPort() else 0,
                    )
                    lastError?.let(cb::onMsg)
                }
            }

            override fun unregisterCallback(cb: IYuhaiinVpnCallback?) {
                if (cb != null) callbacks.unregister(cb)
                scope.launch {
                    refreshMonitoring()
                    evaluateIdle()
                }
            }

            override fun proxyGet(url: String?): ByteArray =
                Yuhaiin.proxyGet(url ?: throw IllegalArgumentException("proxy URL is empty"))

            override fun proxyDownload(url: String?, destination: String?) {
                Yuhaiin.proxyDownload(
                    url ?: throw IllegalArgumentException("proxy URL is empty"),
                    destination ?: throw IllegalArgumentException("proxy destination is empty"),
                )
            }

            override fun stop() {
                scope.launch {
                    commands.withLock {
                        setResumeAt(0)
                        if (app.running()) setVpn(false)
                        evaluateIdle()
                    }
                }
            }

            override fun state(): Int = status.state.ordinal

            override fun snapshot(): Bundle = status.toBundle()

            override fun snooze(minutes: Int) = this@YuhaiinCoreService.snooze(minutes)

            override fun checkHealth() {
                scope.launch { this@YuhaiinCoreService.checkHealth() }
            }

            override fun refreshMonitoring() {
                scope.launch { this@YuhaiinCoreService.refreshMonitoring() }
            }

            override fun session(): Bundle =
                Bundle().apply {
                    val running = app.running()
                    putInt("coreState", status.coreState.ordinal)
                    putString("coreError", coreError.orEmpty())
                    putInt("port", if (running) app.httpPort() else 0)
                    // The token never leaves this app: the service is not exported.
                    putString("token", if (running) app.platformToken() else "")
                }

            override fun startCore() {
                start(this@YuhaiinCoreService, START_CORE)
            }

            override fun stopCore() {
                scope.launch { commands.withLock { stopCore(user = true) } }
            }

            override fun setVpnEnabled(enabled: Boolean) {
                scope.launch {
                    commands.withLock {
                        setResumeAt(0)
                        if (ensureCore()) setVpn(enabled)
                        evaluateIdle()
                    }
                }
            }

            override fun vpnStatus(): String = Yuhaiin.vpnStatus()
        }
}

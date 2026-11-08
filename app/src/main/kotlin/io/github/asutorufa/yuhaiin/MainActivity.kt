package io.github.asutorufa.yuhaiin

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.asutorufa.yuhaiin.compose.Main
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import io.github.asutorufa.yuhaiin.service.CoreState
import io.github.asutorufa.yuhaiin.service.VpnActions
import io.github.asutorufa.yuhaiin.service.VpnStatus
import io.github.asutorufa.yuhaiin.service.YuhaiinCoreService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Management session of the running core, delivered over the in-app Binder only. */
data class CoreSession(
    val state: CoreState = CoreState.STOPPED,
    val port: Int = 0,
    val token: String = "",
    val error: String = "",
) {
    val ready: Boolean
        get() = state == CoreState.RUNNING && port > 0 && token.isNotEmpty()

    companion object {
        fun fromBundle(bundle: Bundle) =
            CoreSession(
                CoreState.entries.getOrNull(bundle.getInt("coreState")) ?: CoreState.STOPPED,
                bundle.getInt("port"),
                bundle.getString("token").orEmpty(),
                bundle.getString("coreError").orEmpty(),
            )
    }
}

class MainActivity : AppCompatActivity() {
    var vpnBinder: IYuhaiinVpnBinder? = null
    val state = MutableStateFlow(State.DISCONNECTED)
    val error = MutableStateFlow<String?>(null)
    private var serviceBound = false
    val status = MutableStateFlow(VpnStatus())
    val session = MutableStateFlow(CoreSession(state = CoreState.STARTING))
    val navigationAction = MutableStateFlow<String?>(null)
    val vpnDisclosure = MutableStateFlow(false)
    private var pendingAction: String? = null
    private var startingSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAction =
            if (savedInstanceState == null) intent?.action
            else savedInstanceState.getString("vpnAction")
        vpnDisclosure.value = savedInstanceState?.getBoolean("vpnDisclosure") ?: false
        preparingVpn = vpnDisclosure.value
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val snapshot =
                        withContext(Dispatchers.IO) {
                            runCatching { vpnBinder?.snapshot()?.let(VpnStatus::fromBundle) }
                                .getOrNull()
                        }
                    if (snapshot != null) status.value = snapshot
                    // Self-heal: never stay on the "starting" screen if a callback was missed.
                    if (session.value.state == CoreState.STARTING) {
                        if (vpnBinder != null) refreshSession()
                        else if (android.os.SystemClock.elapsedRealtime() - startingSince > 15_000)
                            session.value = CoreSession(state = CoreState.STOPPED)
                    }
                    delay(1000)
                }
            }
        }
        setContent { Main(this) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("vpnAction", pendingAction)
        outState.putBoolean("vpnDisclosure", vpnDisclosure.value)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAction = intent.action
        handleAction()
    }

    private fun handleAction() {
        if (vpnBinder == null) return
        val action = pendingAction ?: return
        pendingAction = null
        when (action) {
            VpnActions.CONNECT -> startService()
            VpnActions.DISCONNECT -> vpnBinder?.stop()
            VpnActions.HOME,
            VpnActions.DASHBOARD,
            VpnActions.SETTINGS,
            VpnActions.ROUTES -> navigationAction.value = action
        }
    }

    fun reconnect() {
        runCatching {
            startService(YuhaiinCoreService.intent(this, VpnActions.RECONNECT))
        }
    }

    override fun onStart() {
        super.onStart()
        MainApplication.settings.refresh()
        MainApplication.updateManager.resumePendingInstall()
        // The app is visible, so starting the foreground core service is allowed.
        startCore()
        bindCoreService()
    }

    /** Starts the core; the service keeps it running independently of the VPN. */
    fun startCore() {
        // Only show "starting" when the core is not already serving this UI.
        if (!session.value.ready) {
            session.value = session.value.copy(state = CoreState.STARTING, error = "")
            startingSince = android.os.SystemClock.elapsedRealtime()
        }
        YuhaiinCoreService.start(this)
    }

    /** Stops the core (and with it the VPN) and waits until the configuration is free. */
    suspend fun stopCoreAndWait() {
        val binder = vpnBinder ?: return
        withContext(Dispatchers.IO) { binder.stopCore() }
        kotlinx.coroutines.withTimeout(15_000) {
            session.first { it.state == CoreState.STOPPED || it.state == CoreState.FAILED }
        }
    }

    private fun bindCoreService() {
        if (serviceBound) return
        serviceBound =
            bindService(Intent(this, YuhaiinCoreService::class.java), mConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (serviceBound) {
            runCatching { vpnBinder?.unregisterCallback(vpnCallback) }
                .onFailure { Log.w("MainActivity", "failed to unregister callback", it) }
            // Keep UpdateManager's Binder reference alive so an in-flight update can finish.
            vpnBinder = null
            runCatching { unbindService(mConnection) }
                .onFailure { Log.w("MainActivity", "failed to unbind core service", it) }
            serviceBound = false
        }
        super.onStop()
    }

    private val sessionRequests = java.util.concurrent.atomic.AtomicLong()

    /** Re-reads the session. Only the newest request may publish, so a slow stale
     * answer (for example STARTING) can never overwrite a newer RUNNING one. */
    private fun refreshSession() {
        val binder = vpnBinder ?: return
        val request = sessionRequests.incrementAndGet()
        lifecycleScope.launch {
            val next =
                withContext(Dispatchers.IO) {
                    runCatching { CoreSession.fromBundle(binder.session()) }.getOrNull()
                }
            if (next != null && request == sessionRequests.get()) session.value = next
        }
    }

    val vpnCallback =
        object : IYuhaiinVpnCallback.Stub() {
            override fun onStateChanged(state: Int) {
                this@MainActivity.state.value = State.entries.getOrNull(state) ?: State.ERROR
                if (this@MainActivity.state.value == State.CONNECTING) error.value = null
            }

            override fun onMsg(msg: String?) {
                error.value = msg
                Log.i("yuhaiin core service", "onMsg: $msg")
            }

            override fun onCoreChanged(coreState: Int, port: Int) {
                // A restarted core may listen on another port and always has a new token.
                refreshSession()
            }
        }

    private fun disconnected() {
        vpnBinder = null
        MainApplication.updateManager.setProxyBinder(null)
        state.value = State.DISCONNECTED
        // The ":bg" process died; Android restarts the sticky core service, so show the
        // loading state. The polling loop falls back to "stopped" if it does not return.
        session.value = CoreSession(state = CoreState.STARTING)
        startingSince = android.os.SystemClock.elapsedRealtime()
        status.value =
            status.value.copy(
                state = State.DISCONNECTED,
                connectedAt = 0,
                speed = "",
                nativeHealth = "",
                healthChecking = false,
                coreState = CoreState.STOPPED,
            )
    }

    private val mConnection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
                val remote = IYuhaiinVpnBinder.Stub.asInterface(binder)
                // The ":bg" process can die (for example under memory pressure) between
                // binding and these calls; onServiceDisconnected and the restart follow.
                val ok =
                    runCatching {
                            state.value = State.entries.getOrNull(remote.state()) ?: State.ERROR
                            remote.registerCallback(vpnCallback)
                        }
                        .onFailure { Log.w("MainActivity", "core service died while binding", it) }
                        .isSuccess
                if (!ok) return
                vpnBinder = remote
                MainApplication.updateManager.setProxyBinder(remote)
                refreshSession()
                handleAction()
            }

            override fun onServiceDisconnected(p1: ComponentName) {
                // The :bg process died. The WebView process keeps running; the service restarts.
                disconnected()
            }

            override fun onBindingDied(name: ComponentName) {
                disconnected()
                if (serviceBound) {
                    runCatching { unbindService(this) }
                        .onFailure { Log.w("MainActivity", "failed to drop dead binding", it) }
                    serviceBound = false
                }
                startCore()
                bindCoreService()
            }
        }

    private val vpnPermissionDialogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                launchVpn()
            } else {
                error.value = getString(R.string.vpn_permission_cancelled)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { prepareVpn() }

    private var preparingVpn = false

    /**
     * User-initiated VPN connect: disclosure, notification permission, then the OS VPN dialog.
     * Only this Activity shows these dialogs; background components report "permission required".
     */
    fun startService() {
        if (preparingVpn) return
        preparingVpn = true
        error.value = null
        if (!PrivacyConsent.forContext(this).has(Purpose.VPN)) {
            vpnDisclosure.value = true
            return
        }
        requestNotificationPermission()
    }

    fun acceptVpnDisclosure() {
        runCatching { PrivacyConsent.forContext(this).grant(Purpose.VPN) }
            .onSuccess {
                vpnDisclosure.value = false
                requestNotificationPermission()
            }
            .onFailure { error.value = getString(R.string.privacy_choice_failed) }
    }

    fun dismissVpnDisclosure() {
        vpnDisclosure.value = false
        preparingVpn = false
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else prepareVpn()
    }

    private fun prepareVpn() {
        preparingVpn = false
        VpnService.prepare(this)?.let { vpnPermissionDialogLauncher.launch(it) } ?: launchVpn()
    }

    private fun launchVpn() {
        lifecycleScope.launch {
            runCatching {
                    // Preference writes from this process are queued; the core reads them.
                    MainApplication.settings.flush()
                    val binder = vpnBinder
                    if (binder != null) binder.setVpnEnabled(true)
                    else
                        ContextCompat.startForegroundService(
                            this@MainActivity,
                            YuhaiinCoreService.intent(this@MainActivity, VpnActions.CONNECT),
                        )
                }
                .onFailure { error.value = it.message ?: it.toString() }
        }
    }
}

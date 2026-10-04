package io.github.asutorufa.yuhaiin.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.util.Log
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.IYuhaiinVpnCallback
import io.github.asutorufa.yuhaiin.MainApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuhaiin.App
import yuhaiin.Closer
import yuhaiin.Opts
import yuhaiin.SocketProtect
import yuhaiin.TUN
import yuhaiin.TunAddress
import yuhaiin.Yuhaiin

class YuhaiinVpnService : VpnService() {
    companion object {
        enum class State {
            CONNECTED,
            CONNECTING,
            DISCONNECTING,
            DISCONNECTED,
            ERROR,
        }

        private const val DEFAULT_VPN_MTU = 9000
    }

    private val callbacks = RemoteCallbackList<IYuhaiinVpnCallback>()

    private fun RemoteCallbackList<IYuhaiinVpnCallback>.broadcast(state: State) {
        val n = beginBroadcast()
        try {
            for (i in 0 until n) {
                try {
                    getBroadcastItem(i).onStateChanged(state.ordinal)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } finally {
            finishBroadcast()
        }
    }

    private fun RemoteCallbackList<IYuhaiinVpnCallback>.sendMsg(msg: String) {
        val n = beginBroadcast()
        try {
            for (i in 0 until n) {
                try {
                    getBroadcastItem(i).onMsg(msg)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } finally {
            finishBroadcast()
        }
    }

    private enum class StopReason {
        USER,
        REVOKED,
        RUNTIME_CLOSED,
        START_FAILED,
    }

    private val mBinder = VpnBinder()
    private val tag = this.javaClass.simpleName
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    @Volatile private var state = State.DISCONNECTED
    private var startupJob: Job? = null
    private var latestStartId = 0
    private var foregroundStarted = false
    private var runtimeOwned = false
    private var mInterface: ParcelFileDescriptor? = null
    private var currentUnderlyingNetwork: Network? = null
    private val vpnNotification by lazy { VpnNotification(this) }
    @Volatile private var lastError: String? = null
    private val app = App()

    private fun transitionTo(next: State) {
        if (state == next) return
        Log.d(tag, "state ${state.name} -> ${next.name}")
        state = next
        callbacks.broadcast(next)
    }

    private fun vpnMtu(): Int =
        when (MainApplication.store.getString(Constants.VPN_MTU_PROFILE_KEY).ifBlank { "auto" }) {
            "1500" -> 1500
            "9000" -> 9000
            else -> DEFAULT_VPN_MTU
        }

    private fun shouldRegisterUnderlyingNetworkCallback(): Boolean =
        MainApplication.store
            .getString(Constants.REGISTER_UNDERLYING_NETWORK_CALLBACK_KEY)
            .ifBlank { "true" }
            .toBoolean()

    private val networkMonitor by lazy {
        UnderlyingNetworkMonitor((application as MainApplication).connectivity) { network ->
            if (state == State.CONNECTING || state == State.CONNECTED)
                updateUnderlyingNetwork(network)
        }
    }

    private fun unregisterUnderlyingNetworkCallback() {
        networkMonitor.stop()
    }

    private fun updateUnderlyingNetwork(network: Network?) {
        currentUnderlyingNetwork = network
        if (mInterface == null) return

        val applied = this@YuhaiinVpnService.setUnderlyingNetworks(network?.let { arrayOf(it) })
        if (!applied) {
            Log.w(tag, "failed to update VPN underlying network")
        }
    }

    inner class VpnBinder : IYuhaiinVpnBinder.Stub() {
        override fun registerCallback(cb: IYuhaiinVpnCallback?) {
            if (cb == null || !callbacks.register(cb)) return
            try {
                cb.onStateChanged(state.ordinal)
                lastError?.let(cb::onMsg)
            } catch (e: Exception) {
                Log.w(tag, "failed to send initial VPN state", e)
            }
        }

        override fun unregisterCallback(cb: IYuhaiinVpnCallback?) {
            if (cb != null) callbacks.unregister(cb)
        }

        override fun proxyGet(url: String?): ByteArray {
            return Yuhaiin.proxyGet(url ?: throw IllegalArgumentException("proxy URL is empty"))
        }

        override fun proxyDownload(url: String?, destination: String?) {
            Yuhaiin.proxyDownload(
                url ?: throw IllegalArgumentException("proxy URL is empty"),
                destination ?: throw IllegalArgumentException("proxy destination is empty"),
            )
        }

        override fun stop() = requestStop(StopReason.USER)

        override fun state(): Int {
            return state.ordinal
        }
    }

    override fun onBind(intent: Intent?) =
        if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else mBinder

    private fun requestStop(reason: StopReason) {
        serviceScope.launch {
            stopInternal(reason)
        }
    }

    private suspend fun stopInternal(reason: StopReason) {
        if (state == State.DISCONNECTED || state == State.DISCONNECTING) return

        Log.d(tag, "stopping VPN: ${reason.name}")
        transitionTo(State.DISCONNECTING)

        // Closing the TUN first unblocks native TUN setup/read paths that may
        // already be using the Android-owned descriptor.
        val tun = mInterface
        mInterface = null
        runCatching { tun?.close() }.onFailure { Log.w(tag, "failed to close VPN interface", it) }

        // Stop concurrently with coroutine cancellation. The Go wrapper signals
        // its in-flight Start before waiting for its lifecycle mutex, so this can
        // interrupt startup instead of waiting behind it.
        val runtimeStop =
            if (runtimeOwned) {
                runtimeOwned = false
                serviceScope.async(Dispatchers.IO) {
                    runCatching { app.stop() }
                }
            } else {
                null
            }

        val job = startupJob
        startupJob = null
        job?.cancelAndJoin()

        runtimeStop?.await()?.onFailure { Log.w(tag, "failed to stop VPN runtime", it) }

        cleanupResources()
        stopForegroundIfNeeded()
        transitionTo(if (reason == StopReason.START_FAILED) State.ERROR else State.DISCONNECTED)

        if (latestStartId != 0) {
            stopSelfResult(latestStartId)
        } else {
            stopSelf()
        }
    }

    private suspend fun cleanupResources() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { unregisterUnderlyingNetworkCallback() }
                .onFailure { Log.w(tag, "failed to unregister underlying network callback", it) }
        }
    }

    private fun stopForegroundIfNeeded() {
        if (!foregroundStarted) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    override fun onRevoke() {
        Log.d(tag, "VPN permission revoked")
        requestStop(StopReason.REVOKED)
        super.onRevoke()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        Log.d(tag, "starting")

        if (state != State.DISCONNECTED && state != State.ERROR) {
            Log.d(tag, "ignoring start request while state=${state.name}")
            return START_STICKY
        }

        try {
            // startForegroundService() callers require foreground promotion promptly.
            startNotification()
        } catch (e: Exception) {
            Log.e(tag, "failed to enter foreground", e)
            lastError = e.toString()
            callbacks.sendMsg(lastError!!)
            transitionTo(State.ERROR)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        lastError = null
        transitionTo(State.CONNECTING)
        startupJob = serviceScope.launch {
            try {
                val tunAddress =
                    withContext(Dispatchers.IO) {
                        MainApplication.settings.ready.await()
                        Yuhaiin.getTunAddress()
                    }

                if (state != State.CONNECTING) return@launch
                establishVpnInterface(tunAddress)

                if (state != State.CONNECTING) return@launch
                startRuntime(tunAddress)

                if (state == State.CONNECTING) {
                    transitionTo(State.CONNECTED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "failed to start VPN", e)
                lastError = e.toString()
                callbacks.sendMsg(lastError!!)
                requestStop(StopReason.START_FAILED)
            }
        }

        return START_STICKY
    }

    private fun establishVpnInterface(tunAddress: TunAddress) {
        val builder = Builder().configure(this, tunAddress, vpnMtu())
        if (shouldRegisterUnderlyingNetworkCallback()) {
            networkMonitor.start()
        }
        mInterface = builder.establish() ?: error("failed to establish VPN interface")
    }

    private suspend fun startRuntime(tunAddress: TunAddress) {
        val vpnInterface = mInterface ?: error("VPN interface is not established")
        val opts =
            Opts().apply {
                notifySpped = vpnNotification

                tun =
                    TUN().apply {
                        fd = vpnInterface.fd
                        mtu = vpnMtu()
                        portal = "${tunAddress.iPv4Address}/24"
                        portalV6 = "${tunAddress.iPv6Address}/64"
                        socketProtect = SocketProtect {
                            return@SocketProtect protect(it)
                        }
                    }

                closeFallback = Closer { requestStop(StopReason.RUNTIME_CLOSED) }
            }

        // Claim the runtime before entering native code so partial startup failures
        // are also handled by the common cleanup path.
        runtimeOwned = true
        withContext(Dispatchers.IO) {
            app.start(opts)
        }
    }

    private fun startNotification(name: String = "Default") {
        vpnNotification.createChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                vpnNotification.builder().build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(1, vpnNotification.builder().build())
        }
        foregroundStarted = true
    }

    override fun onDestroy() {
        startupJob?.cancel()
        startupJob = null

        val tun = mInterface
        mInterface = null
        runCatching { tun?.close() }
            .onFailure { Log.w(tag, "failed to close VPN interface during destroy", it) }

        if (runtimeOwned) {
            runtimeOwned = false
            runCatching { app.stop() }
                .onFailure { Log.w(tag, "failed to stop VPN runtime during destroy", it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { unregisterUnderlyingNetworkCallback() }
                .onFailure {
                    Log.w(tag, "failed to unregister network callback during destroy", it)
                }
        }

        stopForegroundIfNeeded()
        callbacks.kill()
        serviceJob.cancel()
        super.onDestroy()
    }
}

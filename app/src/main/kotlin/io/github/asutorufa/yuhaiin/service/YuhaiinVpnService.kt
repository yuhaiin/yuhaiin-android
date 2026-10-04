package io.github.asutorufa.yuhaiin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.IYuhaiinVpnCallback
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import yuhaiin.App
import yuhaiin.Closer
import yuhaiin.NotifySpped
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
            ERROR
        }

        private const val DEFAULT_VPN_MTU = 9000
        private const val PRIVATE_VLAN4_ADDRESS = "172.19.0.1"
        private const val PRIVATE_VLAN4_PORTAL = "172.19.0.2"
        private const val PRIVATE_VLAN6_ADDRESS = "fdfe:dcba:9876::1"
        private const val PRIVATE_VLAN6_PORTAL = "fdfe:dcba:9876::2"
    }


    private val callbacks = RemoteCallbackList<IYuhaiinVpnCallback>()

    private fun RemoteCallbackList<IYuhaiinVpnCallback>.broadcast(state: State) {
        val n = beginBroadcast()
        for (i in 0 until n) {
            try {
                getBroadcastItem(i).onStateChanged(state.ordinal)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        finishBroadcast()
    }

    private fun RemoteCallbackList<IYuhaiinVpnCallback>.sendMsg(msg: String) {
        val n = beginBroadcast()
        for (i in 0 until n) {
            try {
                getBroadcastItem(i).onMsg(msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        finishBroadcast()
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

    @Volatile
    private var state = State.DISCONNECTED
    private var startupJob: Job? = null
    private var latestStartId = 0
    private var foregroundStarted = false
    private var runtimeOwned = false
    private var mInterface: ParcelFileDescriptor? = null
    private var underlyingNetworkCallbackRegistered = false
    private val notification by lazy { application.getSystemService<NotificationManager>()!! }
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
        MainApplication.store.getString(Constants.REGISTER_UNDERLYING_NETWORK_CALLBACK_KEY)
            .ifBlank { "true" }
            .toBoolean()

    private fun unregisterUnderlyingNetworkCallback() {
        if (!underlyingNetworkCallbackRegistered) return

        (application as MainApplication).connectivity.unregisterNetworkCallback(defaultNetworkCallback)
        underlyingNetworkCallbackRegistered = false
    }

    private fun notificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, packageName)
            .setContentTitle(resources.getString(R.string.yuhaiin_running))
//            .setContentText(String.format(getString(R.string.notify_msg), "VPN"))
            .setSmallIcon(R.drawable.emoji_nature)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
    }

    inner class VpnBinder : IYuhaiinVpnBinder.Stub() {
        override fun registerCallback(cb: IYuhaiinVpnCallback?) {
            if (cb == null || !callbacks.register(cb)) return
            try {
                cb.onStateChanged(state.ordinal)
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


    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val defaultNetworkCallback: ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
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

        val job = startupJob
        startupJob = null
        job?.cancelAndJoin()

        cleanupResources()
        stopForegroundIfNeeded()
        transitionTo(State.DISCONNECTED)

        if (latestStartId != 0) {
            stopSelfResult(latestStartId)
        } else {
            stopSelf()
        }
    }

    private suspend fun cleanupResources() {
        val tun = mInterface
        mInterface = null
        runCatching { tun?.close() }
            .onFailure { Log.w(tag, "failed to close VPN interface", it) }

        if (runtimeOwned) {
            runtimeOwned = false
            runCatching {
                withContext(Dispatchers.IO) {
                    app.stop()
                }
            }.onFailure { Log.w(tag, "failed to stop VPN runtime", it) }
        }

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

        if (state != State.DISCONNECTED) {
            Log.d(tag, "ignoring start request while state=${state.name}")
            return START_STICKY
        }

        try {
            // startForegroundService() callers require foreground promotion promptly.
            startNotification()
        } catch (e: Exception) {
            Log.e(tag, "failed to enter foreground", e)
            callbacks.sendMsg(e.toString())
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        transitionTo(State.CONNECTING)
        startupJob = serviceScope.launch {
            try {
                val tunAddress = withContext(Dispatchers.IO) {
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
                callbacks.sendMsg(e.toString())
                requestStop(StopReason.START_FAILED)
            }
        }

        return START_STICKY
    }

    private fun Builder.addRoute(cidr: String) {
        try {
            Yuhaiin.parseCIDR(cidr).apply {
                // Cannot handle 127.0.0.0/8
                if (!ip.startsWith("127")) addRoute(ip, mask)
            }
        } catch (e: Exception) {
            Log.e("addRoute", "addRoute " + cidr + " failed: " + e.message)
        }
    }

    private fun establishVpnInterface(tunAddress: TunAddress) {
        Builder().apply {
            setMtu(vpnMtu())
            setSession("Default")


            val appListString = MainApplication.store.getString("app_list")
            Log.d(tag, "configure: $appListString")

            fun bypassApp(bypass: Boolean, app: String) =
                try {
                    if (bypass) addDisallowedApplication(app.trim())
                    else addAllowedApplication(app.trim())
                } catch (e: Exception) {
                    Log.w(tag, e)
                }

            if (MainApplication.store.getBoolean(resources.getString(R.string.adv_per_app_key))) {
                val appList = Json.decodeFromString<MutableSet<String>>(appListString)
                val bypass =
                    MainApplication.store.getBoolean(resources.getString(R.string.adv_app_bypass_key))
                appList.toMutableSet().apply {
                    // make yuhaiin using VPN, because tun2socket tcp need relay tun data to tcp a listener
                    if (bypass) remove(BuildConfig.APPLICATION_ID)
                    else add(BuildConfig.APPLICATION_ID)
                    forEach { bypassApp(bypass, it) }
                }
            }

            addAddress(tunAddress.iPv4Address, 24).addRoute(tunAddress.iPv4, 24)

            // Route all IPv6 traffic
            addAddress(tunAddress.iPv6Address, 64)
                .addRoute("2000::", 3) // https://issuetracker.google.com/issues/149636790
                .addRoute(tunAddress.iPv6, 64)


            val routeKey = MainApplication.store.getString(Constants.ROUTE_KEY)
            val content = MainApplication.store.getString(Constants.ROUTE_CONTENT_PREFIX + routeKey)
            Log.i("VPN", "Configure route: $routeKey")
            if (content.isNotBlank()) {
                content.lineSequence().forEach {
                    if (it.isNotBlank()) addRoute(it)
                }
            } else {
                addRoute("0.0.0.0/0")
                if (Yuhaiin.isIPv6()) addRoute("::/0")
            }

            addDnsServer(tunAddress.iPv4Portal)
            addDnsServer(tunAddress.iPv6Portal)
            Yuhaiin.addFakeDnsCidr {
                try {
                    addRoute(it.ip, it.mask)
                } catch (e: Exception) {
                    Log.w("vpn service", "configure: $e")
                }
            }
//            addRoute(MainApplication.store.getString(resources.getString(R.string.adv_fake_dns_cidr_key)))
//            addRoute(MainApplication.store.getString(resources.getString(R.string.adv_fake_dnsv6_cidr_key)))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && shouldRegisterUnderlyingNetworkCallback()) {
                (application as MainApplication).connectivity.requestNetwork(
                    defaultNetworkRequest,
                    defaultNetworkCallback
                )
                underlyingNetworkCallbackRegistered = true
            }

            val httpProxy = MainApplication.store.getInt("http_port")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setMetered(false)
                if (httpProxy != 0 && MainApplication.store.getBoolean(resources.getString(R.string.append_http_proxy_to_vpn_key))) {
                    setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", httpProxy))
                }
            }


            mInterface = establish() ?: error("failed to establish VPN interface")
        }
    }

    private suspend fun startRuntime(tunAddress: TunAddress) {
        val vpnInterface = mInterface ?: error("VPN interface is not established")
        val opts = Opts().apply {
            notifySpped = SpeedNotifier(
                notificationBuilder(),
                NotificationManagerCompat.from(this@YuhaiinVpnService)
            )

            tun = TUN().apply {
                fd = vpnInterface.fd
                mtu = vpnMtu()
                portal = "${tunAddress.iPv4Address}/24"
                portalV6 = "${tunAddress.iPv6Address}/64"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    socketProtect = SocketProtect { return@SocketProtect protect(it) }
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
        // Notifications on Oreo and above need a channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notification.createNotificationChannel(
                NotificationChannel(
                    packageName,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_NONE
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notificationBuilder().build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notificationBuilder().build())
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
                .onFailure { Log.w(tag, "failed to unregister network callback during destroy", it) }
        }

        stopForegroundIfNeeded()
        callbacks.kill()
        serviceJob.cancel()
        super.onDestroy()
    }

    inner class SpeedNotifier(
        private var builder: NotificationCompat.Builder,
        private val notificationManagerCompat: NotificationManagerCompat
    ) : NotifySpped {

        private val enabled = notificationManagerCompat.areNotificationsEnabled()

        override fun notifyEnable(): Boolean = enabled

        override fun notify(str: String) {
            if (enabled)
                notificationManagerCompat.notify(
                    1,
                    builder
                        .setContentTitle("${resources.getString(R.string.yuhaiin_running)} $str")
                        .build()
                )
        }
    }
}

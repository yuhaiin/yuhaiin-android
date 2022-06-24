package io.github.asutorufa.yuhaiin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.*
import android.net.ConnectivityManager.NetworkCallback
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.database.Profile
import io.github.asutorufa.yuhaiin.util.Routes
import yuhaiin.*


class YuhaiinVpnService : VpnService() {
    companion object {
        private const val INTENT_PREFIX = "YUHAIIN"
        const val INTENT_DISCONNECTED = INTENT_PREFIX + "DISCONNECTED"
        const val INTENT_CONNECTED = INTENT_PREFIX + "CONNECTED"
        const val INTENT_CONNECTING = INTENT_PREFIX + "CONNECTING"
        const val INTENT_DISCONNECTING = INTENT_PREFIX + "DISCONNECTING"
        const val INTENT_ERROR = INTENT_PREFIX + "ERROR"

        enum class State {
            CONNECTED,
            CONNECTING,
            DISCONNECTING,
            DISCONNECTED,
        }

        private const val VPN_MTU = 1500
        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        private const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
        private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
    }

    private val yuhaiin = App()
    private val mBinder = VpnBinder()
    private val tag = this.javaClass.simpleName
    private var state = State.DISCONNECTED
    private var mInterface: ParcelFileDescriptor? = null


    inner class VpnBinder : Binder() {
        fun isRunning() = state == State.CONNECTED
        fun stop() = this@YuhaiinVpnService.onRevoke()
        fun saveNewBypass(url: String?): String? {
            Log.d(tag, "SaveNewBypass: $url")
            return try {
                yuhaiin.saveNewBypass(
                    Uri.parse(url).toString(),
                    getExternalFilesDir("yuhaiin")!!.absolutePath
                )
                ""
            } catch (e: Exception) {
                e.message
            }
        }
    }

    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(
            CONNECTIVITY_SERVICE
        ) as ConnectivityManager
    }
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }
    private val defaultNetworkCallback: NetworkCallback = object : NetworkCallback() {
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

    private fun stop() {
        if (state != State.CONNECTED) return
        setState(State.DISCONNECTING)

        stopForeground(true)
        yuhaiin.stop()
        mInterface?.close()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (ignored: Exception) {
                // ignored
            }
        }

        setState(State.DISCONNECTED)
    }

    override fun onRevoke() {
        Log.d(tag, "onRevoke")
        stop()
        super.onRevoke()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "starting")
        if (state == State.CONNECTED) {
            setState(State.CONNECTED)
            return START_STICKY
        }

        setState(State.CONNECTING)

        try {
            val profile = Manager.db.getProfileByName(Manager.db.getLastProfile() ?: "Default")

            configure(profile)
            start(profile)
            startNotification(profile.name)

            setState(State.CONNECTED)

        } catch (e: Exception) {
            e.printStackTrace()
            setState(State.DISCONNECTED)
            applicationContext.sendBroadcast(Intent(INTENT_ERROR).also {
                it.putExtra(
                    "message",
                    e.toString()
                )
            })
            onRevoke()
        }

        return START_STICKY
    }

    private fun configure(profile: Profile) {
        val b = Builder()
        b.setMtu(VPN_MTU)
            .setSession(profile.name)
            .addAddress(PRIVATE_VLAN4_CLIENT, 24)
            .addDnsServer(PRIVATE_VLAN4_ROUTER)

        if (profile.hasIPv6) {
            // Route all IPv6 traffic
            b.addAddress(PRIVATE_VLAN6_CLIENT, 126)
                .addRoute("::", 0)
        }

        Routes.addRoutes(this, b, profile.route)
        Routes.addRoute(b, profile.fakeDnsCidr)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            connectivityManager.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            b.setMetered(false)
            if (profile.httpServerPort != 0 && profile.appendHttpProxyToSystem) {
                b.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", profile.httpServerPort))
            }
        }

        Log.d(tag, "configure: ${profile.appList}")

        when (profile.isPerApp) {
            true -> {
                fun process(app: String) =
                    try {
                        if (profile.isBypassApp) b.addDisallowedApplication(app.trim()) else b.addAllowedApplication(
                            app.trim()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }


                if (profile.isBypassApp) b.addDisallowedApplication(BuildConfig.APPLICATION_ID)
                profile.appList.map {
                    if ((!profile.isBypassApp && it == BuildConfig.APPLICATION_ID) || it.isEmpty()) return
                    process(it)
                }
            }

            false -> {
                // Just bypass myself
                try {
                    b.addDisallowedApplication(BuildConfig.APPLICATION_ID)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        mInterface = b.establish()
    }

    private fun start(profile: Profile) {
        Log.d(tag, "start yuhaiin: $profile")
        if (profile.yuhaiinPort > 0) {
            var address = "127.0.0.1"
            if (profile.allowLan) address = "0.0.0.0"
            yuhaiin.start(Opts().apply {
                host = "${address}:${profile.yuhaiinPort}"
                savepath = getExternalFilesDir("yuhaiin")!!.absolutePath
                socks5 = "${address}:${profile.socks5ServerPort}"
                http = "${address}:${profile.httpServerPort}"
                saveLogcat = profile.saveLogcat
                block = profile.ruleBlock
                proxy = profile.ruleProxy
                direct = profile.ruleProxy
                dns = DNSSetting().apply {
                    server = "${address}:${profile.dnsPort}"
                    fakedns = profile.fakeDnsCidr.isNotEmpty()
                    fakednsIpRange = profile.fakeDnsCidr
                    remote = DNS().apply {
                        host = profile.remoteDns.host
                        subnet = profile.remoteDns.subnet
                        type = profile.remoteDns.type
                        tlsServername = profile.remoteDns.tlsServerName
                        proxy = profile.remoteDns.proxy
                    }
                    local = DNS().apply {
                        host = profile.localDns.host
                        subnet = profile.localDns.subnet
                        type = profile.localDns.type
                        tlsServername = profile.localDns.tlsServerName
                        proxy = profile.localDns.proxy
                    }
                    bootstrap = DNS().apply {
                        host = profile.bootstrapDns.host
                        subnet = profile.bootstrapDns.subnet
                        type = profile.bootstrapDns.type
                        tlsServername = profile.bootstrapDns.tlsServerName
                        proxy = profile.bootstrapDns.proxy
                    }
                    tun = TUN().apply {
                        fd = mInterface!!.fd
                        mtu = VPN_MTU
                        gateway = PRIVATE_VLAN4_ROUTER
                        dnsHijacking = profile.dnsHijacking
                    }
                }
            })
        }
    }

    private fun startNotification(name: String) {
        // Notifications on Oreo and above need a channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                packageName,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannel(channel)
        }

        startForeground(
            1,
            NotificationCompat.Builder(this, packageName)
                .setContentTitle("yuhaiin running")
                .setContentText(String.format(getString(R.string.notify_msg), name))
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        )
    }

    private fun setState(state: State) {
        this.state = state

        when (state) {
            State.CONNECTED -> {
                applicationContext.sendBroadcast(Intent(INTENT_CONNECTED))
            }
            State.DISCONNECTED -> {
                applicationContext.sendBroadcast(Intent(INTENT_DISCONNECTED))
            }
            State.DISCONNECTING -> {
                applicationContext.sendBroadcast(Intent(INTENT_DISCONNECTING))
            }
            State.CONNECTING -> {
                applicationContext.sendBroadcast(Intent(INTENT_CONNECTING))
            }
        }
    }
}
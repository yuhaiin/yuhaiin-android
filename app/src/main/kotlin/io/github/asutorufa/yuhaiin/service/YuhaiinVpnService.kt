package io.github.asutorufa.yuhaiin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.gson.Gson
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.database.Profile
import yuhaiin.*
import java.net.InetSocketAddress
import io.github.asutorufa.yuhaiin.database.DNS as dDNS


class YuhaiinVpnService : VpnService() {
    companion object {
        enum class State {
            CONNECTED,
            CONNECTING,
            DISCONNECTING,
            DISCONNECTED,
            ERROR
        }

        private const val VPN_MTU = 9000
        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        private const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
        private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
    }


    private val mBinder = VpnBinder()
    private val tag = this.javaClass.simpleName
    private var state = State.DISCONNECTED
        set(value) {
            field = value
            applicationContext.sendBroadcast(Intent(value.toString()))
        }

    private var mInterface: ParcelFileDescriptor? = null
    private val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
    private val notification by lazy { application.getSystemService<NotificationManager>()!! }
    private val uidDumper = UidDumper()
    private val app = App()

    inner class VpnBinder : IYuhaiinVpnBinder.Stub() {
        override fun isRunning() = state == State.CONNECTED
        override fun stop() = this@YuhaiinVpnService.onRevoke()
        override fun saveNewBypass(url: String): String {
            Log.d(tag, "SaveNewBypass: $url")
            return try {
                app.saveNewBypass(
                    Uri.parse(url).toString(),
                    getExternalFilesDir("yuhaiin")!!.absolutePath
                )
                ""
            } catch (e: Exception) {
                e.message.toString()
            }
        }
    }

    inner class UidDumper : yuhaiin.UidDumper {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun dumpUid(
            p0: Int,
            srcIp: String?,
            srcPort: Int,
            destIp: String?,
            destPort: Int
        ): Int =
            connectivity.getConnectionOwnerUid(
                p0,
                InetSocketAddress(srcIp, srcPort),
                InetSocketAddress(destIp, destPort)
            )

        override fun getUidInfo(p0: Int): String = packageManager.getNameForUid(p0) ?: "unknown"
    }

    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
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

    private fun stop() {
        if (state != State.CONNECTED) return
        state = State.DISCONNECTING

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(Service.STOP_FOREGROUND_REMOVE)
            else stopForeground(true)

            mInterface?.close()
            app.stop()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            }
            state = State.DISCONNECTED
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRevoke() {
        Log.d(tag, "onRevoke")
        stop()
        super.onRevoke()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "starting")
        if (state == State.CONNECTED || state == State.CONNECTING || app.running()) {
            Log.d(tag, "already running")
            return START_STICKY
        }

        state = State.CONNECTING

        try {
            val profile = Manager.db.getProfileByName(Manager.db.getLastProfile() ?: "Default")

            configure(profile)
            start(profile)
            startNotification(profile.name)

            state = State.CONNECTED

        } catch (e: Exception) {
            e.printStackTrace()
            state = State.DISCONNECTED
            applicationContext.sendBroadcast(Intent(State.ERROR.toString()).also {
                it.putExtra(
                    "message",
                    e.toString()
                )
            })
            onRevoke()
        }

        return START_STICKY
    }

    private fun VpnService.Builder.addRoute(cidr: String) {
        try {
            Yuhaiin.parseCIDR(cidr).apply {
                // Cannot handle 127.0.0.0/8
                if (!ip.startsWith("127")) addRoute(ip, mask)
            }
        } catch (e: Exception) {
            Log.e("addRoute", "addRoute " + cidr + " failed: " + e.message)
        }
    }

    private fun configure(profile: Profile) {
        Builder().apply {
            setMtu(VPN_MTU)
            setSession(profile.name)

            addAddress(PRIVATE_VLAN4_CLIENT, 24).addRoute(PRIVATE_VLAN4_ROUTER, 32)

            // Route all IPv6 traffic
            if (profile.hasIPv6) addAddress(PRIVATE_VLAN6_CLIENT, 126)
                .addRoute("2000::", 3) // https://issuetracker.google.com/issues/149636790
                .addRoute(PRIVATE_VLAN6_ROUTER, 128)

            when (profile.route) {
                resources.getString(R.string.adv_route_non_chn) ->
                    resources.getStringArray(R.array.simple_route).forEach { addRoute(it) }

                resources.getString(R.string.adv_route_non_local) ->
                    resources.getStringArray(R.array.all_routes_except_local)
                        .forEach { addRoute(it) }

                else -> {
                    addRoute("0.0.0.0/0")
                    if (profile.hasIPv6) addRoute("[::]/0")
                }
            }

            addDnsServer(PRIVATE_VLAN4_ROUTER)
            addRoute(profile.fakeDnsCidr)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setMetered(false)
                if (profile.httpServerPort != 0 && profile.appendHttpProxyToSystem) {
                    setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", profile.httpServerPort))
                }
            }

            Log.d(tag, "configure: ${profile.appList}")

            fun bypassApp(bypass: Boolean, app: String) =
                try {
                    if (bypass) addDisallowedApplication(app.trim())
                    else addAllowedApplication(app.trim())
                } catch (e: Exception) {
                    Log.w(tag, e)
                }

            if (profile.isPerApp) {
                profile.appList.toMutableSet().apply {
                    // make yuhaiin using VPN, because tun2socket tcp need relay tun data to tcp a listener
                    if (profile.isBypassApp) remove(BuildConfig.APPLICATION_ID)
                    else add(BuildConfig.APPLICATION_ID)
                    forEach { bypassApp(profile.isBypassApp, it) }
                }
            }

            mInterface = establish()
        }
    }

    private fun start(profile: Profile) {
        if (profile.yuhaiinPort <= 0) throw Exception("Invalid yuhaiin port: ${profile.yuhaiinPort}")

        var address = "127.0.0.1"
        if (profile.allowLan) address = "0.0.0.0"

        app.start(Opts().apply {
            host = "${address}:${profile.yuhaiinPort}"
            savepath = getExternalFilesDir("yuhaiin").toString()
            iPv6 = profile.hasIPv6


            if (profile.socks5ServerPort > 0) socks5 = "${address}:${profile.socks5ServerPort}"
            if (profile.httpServerPort > 0) http = "${address}:${profile.httpServerPort}"

            log = Log().apply {
                saveLogcat = profile.saveLogcat
                logLevel = profile.logLevel
            }

            bypass = Bypass().apply {
                block = profile.ruleBlock
                proxy = profile.ruleProxy
                direct = profile.ruleDirect
                tcp = profile.bypass.tcp
                udp = profile.bypass.udp
            }

            tun = TUN().apply {
                fd = mInterface!!.fd
                mtu = VPN_MTU
                gateway = PRIVATE_VLAN4_CLIENT
                portal = PRIVATE_VLAN4_ROUTER
                dnsHijacking = profile.dnsHijacking
                // 0: fdbased, 1: channel, 2: system gvisor
                driver = profile.tunDriver
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    uidDumper = this@YuhaiinVpnService.uidDumper
                socketProtect = SocketProtect { return@SocketProtect protect(it) }
            }

            fun convertDNS(o: dDNS): DNS = DNS().apply {
                host = o.host
                subnet = o.subnet
                type = o.type
                tlsServername = o.tlsServerName
            }

            dns = DNSSetting().apply {
                if (profile.dnsPort > 0) server = "${address}:${profile.dnsPort}"
                fakedns = profile.fakeDnsCidr.isNotEmpty()
                fakednsIpRange = profile.fakeDnsCidr
                resolveRemoteDomain = profile.resolveRemoteDomain
                remote = convertDNS(profile.remoteDns)
                local = convertDNS(profile.localDns)
                bootstrap = convertDNS(profile.bootstrapDns)
                hosts = Gson().toJson(profile.hosts).toByteArray()
            }
        })
    }

    private fun startNotification(name: String) {
        // Notifications on Oreo and above need a channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notification.createNotificationChannel(
                NotificationChannel(
                    packageName,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )



        startForeground(
            1,
            NotificationCompat.Builder(this, packageName)
                .setContentTitle(resources.getString(R.string.yuhaiin_running))
                .setContentText(String.format(getString(R.string.notify_msg), name))
                .setSmallIcon(R.drawable.emoji_nature)
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
}

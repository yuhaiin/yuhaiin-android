package io.github.asutorufa.yuhaiin.service

import android.app.Notification
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.logviewer.ReadLogcat.Companion.ignore
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import kotlinx.serialization.json.Json
import yuhaiin.App
import yuhaiin.Closer
import yuhaiin.NotifySpped
import yuhaiin.Opts
import yuhaiin.SocketProtect
import yuhaiin.TUN
import yuhaiin.Yuhaiin
import java.net.InetSocketAddress


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
        private const val PRIVATE_VLAN4_ADDRESS = "172.19.0.1"
        private const val PRIVATE_VLAN4_PORTAL = "172.19.0.2"
        private const val PRIVATE_VLAN6_ADDRESS = "fdfe:dcba:9876::1"
        private const val PRIVATE_VLAN6_PORTAL = "fdfe:dcba:9876::2"
    }


    private val mBinder = VpnBinder()
    private val tag = this.javaClass.simpleName
    private var state = State.DISCONNECTED
        set(value) {
            field = value
            Log.d("VpnService", "send broadcast $value")
            applicationContext.sendBroadcast(Intent(value.toString()))
        }

    private var mInterface: ParcelFileDescriptor? = null
    private val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
    private val notification by lazy { application.getSystemService<NotificationManager>()!! }
    private val uidDumper = UidDumper()
    private val app = App()

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
        override fun isRunning() = state == State.CONNECTED
        override fun stop() = this@YuhaiinVpnService.onRevoke()
        override fun saveNewBypass(url: String): String {
            Log.d(tag, "SaveNewBypass: $url")
            return try {
                app.saveNewBypass(Uri.parse(url).toString())
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
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
            configure()
            startNotification()
            start()

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

    private fun Builder.addRuleRoute() {
        Yuhaiin.addRulesCidrv2 { ignore { addRoute(it.ip, it.mask) } }
    }

    private fun configure() {
        Builder().apply {
            setMtu(VPN_MTU)
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

            addAddress(PRIVATE_VLAN4_ADDRESS, 24).addRoute(PRIVATE_VLAN4_PORTAL, 32)

            // Route all IPv6 traffic
            addAddress(PRIVATE_VLAN6_ADDRESS, 64)
                .addRoute("2000::", 3) // https://issuetracker.google.com/issues/149636790
                .addRoute(PRIVATE_VLAN6_PORTAL, 128)

            when (MainApplication.store.getString(resources.getString(R.string.adv_route_Key))) {
                resources.getString(R.string.adv_route_non_chn) -> {
                    resources.getStringArray(R.array.simple_route).forEach { addRoute(it) }
                    addRuleRoute()
                }

                resources.getString(R.string.adv_route_non_local) -> {
                    resources.getStringArray(R.array.all_routes_except_local)
                        .forEach { addRoute(it) }
                    addRuleRoute()
                }

                else -> {
                    addRoute("0.0.0.0/0")
                    if (MainApplication.store.getBoolean(resources.getString(R.string.ipv6_proxy_key))) addRoute(
                        "::/0"
                    )
                }
            }

            addDnsServer(PRIVATE_VLAN4_PORTAL)
            addDnsServer(PRIVATE_VLAN6_PORTAL)
            addRoute(MainApplication.store.getString(resources.getString(R.string.adv_fake_dns_cidr_key)))
            addRoute(MainApplication.store.getString(resources.getString(R.string.adv_fake_dnsv6_cidr_key)))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)

            val httpProxy = MainApplication.store.getInt("http_port")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setMetered(false)
                if (httpProxy != 0 && MainApplication.store.getBoolean(resources.getString(R.string.append_http_proxy_to_vpn_key))) {
                    setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", httpProxy))
                }
            }


            mInterface = establish()
        }
    }

    private fun start() {
        app.start(Opts().apply {
            savepath = getExternalFilesDir("yuhaiin").toString()
            notifySpped = SpeedNotifier(
                notificationBuilder(),
                NotificationManagerCompat.from(this@YuhaiinVpnService)
            )

            tun = TUN().apply {
                fd = mInterface!!.fd
                mtu = VPN_MTU
                portal = "$PRIVATE_VLAN4_ADDRESS/24"
                portalV6 = "$PRIVATE_VLAN6_ADDRESS/64"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    uidDumper = this@YuhaiinVpnService.uidDumper
                socketProtect = SocketProtect { return@SocketProtect protect(it) }
            }

            closeFallback = Closer { stop() }
        })
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


        startForeground(1, notificationBuilder().build())
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

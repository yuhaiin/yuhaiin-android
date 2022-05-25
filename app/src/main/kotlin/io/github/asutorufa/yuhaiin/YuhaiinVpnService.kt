package io.github.asutorufa.yuhaiin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.*
import android.net.ConnectivityManager.NetworkCallback
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import io.github.asutorufa.yuhaiin.util.Constants
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_CONNECTED
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_CONNECTING
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_DISCONNECTED
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_DISCONNECTING
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_ERROR
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_APP_BYPASS
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_APP_LIST
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_DNS_PORT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_FAKE_DNS_CIDR
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_PER_APP
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ADV_ROUTE
import io.github.asutorufa.yuhaiin.util.Constants.PREF_ALLOW_LAN
import io.github.asutorufa.yuhaiin.util.Constants.PREF_AUTH_PASSWORD
import io.github.asutorufa.yuhaiin.util.Constants.PREF_AUTH_USERNAME
import io.github.asutorufa.yuhaiin.util.Constants.PREF_HTTP_SERVER_PORT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_IPV6_PROXY
import io.github.asutorufa.yuhaiin.util.Constants.PREF_RULE_BLOCK
import io.github.asutorufa.yuhaiin.util.Constants.PREF_RULE_DIRECT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_RULE_PROXY
import io.github.asutorufa.yuhaiin.util.Constants.PREF_SAVE_LOGCAT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_SOCKS5_SERVER_PORT
import io.github.asutorufa.yuhaiin.util.Constants.PREF_YUHAIIN_PORT
import io.github.asutorufa.yuhaiin.util.Constants.ROUTE_ALL
import io.github.asutorufa.yuhaiin.util.Routes
import io.github.asutorufa.yuhaiin.util.Utility
import yuhaiin.App
import java.io.File


class YuhaiinVpnService : VpnService() {
    private val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
    private val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
    private val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
    private val TAG = this.javaClass.simpleName
    private val VPN_MTU = 1500
    private val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"

    private var mRunning = false
    private var mStopping = false

    private var mg: ConnectivityManager? = null
    private var mInterface: ParcelFileDescriptor? = null
    private var yuhaiin: App? = null
    private var tun2socks: Process? = null

    private val mBinder: IBinder = object : IVpnService.Stub() {
        override fun isRunning() = this@YuhaiinVpnService.mRunning
        override fun stop() = stopMe()
    }

    @Volatile
    var underlyingNetwork: Network? = null
        set(value) {
            field = value
            if (this.mRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(underlyingNetworks)
            }
        }
    private val underlyingNetworks
        get() = // clearing underlyingNetworks makes Android 9 consider the network to be metered
            if (Build.VERSION.SDK_INT == 28) null else underlyingNetwork?.let {
                arrayOf(it)
            }

    private val defaultNetworkCallback: NetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(arrayOf(network))
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(arrayOf(network))
            }
        }

        override fun onLost(network: Network) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(null)
            }
        }
    }

    fun stopMe() {
        if (mStopping) return

        mStopping = true
        applicationContext.sendBroadcast(Intent(INTENT_DISCONNECTING))
        stopForeground(true)

        yuhaiin?.let {
            it.stop()
            yuhaiin = null
        }

        tun2socks?.let {
            it.destroy()
            tun2socks = null
        }

        stopSelf()

        mInterface?.close()

        mRunning = false
        mStopping = false

        applicationContext.sendBroadcast(Intent(INTENT_DISCONNECTED))
    }

    override fun onBind(intent: Intent?) = if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else mBinder

    override fun onRevoke() {
        Log.d(TAG, "onRevoke")
        stopMe()
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopMe()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onLowMemory() {
        Log.d(TAG, "onLowMemory")
        stopMe()
        super.onLowMemory()
    }

    override fun stopService(name: Intent?): Boolean {
        Log.d(TAG, "stopService")
        stopMe()
        return super.stopService(name)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applicationContext.sendBroadcast(Intent(INTENT_CONNECTING))

        if (DEBUG) Log.d(TAG, "starting")

        if (intent == null) {
            applicationContext.sendBroadcast(Intent(INTENT_DISCONNECTED))
            return START_STICKY
        }

        if (mRunning) {
            applicationContext.sendBroadcast(Intent(INTENT_CONNECTED))
            return START_STICKY
        }


        // Notifications on Oreo and above need a channel
        val NOTIFICATION_CHANNEL_ID = "io.github.asutorufa.yuhaiin"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        val NOTIFICATION_ID = 1
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(
            NOTIFICATION_ID, builder
                .setContentTitle("yuhaiin running")
                .setContentText(
                    String.format(
                        getString(R.string.notify_msg),
                        intent.getStringExtra(Constants.PREF_PROFILE) ?: "default"
                    )
                )
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentIntent(contentIntent)
                .build()
        )

        try {

            configure(
                intent.getStringExtra(Constants.PREF_PROFILE) ?: "default",
                intent.getStringExtra(PREF_ADV_ROUTE) ?: ROUTE_ALL,
                intent.getStringExtra(PREF_ADV_FAKE_DNS_CIDR) ?: "10.0.2.1/24",
                intent.getIntExtra(PREF_HTTP_SERVER_PORT, 8188),
                intent.getBooleanExtra(PREF_ADV_PER_APP, false),
                intent.getBooleanExtra(PREF_ADV_APP_BYPASS, false),
                intent.getStringArrayExtra(PREF_ADV_APP_LIST) ?: Array(0) { "" },
                intent.getBooleanExtra(PREF_IPV6_PROXY, false)
            )


            if (DEBUG) Log.d(TAG, "fd: ${mInterface?.fd}")

            start(
                intent.getIntExtra(PREF_YUHAIIN_PORT, 50051),
                intent.getIntExtra(PREF_HTTP_SERVER_PORT, 8188),
                intent.getIntExtra(PREF_SOCKS5_SERVER_PORT, 1080),
                intent.getStringExtra(PREF_AUTH_USERNAME) ?: "",
                intent.getStringExtra(PREF_AUTH_PASSWORD) ?: "",
                intent.getStringExtra(PREF_ADV_FAKE_DNS_CIDR)!!,
                intent.getIntExtra(PREF_ADV_DNS_PORT, 5353),
                intent.getBooleanExtra(PREF_IPV6_PROXY, false),
                intent.getBooleanExtra(PREF_SAVE_LOGCAT, false),
                intent.getBooleanExtra(PREF_ALLOW_LAN, false),

                intent.getStringExtra(PREF_RULE_BLOCK) ?: "",
                intent.getStringExtra(PREF_RULE_PROXY) ?: "",
                intent.getStringExtra(PREF_RULE_DIRECT) ?: "",
            )
        } catch (e: Exception) {
            e.printStackTrace()
            applicationContext.sendBroadcast(
                Intent(INTENT_ERROR).also {
                    it.putExtra("message", e.toString())
                })
            stopMe()
        }

        return START_STICKY
    }

    private fun configure(
        name: String,
        route: String,
        fakedns: String,
        httpserverport: Int,
        perApp: Boolean,
        bypass: Boolean,
        apps: Array<String>,
        ipv6: Boolean
    ) {
        val b = Builder()
        b.setMtu(VPN_MTU)
            .setSession(name)
            .addAddress(PRIVATE_VLAN4_CLIENT, 24)
            .addDnsServer(PRIVATE_VLAN4_ROUTER)

        if (ipv6) {
            // Route all IPv6 traffic
            b.addAddress(PRIVATE_VLAN6_CLIENT, 126)
                .addRoute("::", 0)
        }

        Routes.addRoutes(this, b, route)
        Routes.addRoute(b, fakedns)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (mg == null) mg = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).build()
            mg?.requestNetwork(req, defaultNetworkCallback)
        }
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && httpserverport != 0) {
            b.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", httpserverport))
        }

        // Add the default DNS
        // Note that this DNS is just a stub.
        // Actual DNS requests will be redirected through pdnsd.
//        b.addRoute("223.5.5.5", 32);

        if (DEBUG) Log.d(TAG, "configure: ${apps.toList()}")
        when (perApp) {
            true -> {
                fun process(app: String) =
                    try {
                        if (bypass) b.addDisallowedApplication(app.trim()) else b.addAllowedApplication(app.trim())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }


                if (bypass) b.addDisallowedApplication(BuildConfig.APPLICATION_ID)
                apps.map {
                    if ((!bypass && it == BuildConfig.APPLICATION_ID) || it.isEmpty()) return
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

    private fun start(
        yuhaiinPort: Int,
        httpport: Int,
        socks5port: Int,
        user: String,
        passwd: String,
        fakeDnsCidr: String,
        dnsPort: Int,
        ipv6: Boolean,
        savelog: Boolean,
        allowLan: Boolean,

        block: String,
        proxy: String,
        direct: String
    ) {
        var address = "127.0.0.1"
        if (allowLan) address = "0.0.0.0"
        Log.d(
            TAG,
            "start, yuhaiin: $yuhaiinPort, http: $httpport, socks5: $socks5port," +
                    " fakednsCidr: $fakeDnsCidr, dnsPort: $dnsPort, saveLog: $savelog, allowLan: $allowLan"
        )
        if (yuhaiinPort > 0) {
            yuhaiin = App()
            try {
                yuhaiin!!.start(
                    "${address}:${yuhaiinPort}",
                    getExternalFilesDir("yuhaiin")!!.absolutePath,
                    "${address}:${dnsPort}",
                    "${address}:${socks5port}",
                    "${address}:${httpport}",
                    fakeDnsCidr.isNotEmpty(),
                    fakeDnsCidr,
                    savelog,
                    block,
                    proxy,
                    direct
                )
            } catch (e: Exception) {
                throw e
            }
        }

        val command = buildString {
            append("${applicationInfo.nativeLibraryDir}/libtun2socks.so")
            append(" --netif-ipaddr $PRIVATE_VLAN4_ROUTER")
            // + " --netif-netmask 255.255.255.252"
            append(" --socks-server-addr 127.0.0.1:$socks5port")
            // + " --tunfd %d"
            append(" --tunmtu 1500")
            append(" --loglevel warning")
            // + " --pid %s/tun2socks.pid"
            append(" --sock-path ${applicationInfo.dataDir}/sock_path")

            if (user.isNotEmpty() && passwd.isNotEmpty()) {
                append(" --username $user")
                append(" --password $passwd")
            }
            if (ipv6) {
                append(" --netif-ip6addr $PRIVATE_VLAN6_ROUTER")
            }
            append(" --dnsgw 127.0.0.1:$dnsPort --enable-udprelay")
        }
        if (DEBUG) {
            Log.d(TAG, (command))
        }
        tun2socks = Utility.exec(command) { stopMe() }

        // Try to send the Fd through socket.
        if (DEBUG) Log.d(TAG, "send sock_path:" + File(applicationInfo.dataDir + "/sock_path").absolutePath)
        sendFd(File(applicationInfo.dataDir + "/sock_path").absolutePath)
        mRunning = true
        applicationContext.sendBroadcast(Intent(INTENT_CONNECTED))
    }

    private fun sendFd(path: String) {
        var tries = 0
        val fd = mInterface!!.fileDescriptor
        while (true) try {
            Log.d(TAG, "sdFd tries: $tries")
            Thread.sleep(140L * tries)
            LocalSocket().use { localSocket ->
                localSocket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                localSocket.setFileDescriptorsForSend(arrayOf(fd))
                localSocket.outputStream.write(42)
            }
            break
        } catch (e: Exception) {
            if (tries > 5) throw e
            tries += 1
        }
    }
}
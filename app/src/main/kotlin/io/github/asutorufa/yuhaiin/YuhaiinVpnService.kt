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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_CONNECTED
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_CONNECTING
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_DISCONNECTED
import io.github.asutorufa.yuhaiin.util.Constants.INTENT_DISCONNECTING
import io.github.asutorufa.yuhaiin.util.Profile
import io.github.asutorufa.yuhaiin.util.ProfileManager
import io.github.asutorufa.yuhaiin.util.Routes
import io.github.asutorufa.yuhaiin.util.Utility
import java.io.File
import java.util.*


class YuhaiinVpnService : VpnService() {
    private val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
    private val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
    private val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
    private val TAG = this.javaClass.simpleName
    private val VPN_MTU = 1500;
    private val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1";
    private var mRunning = false
    private var mStopping = false
    var mg: ConnectivityManager? = null
    private var mInterface: ParcelFileDescriptor? = null
    private var yuhaiin: Thread? = null
    private var tun2socks: Process? = null
    private lateinit var profile: Profile
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

        yuhaiin?.interrupt()
        tun2socks?.destroy()

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

        if (mRunning) {
            applicationContext.sendBroadcast(Intent(INTENT_CONNECTED))
            return START_STICKY
        }

        profile = ProfileManager(applicationContext).default

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
                .setContentText(String.format(getString(R.string.notify_msg), profile.name))
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentIntent(contentIntent)
                .build()
        )

        configure(
            profile.name,
            profile.route,
            profile.fakeDnsCidr,
            profile.httpServerPort,
            profile.isPerApp,
            profile.isBypassApp,
            profile.appList as MutableSet<String>,
            profile.hasIPv6()
        )


        if (DEBUG) Log.d(TAG, "fd: ${mInterface?.fd}")

        try {
            start(
                profile.yuhaiinHost,
                profile.httpServerPort,
                profile.socks5ServerPort,
                profile.username,
                profile.password,
                profile.fakeDnsCidr,
                profile.dnsPort,
                profile.hasIPv6()
            )
        } catch (e: Exception) {
            e.printStackTrace()
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
        apps: MutableSet<String>,
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

        if (DEBUG) Log.d(TAG, "configure: $apps")
        when (perApp) {
            true -> {
                apps.remove("")
                if (bypass) apps.add(BuildConfig.APPLICATION_ID) else apps.remove(BuildConfig.APPLICATION_ID)
                fun process(app: String) =
                    try {
                        if (bypass) b.addDisallowedApplication(app.trim()) else b.addAllowedApplication(app.trim())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                apps.map { process(it) }
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
        host: String,
        httpport: Int,
        socks5port: Int,
        user: String,
        passwd: String,
        fakeDnsCidr: String,
        dnsPort: Int,
        ipv6: Boolean
    ) {
        if (host != "") {
            yuhaiin = Yuhaiin(
                host,
                getExternalFilesDir("yuhaiin")!!.absolutePath,
                "127.0.0.1:$dnsPort",
                "127.0.0.1:$socks5port",
                "127.0.0.1:$httpport",
                fakeDnsCidr,
                fakeDnsCidr.isNotEmpty(),
            ) { stopMe() }
            yuhaiin!!.start()
            Toast.makeText(this, "start yuhaiin success, listen at: $host.", Toast.LENGTH_LONG).show()
        }

        var command = java.lang.String.format(
            Locale.US,
            "%s/libtun2socks.so"
                    + " --netif-ipaddr " + PRIVATE_VLAN4_ROUTER
                    // + " --netif-netmask 255.255.255.252"
                    + " --socks-server-addr 127.0.0.1:%d"
                    // + " --tunfd %d"
                    + " --tunmtu 1500"
                    + " --loglevel warning"
                    // + " --pid %s/tun2socks.pid"
                    + " --sock-path %s/sock_path", applicationInfo.nativeLibraryDir, socks5port, applicationInfo.dataDir
        )
        if (user != "" && passwd != "") {
            command += " --username $user"
            command += " --password $passwd"
        }
        if (ipv6) {
            command += " --netif-ip6addr $PRIVATE_VLAN6_ROUTER"
        }
        command += " --dnsgw 127.0.0.1:$dnsPort --enable-udprelay"

        if (DEBUG) {
            Log.d(TAG, (command))
        }

        tun2socks = Utility.exec(command) { stopMe() }
        Toast.makeText(this, "start tun2socks success.", Toast.LENGTH_LONG).show()

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
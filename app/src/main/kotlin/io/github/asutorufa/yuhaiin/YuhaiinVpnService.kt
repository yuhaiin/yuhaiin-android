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
import io.github.asutorufa.yuhaiin.database.Profile
import io.github.asutorufa.yuhaiin.util.Routes
import io.github.asutorufa.yuhaiin.util.Utility
import yuhaiin.App
import yuhaiin.DNS
import yuhaiin.DNSSetting
import yuhaiin.Opts
import java.io.File


class YuhaiinVpnService : VpnService() {
    companion object {
        private const val INTENT_PREFIX = "YUHAIIN"
        const val INTENT_DISCONNECTED = INTENT_PREFIX + "DISCONNECTED"
        const val INTENT_CONNECTED = INTENT_PREFIX + "CONNECTED"
        const val INTENT_CONNECTING = INTENT_PREFIX + "CONNECTING"
        const val INTENT_DISCONNECTING = INTENT_PREFIX + "DISCONNECTING"
        const val INTENT_ERROR = INTENT_PREFIX + "ERROR"

        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        private const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
        private const val VPN_MTU = 1500
        private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
    }

    private val tag = this.javaClass.simpleName

    private var mRunning = false
    private var mStopping = false

    private var mg: ConnectivityManager? = null
    private var mInterface: ParcelFileDescriptor? = null
    private var yuhaiin: App = App()
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
        if (mStopping || !mRunning) return

        mStopping = true
        applicationContext.sendBroadcast(Intent(INTENT_DISCONNECTING))
        stopForeground(true)

        yuhaiin.stop()

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
        Log.d(tag, "onRevoke")
        stopMe()
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        stopMe()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onLowMemory() {
        Log.d(tag, "onLowMemory")
        stopMe()
        super.onLowMemory()
    }

    override fun stopService(name: Intent?): Boolean {
        Log.d(tag, "stopService")
        stopMe()
        return super.stopService(name)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applicationContext.sendBroadcast(Intent(INTENT_CONNECTING))

        Log.d(tag, "starting")

        if (mRunning) {
            applicationContext.sendBroadcast(Intent(INTENT_CONNECTED))
            return START_STICKY
        }


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
        val builder = NotificationCompat.Builder(this, packageName)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val db = MainApplication.db.ProfileDao()
            val profile = db.getProfileByName(db.getLastProfile() ?: "Default")!!

            startForeground(
                1, builder
                    .setContentTitle("yuhaiin running")
                    .setContentText(String.format(getString(R.string.notify_msg), profile.name))
                    .setSmallIcon(R.drawable.ic_vpn)
                    .setContentIntent(contentIntent)
                    .build()
            )

            configure(profile)
            Log.d(tag, "fd: ${mInterface?.fd}")
            start(profile)

        } catch (e: Exception) {
            e.printStackTrace()
            applicationContext.sendBroadcast(Intent(INTENT_ERROR).also {
                it.putExtra(
                    "message",
                    e.toString()
                )
            })
            stopMe()
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
            if (mg == null) mg = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val req =
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).build()
            mg?.requestNetwork(req, defaultNetworkCallback)
        }
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && profile.httpServerPort != 0) {
            b.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", profile.httpServerPort))
        }

        // Add the default DNS
        // Note that this DNS is just a stub.
        // Actual DNS requests will be redirected through pdnsd.
//        b.addRoute("223.5.5.5", 32);

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

        Log.d(tag, "start, yuhaiin: $profile")

        if (profile.yuhaiinPort > 0) {

            var address = "127.0.0.1"
            if (profile.allowLan) address = "0.0.0.0"

            val opts = Opts().apply {
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
                }
            }
            try {
                yuhaiin.start(opts)
            } catch (e: Exception) {
                throw e
            }
        }

        val command = buildString {
            append("${applicationInfo.nativeLibraryDir}/libtun2socks.so")
            append(" --netif-ipaddr $PRIVATE_VLAN4_ROUTER")
            // + " --netif-netmask 255.255.255.252"
            append(" --socks-server-addr 127.0.0.1:${profile.socks5ServerPort}")
            // + " --tunfd %d"
            append(" --tunmtu 1500")
            append(" --loglevel warning")
            // + " --pid %s/tun2socks.pid"
            append(" --sock-path ${applicationInfo.dataDir}/sock_path")

            if (profile.username.isNotEmpty() && profile.password.isNotEmpty()) {
                append(" --username ${profile.username}")
                append(" --password ${profile.password}")
            }
            if (profile.hasIPv6) {
                append(" --netif-ip6addr $PRIVATE_VLAN6_ROUTER")
            }
            append(" --dnsgw 127.0.0.1:${profile.dnsPort} --enable-udprelay")
        }
        Log.d(tag, (command))
        tun2socks = Utility.exec(command) { stopMe() }

        // Try to send the Fd through socket.
        Log.d(tag, "send sock_path:" + File(applicationInfo.dataDir + "/sock_path").absolutePath)

        sendFd(File(applicationInfo.dataDir + "/sock_path").absolutePath)
        mRunning = true
        applicationContext.sendBroadcast(Intent(INTENT_CONNECTED))
    }

    private fun sendFd(path: String) {
        var tries = 0
        val fd = mInterface!!.fileDescriptor
        while (true) try {
            Log.d(tag, "sdFd tries: $tries")
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
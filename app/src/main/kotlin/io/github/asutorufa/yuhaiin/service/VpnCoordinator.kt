package io.github.asutorufa.yuhaiin.service

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.github.asutorufa.yuhaiin.core.NativeCore
import io.github.asutorufa.yuhaiin.core.NativeHost

/**
 * Same-process coordinator between the Go core and [YuhaiinVpnService], both in ":bg".
 *
 * The core asks for an interface with [establish] and returns it with [release]; packets never
 * cross Binder. Ownership: this object keeps each generation's ParcelFileDescriptor until the core
 * releases that generation. Go reads a duplicate it owns, so neither side closes the other's
 * descriptor. Releases and revokes name a generation, so stale callbacks are ignored.
 */
object VpnCoordinator {
    private const val TAG = "VpnCoordinator"
    private const val SERVICE_TIMEOUT_SECONDS = 10L

    private lateinit var app: Application
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val interfaces = LinkedHashMap<Long, ParcelFileDescriptor>()
    @Volatile private var service: YuhaiinVpnService? = null
    private var serviceReady = CountDownLatch(1)
    private var bound = false

    private val _status = MutableStateFlow(PlatformVpnStatus())
    val status: StateFlow<PlatformVpnStatus> = _status

    /** Currently selected physical Network, published by the monitor. */
    @Volatile var underlyingNetwork: Network? = null
        private set

    private val networkMonitor by lazy {
        UnderlyingNetworkMonitor((app as MainApplication).connectivity) { network ->
            underlyingNetwork = network
            service?.updateUnderlyingNetwork(network)
            networkListener?.invoke(network)
        }
    }
    @Volatile var networkListener: ((Network?) -> Unit)? = null
    private var monitoring = false

    fun init(application: Application) {
        if (::app.isInitialized) return
        app = application
        // The core calls these on Go threads; Binder, UI and Go never share a lock with them.
        NativeHost.vpnEstablish = { generation, config -> establish(generation, config.decodeToString()) }
        NativeHost.socketProtect = socketProtect
        NativeHost.socketBind = socketBind
        NativeHost.register("vpn.authorization") { authorization().toByteArray() }
        NativeHost.register("vpn.release") {
            release(
                kotlinx.serialization.json.Json.parseToJsonElement(it!!.decodeToString())
                    .let { e -> (e as kotlinx.serialization.json.JsonObject)["generation"] }
                    .let { g -> (g as kotlinx.serialization.json.JsonPrimitive).content.toLong() }
            )
            null
        }
        NativeHost.register("vpn.status_changed") { statusChanged(it!!.decodeToString()); null }
        _status.value = PlatformVpnStatus.parse(NativeCore.vpnStatus())
    }

    // ---- core callbacks (called on Go threads) ----

    private fun authorization(): String =
        VpnAuthorization(
                prepared = runCatching { VpnService.prepare(app) == null }.getOrDefault(false),
                consent = PrivacyConsent.forContext(app).has(Purpose.VPN),
                alwaysOn = service?.isAlwaysOnCompat() ?: false,
                lockdown = service?.isLockdownCompat() ?: false,
            )
            .encode()

    private fun establish(generation: Long, config: String): Int {
        val parsed = PlatformVpnConfig.parse(config)
        if (!PrivacyConsent.forContext(app).has(Purpose.VPN))
            throw IllegalStateException("consent_required: the VPN data disclosure was not accepted")
        if (VpnService.prepare(app) != null)
            throw IllegalStateException("permission_required: Android VPN permission is not granted")
        val vpn = obtainService()
        val descriptor =
            vpn.establish(parsed)
                ?: throw IllegalStateException(
                    "permission_required: VpnService.Builder.establish() returned null"
                )
        synchronized(lock) { interfaces[generation] = descriptor }
        Log.i(TAG, "established generation $generation mtu=${parsed.mtu}")
        return descriptor.fd
    }

    private fun release(generation: Long) {
        val (descriptor, empty) =
            synchronized(lock) { interfaces.remove(generation) to interfaces.isEmpty() }
        if (descriptor != null) {
            runCatching { descriptor.close() }.onFailure { Log.w(TAG, "close generation", it) }
            Log.i(TAG, "released generation $generation")
        }
        if (empty) main.post { stopServiceIfIdle() }
    }

    private fun statusChanged(status: String) {
        _status.value = PlatformVpnStatus.parse(status)
    }

    // ---- service binding ----

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val local = binder as? YuhaiinVpnService.LocalBinder ?: return
                synchronized(lock) {
                    service = local.service
                    serviceReady.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                synchronized(lock) {
                    service = null
                    serviceReady = CountDownLatch(1)
                }
            }
        }

    private fun obtainService(): YuhaiinVpnService {
        service?.let {
            return it
        }
        val latch = synchronized(lock) { serviceReady }
        main.post {
            if (!bound) {
                bound =
                    app.bindService(
                        Intent(app, YuhaiinVpnService::class.java)
                            .setAction(YuhaiinVpnService.ACTION_PLATFORM),
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
            }
        }
        check(Looper.myLooper() != Looper.getMainLooper()) { "establish must not run on main" }
        if (!latch.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            throw IllegalStateException("provider_unavailable: VpnService did not start")
        return service ?: throw IllegalStateException("provider_unavailable: VpnService stopped")
    }

    private fun stopServiceIfIdle() {
        if (synchronized(lock) { interfaces.isNotEmpty() }) return
        if (bound) {
            runCatching { app.unbindService(connection) }
            bound = false
            synchronized(lock) {
                service = null
                serviceReady = CountDownLatch(1)
            }
        }
    }

    /** Called by the service when Android revokes the VPN. */
    internal fun onRevoked() {
        val generations = synchronized(lock) { interfaces.keys.toList() }
        // Only the newest generation can be current; older ones are being released.
        val generation = generations.lastOrNull() ?: return
        // Off the main thread: the core may be waiting for this thread to bind the
        // VpnService for a rebuild while it holds the accepter lock.
        Thread({ NativeCore.notifyVpnRevoked(generation) }, "vpn-revoke").start()
    }

    /** Releases every interface after the core stopped or crashed. */
    fun releaseAll() {
        val all = synchronized(lock) { interfaces.keys.toList() }
        all.forEach(::release)
    }

    fun hasInterface(): Boolean = synchronized(lock) { interfaces.isNotEmpty() }

    // ---- networks and sockets, used by the core whether or not the VPN runs ----

    fun startMonitoring() {
        if (monitoring) return
        val enabled =
            MainApplication.store
                .getString(Constants.REGISTER_UNDERLYING_NETWORK_CALLBACK_KEY)
                .ifBlank { "true" }
                .toBoolean()
        if (!enabled) return
        monitoring = true
        main.post { networkMonitor.start() }
    }

    fun stopMonitoring() {
        if (!monitoring) return
        monitoring = false
        underlyingNetwork = null
        main.post { runCatching { networkMonitor.stop() } }
    }

    /** Protects sockets from the VPN. Without a VPN there is nothing to bypass. */
    private val socketProtect: (Int) -> Boolean = { fd ->
        val vpn = service
        if (vpn == null || !hasInterface()) true else vpn.protect(fd)
    }

    private val socketBind: (Int, String) -> Long = { socket, iface -> bindOutboundSocket(socket, iface) }

    // Called only for outbound sockets, after protect(). VpnService's setUnderlyingNetworks is a
    // hint; it does not bind an individual socket.
    private fun bindOutboundSocket(socket: Int, iface: String): Long {
        val vpn = service
        if (vpn != null && hasInterface() && !vpn.protect(socket)) return -1L
        val connectivity = (app as MainApplication).connectivity
        return runCatching {
                fun physical(network: Network): Boolean =
                    connectivity
                        .getNetworkCapabilities(network)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true
                if (iface.isNotBlank()) {
                    // An explicit interface must never silently fall back to another network.
                    val network =
                        (if (monitoring) networkMonitor.networkForInterface(iface) else null)
                            ?: @Suppress("DEPRECATION")
                            connectivity.allNetworks.firstOrNull {
                                physical(it) &&
                                    connectivity.getLinkProperties(it)?.interfaceName == iface
                            }
                            ?: return -1L
                    bind(socket, network)
                    return network.networkHandle
                }
                val network =
                    underlyingNetwork
                        ?: connectivity.activeNetwork?.takeIf(::physical)
                        ?: return 0L
                // The selection can lag a handover; keep Android's default then.
                runCatching { bind(socket, network) }
                    .fold(
                        onSuccess = { network.networkHandle },
                        onFailure = {
                            Log.d(TAG, "selected Network binding failed, using default", it)
                            0L
                        },
                    )
            }
            .getOrElse {
                Log.d(TAG, "outbound Network binding failed", it)
                -1L
            }
    }

    private fun bind(socket: Int, network: Network) {
        // fromFd duplicates the descriptor; the binding applies to the shared socket.
        ParcelFileDescriptor.fromFd(socket).use { network.bindSocket(it.fileDescriptor) }
    }

}

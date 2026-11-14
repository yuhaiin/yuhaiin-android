package io.github.asutorufa.yuhaiin

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Process
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import io.github.asutorufa.yuhaiin.core.NativeHost
import io.github.asutorufa.yuhaiin.core.NativeRuntime
import io.github.asutorufa.yuhaiin.core.Store
import io.github.asutorufa.yuhaiin.core.remoteStore
import io.github.asutorufa.yuhaiin.data.AppSettings
import io.github.asutorufa.yuhaiin.service.VpnActions
import io.github.asutorufa.yuhaiin.update.UpdateManager
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

open class MainApplication : Application() {

    companion object {
        lateinit var store: Store
        // Full restore uses Android's restricted Application and may deliver queued broadcasts.
        val initialized: Boolean
            get() = ::store.isInitialized && ::settings.isInitialized

        lateinit var updateManager: UpdateManager
        lateinit var settings: AppSettings
        lateinit var installedApps: io.github.asutorufa.yuhaiin.data.InstalledAppsRepository

        fun getAddresses(): List<String> =
            try {
                NetworkInterface.getNetworkInterfaces()
                    ?.asSequence()
                    ?.filter {
                        it.isUp &&
                            !it.isLoopback &&
                            !it.isVirtual &&
                            !it.name.startsWith("dummy") &&
                            !it.name.startsWith("lo")
                    }
                    ?.flatMap { nif ->
                        nif.interfaceAddresses.asSequence().mapNotNull { ia ->
                            ia.address?.hostAddress?.substringBefore('%')?.let {
                                "$it (${nif.name})"
                            }
                        }
                    }
                    ?.toList() ?: emptyList()
            } catch (e: java.net.SocketException) {
                Log.e("MainApplication", "Could not get network interfaces", e)
                emptyList()
            }
    }

    val connectivity by lazy { this.getSystemService<ConnectivityManager>()!! }

    private fun processLookupMode(): String =
        store.getString(Constants.PROCESS_LOOKUP_MODE_KEY).ifBlank { "always" }

    private fun dumpUid(protocol: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int): Int =
        if (processLookupMode() == "off") {
            0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivity.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(srcIp, srcPort),
                InetSocketAddress(destIp, destPort),
            )
        } else {
            0
        }

    private fun uidName(uid: Int): String {
        if (processLookupMode() == "off") return ""
        return packageManager.getNameForUid(uid) ?: "unknown"
    }

    override fun onCreate() {
        super.onCreate()
        // The Go runtime lives in ":bg" only. The UI process reaches preferences through
        // StoreProvider, so it never loads libyuhaiin.so.
        if (isBackgroundProcess()) {
            NativeRuntime.ensureInit(this)
            store = NativeRuntime.store
            installHostCallbacks()
        } else {
            store = remoteStore(this)
        }
        updateManager = UpdateManager(this)
        installedApps =
            io.github.asutorufa.yuhaiin.data.InstalledAppsRepository(
                packageManager,
                io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.forContext(this),
            )
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings =
            AppSettings(
                store,
                settingsScope,
                java.io.File(noBackupFilesDir, "settings-init.lock"),
            ) {
                ensureBatteryDefaults()
                initRoutes()
            }
        settingsScope.launch {
            settings.snapshot
                .map { it[Constants.NOTIFICATION_SPEED_KEY] }
                .distinctUntilChanged()
                .collect {
                    // Broadcast only after the Store write is visible to the remote VPN process.
                    settings.flush()
                    sendBroadcast(Intent(VpnActions.REFRESH_MONITORING).setPackage(packageName))
                }
        }
    }

    private fun ensureBatteryDefaults() {
        // Preserve the legacy notification preference, including an explicit false.
        // Display sampling is now managed by the VPN service's consumers.
        if (store.getString(Constants.NOTIFICATION_SPEED_KEY).isBlank()) {
            store.putBoolean(
                Constants.NOTIFICATION_SPEED_KEY,
                store.getBoolean(Constants.NETWORK_SPEED_KEY),
            )
        }
        if (store.getString(Constants.PROCESS_LOOKUP_MODE_KEY).isBlank()) {
            store.putString(Constants.PROCESS_LOOKUP_MODE_KEY, "always")
        }
        if (store.getString(Constants.VPN_MTU_PROFILE_KEY).isBlank()) {
            store.putString(Constants.VPN_MTU_PROFILE_KEY, "auto")
        }
        if (store.getString(Constants.REGISTER_UNDERLYING_NETWORK_CALLBACK_KEY).isBlank()) {
            store.putBoolean(Constants.REGISTER_UNDERLYING_NETWORK_CALLBACK_KEY, true)
        }
        if (store.getString(Constants.BOOT_CONNECT_POLICY_KEY).isBlank()) {
            store.putString(Constants.BOOT_CONNECT_POLICY_KEY, "always")
        }
    }

    private fun initRoutes() {
        val raw = store.getString(Constants.SAVED_ROUTES_LIST)
        // Do not replace user rules when the persisted list cannot be decoded.
        val savedRoutes =
            if (raw.isBlank()) emptySet<String>()
            else kotlinx.serialization.json.Json.decodeFromString<Set<String>>(raw)
        if (savedRoutes.isEmpty()) {
            val all = Constants.ALL_ROUTE
            val nonLocal = Constants.NON_LOCAL_ROUTE

            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + all,
                "0.0.0.0/0\n::/0",
            )
            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + nonLocal,
                resources.getStringArray(R.array.all_routes_except_local).joinToString("\n"),
            )
            // Publish the list last, so an interrupted first launch retries initialization.
            store.putStringSet(Constants.SAVED_ROUTES_LIST, setOf(all, nonLocal))
        } else if (LEGACY_NON_CHINESE_ROUTE in savedRoutes) {
            // The built-in China IP list was retired: GeoIP rules in the core cover it. Drop the
            // preset and move anyone still on it to the default route.
            store.putStringSet(Constants.SAVED_ROUTES_LIST, savedRoutes - LEGACY_NON_CHINESE_ROUTE)
            store.putString(Constants.ROUTE_CONTENT_PREFIX + LEGACY_NON_CHINESE_ROUTE, "")
            if (store.getString(Constants.ROUTE_KEY) == LEGACY_NON_CHINESE_ROUTE) {
                store.putString(Constants.ROUTE_KEY, Constants.ALL_ROUTE)
            }
        }
    }

    private fun isBackgroundProcess(): Boolean =
        (if (Build.VERSION.SDK_INT >= 28) getProcessName()
        else
            runCatching { java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000') }
                .getOrDefault(""))
            .endsWith(":bg")

    /** Android answers for the Go core: network interfaces and process (uid) lookup. */
    private fun installHostCallbacks() {
        NativeHost.uidLookup = ::dumpUid
        NativeHost.register("interfaces.list") { interfacesJson() }
        NativeHost.register("routes.list") { routesJson() }
        NativeHost.register("uid.name") {
            val uid = Json.parseToJsonElement(it!!.decodeToString())
                .let { e -> (e as kotlinx.serialization.json.JsonObject)["uid"] as JsonPrimitive }
                .content.toInt()
            uidName(uid).toByteArray()
        }
    }

    /**
     * The routes Android holds for each network. Apps may not read the kernel
     * routing table, but LinkProperties lists every network's routes. ownVpn
     * marks the routes of this app's VPN; before API 30 the owner is unknown,
     * and Android runs one VPN at a time, so a VPN network is taken as ours.
     */
    @Suppress("DEPRECATION")
    private fun routesJson(): ByteArray {
        val cm = getSystemService<ConnectivityManager>() ?: return "[]".toByteArray()
        val list = mutableListOf<kotlinx.serialization.json.JsonElement>()
        for (network in cm.allNetworks) {
            val lp = cm.getLinkProperties(network) ?: continue
            val caps = cm.getNetworkCapabilities(network)
            val ownVpn = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (Build.VERSION.SDK_INT < 30 || caps.ownerUid == Process.myUid())
            for (r in lp.routes) {
                list.add(
                    buildJsonObject {
                        put("destination", r.destination.toString())
                        // An unspecified gateway (0.0.0.0, ::) means directly connected.
                        r.gateway?.takeUnless { it.isAnyLocalAddress }?.let {
                            put("gateway", it.hostAddress?.substringBefore('%').orEmpty())
                        }
                        put("interface", r.`interface` ?: lp.interfaceName.orEmpty())
                        if (Build.VERSION.SDK_INT >= 33) {
                            put(
                                "type",
                                when (r.type) {
                                    RouteInfo.RTN_UNREACHABLE -> "unreachable"
                                    RouteInfo.RTN_THROW -> "throw"
                                    else -> "unicast"
                                },
                            )
                        }
                        put("ownVpn", ownVpn)
                    }
                )
            }
        }
        return JsonArray(list).toString().toByteArray()
    }

    private fun interfacesJson(): ByteArray {
        val list = mutableListOf<kotlinx.serialization.json.JsonElement>()
        for (nif in NetworkInterface.getNetworkInterfaces().toList()) {
            try {
                list.add(
                    buildJsonObject {
                        put("name", nif.name)
                        put("displayName", nif.displayName)
                        put("index", nif.index)
                        put("mtu", nif.mtu)
                        put("isVirtual", nif.isVirtual)
                        nif.hardwareAddress?.let {
                            put("hardwareAddr", kotlin.io.encoding.Base64.Default.encode(it))
                        }
                        put("isUp", nif.isUp)
                        put("broadcast", nif.supportsMulticast())
                        put("isLoopback", nif.isLoopback)
                        put("isPointToPoint", nif.isPointToPoint)
                        put("supportsMulticast", nif.supportsMulticast())
                        put(
                            "addresses",
                            JsonArray(
                                nif.interfaceAddresses.map { ia ->
                                    buildJsonObject {
                                        put("address", ia.address.toString().trimStart('/'))
                                        put("mask", ia.networkPrefixLength.toInt())
                                        put("broadcast", ia.broadcast?.toString()?.trimStart('/').orEmpty())
                                    }
                                }
                            ),
                        )
                    }
                )
            } catch (_: Exception) {
                continue
            }
        }
        return JsonArray(list).toString().toByteArray()
    }
}

fun Store.getStringSet(key: String?): Set<String> {
    val data = getString(key.orEmpty())
    if (data.isEmpty()) return HashSet()
    return runCatching { Json.decodeFromString<Set<String>>(data) }
        .getOrElse {
            Log.e("Store", "Invalid string set for $key", it)
            emptySet()
        }
}

fun Store.putStringSet(key: String?, values: Set<String?>?) {
    putString(key.orEmpty(), Json.encodeToString(values))
}

/** Name of the retired built-in China IP route profile, kept only to migrate it away. */
private const val LEGACY_NON_CHINESE_ROUTE = "Non-Chinese IPs"

package io.github.asutorufa.yuhaiin

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import go.Seq
import io.github.asutorufa.yuhaiin.data.AppSettings
import io.github.asutorufa.yuhaiin.update.UpdateManager
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import yuhaiin.AddressIter
import yuhaiin.AddressPrefix
import yuhaiin.Interface
import yuhaiin.InterfaceIter
import yuhaiin.Interfaces
import yuhaiin.Store
import yuhaiin.Yuhaiin

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

    inner class UidDumper : yuhaiin.UidDumper {
        private fun processLookupMode(): String =
            store.getString(Constants.PROCESS_LOOKUP_MODE_KEY).ifBlank { "always" }

        override fun dumpUid(
            p0: Int,
            srcIp: String?,
            srcPort: Int,
            destIp: String?,
            destPort: Int,
        ): Int =
            if (processLookupMode() == "off") {
                0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connectivity.getConnectionOwnerUid(
                    p0,
                    InetSocketAddress(srcIp, srcPort),
                    InetSocketAddress(destIp, destPort),
                )
            } else {
                0
            }

        override fun getUidInfo(p0: Int): String {
            if (processLookupMode() == "off") return ""
            return packageManager.getNameForUid(p0) ?: "unknown"
        }
    }

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Yuhaiin.setSavePath(
            (getExternalFilesDir("yuhaiin") ?: java.io.File(filesDir, "yuhaiin"))
                .apply { mkdirs() }
                .absolutePath
        )
        Yuhaiin.setConfigLockPath(java.io.File(noBackupFilesDir, "configuration.lock").absolutePath)
        store = Yuhaiin.getStore()
        updateManager = UpdateManager(this)
        installedApps = io.github.asutorufa.yuhaiin.data.InstalledAppsRepository(packageManager)
        settings =
            AppSettings(
                store,
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
                java.io.File(noBackupFilesDir, "settings-init.lock"),
            ) {
                ensureBatteryDefaults()
                initRoutes()
            }
        Yuhaiin.setInterfaces(GetInterfaces())
        Yuhaiin.setProcessDumper(UidDumper())
    }

    private fun ensureBatteryDefaults() {
        // The core's callback also feeds the native status page. Preserve the old notification
        // preference separately before enabling collection, including an explicit false.
        if (store.getString(Constants.NOTIFICATION_SPEED_KEY).isBlank()) {
            store.putBoolean(
                Constants.NOTIFICATION_SPEED_KEY,
                store.getBoolean(Constants.NETWORK_SPEED_KEY),
            )
        }
        store.putBoolean(Constants.NETWORK_SPEED_KEY, true)
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
            val nonChn = Constants.NON_CHINESE_ROUTE

            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + all,
                "0.0.0.0/0\n::/0",
            )
            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + nonLocal,
                resources.getStringArray(R.array.all_routes_except_local).joinToString("\n"),
            )
            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + nonChn,
                resources.getStringArray(R.array.simple_route).joinToString("\n"),
            )
            // Publish the list last, so an interrupted first launch retries initialization.
            store.putStringSet(Constants.SAVED_ROUTES_LIST, setOf(all, nonLocal, nonChn))
        }
    }

    class InterfaceIterImpl(private val data: MutableList<Interface>) : InterfaceIter {
        private var index = 0

        override fun next(): Interface? {
            return if (index < data.size) {
                data[index++]
            } else {
                null
            }
        }

        override fun hasNext(): Boolean = index < data.size

        override fun reset() {
            index = 0
        }
    }

    class AddressIterImpl(private val data: ArrayList<AddressPrefix>) : AddressIter {
        private var index = 0

        override fun next(): AddressPrefix? {
            return if (index < data.size) {
                data[index++]
            } else {
                null
            }
        }

        override fun hasNext(): Boolean {
            return index < data.size
        }

        override fun reset() {
            index = 0
        }
    }

    inner class GetInterfaces : Interfaces {
        override fun getInterfaces(): InterfaceIter {
            val interfaces: List<NetworkInterface> =
                NetworkInterface.getNetworkInterfaces().toList()
            val sb = mutableListOf<Interface>()
            for (nif in interfaces) {

                try {
                    sb.add(
                        Interface().apply {
                            name = nif.name
                            displayName = nif.displayName
                            index = nif.index
                            mtu = nif.mtu
                            isVirtual = nif.isVirtual
                            hardwareAddr = nif.hardwareAddress
                            isUp = nif.isUp
                            broadcast = nif.supportsMulticast()
                            isLoopback = nif.isLoopback
                            isPointToPoint = nif.isPointToPoint
                            supportsMulticast = nif.supportsMulticast()
                            address =
                                AddressIterImpl(
                                    ArrayList<AddressPrefix>().apply {
                                        for (ia in nif.interfaceAddresses) {
                                            add(
                                                AddressPrefix().apply {
                                                    address = ia.address.toString().trimStart('/')
                                                    mask = ia.networkPrefixLength.toInt()
                                                    broadcast =
                                                        ia.broadcast?.toString()?.trimStart('/')
                                                }
                                            )
                                        }
                                    }
                                )
                        }
                    )
                } catch (_: Exception) {
                    continue
                }
            }

            return InterfaceIterImpl(sb)
        }
    }
}

fun Store.getStringSet(key: String?): Set<String> {
    val data = getString(key)
    if (data.isEmpty()) return HashSet()
    return runCatching { Json.decodeFromString<Set<String>>(data) }
        .getOrElse {
            Log.e("Store", "Invalid string set for $key", it)
            emptySet()
        }
}

fun Store.putStringSet(key: String?, values: Set<String?>?) {
    putString(key, Json.encodeToString(values))
}

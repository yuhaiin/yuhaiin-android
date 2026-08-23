package io.github.asutorufa.yuhaiin

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import io.github.asutorufa.yuhaiin.update.UpdateManager
import yuhaiin.AddressIter
import yuhaiin.AddressPrefix
import yuhaiin.Interface
import yuhaiin.InterfaceIter
import yuhaiin.Interfaces
import yuhaiin.Store
import yuhaiin.Yuhaiin
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface

open class MainApplication : Application() {

    companion object {
        lateinit var store: Store
        lateinit var updateManager: UpdateManager

        fun stateDatabase(context: Context): File =
            File(File(context.filesDir, "yuhaiin").apply { mkdirs() }, "state.db")

        private fun migrateLegacyState(context: Context, destination: File) {
            if (destination.exists()) return

            val legacyDirectory = context.getExternalFilesDir("yuhaiin") ?: return
            if (!File(legacyDirectory, "state.db").exists()) return

            destination.parentFile?.mkdirs()
            listOf("state.db", "state.db-wal", "state.db-shm").forEach { name ->
                val source = File(legacyDirectory, name)
                if (!source.exists()) return@forEach
                FileInputStream(source).use { input ->
                    FileOutputStream(File(destination.parentFile, name)).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        fun getAddresses(): List<String> = try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter {
                    it.isUp &&
                            !it.isLoopback &&
                            !it.isVirtual &&
                            !it.name.startsWith("dummy") &&
                            !it.name.startsWith("lo")
                }
                ?.flatMap { nif ->
                    nif.interfaceAddresses.asSequence().mapNotNull { ia ->
                        ia.address?.hostAddress?.substringBefore('%')
                            ?.let { "$it (${nif.name})" }
                    }
                }?.toList() ?: emptyList()
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
            destPort: Int
        ): Int =
            if (processLookupMode() == "off") {
                0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connectivity.getConnectionOwnerUid(
                    p0,
                    InetSocketAddress(srcIp, srcPort),
                    InetSocketAddress(destIp, destPort)
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
        val database = stateDatabase(this)
        migrateLegacyState(this, database)
        Yuhaiin.setSavePath(database.parentFile!!.path)
        store = Yuhaiin.getStore()
        updateManager = UpdateManager(this)
        ensureBatteryDefaults()
        Yuhaiin.setInterfaces(GetInterfaces())
        Yuhaiin.setProcessDumper(UidDumper())
        CoroutineScope(Dispatchers.IO).launch {
            initRoutes()
        }
    }

    private fun ensureBatteryDefaults() {
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
        val savedRoutes = store.getStringSet(Constants.SAVED_ROUTES_LIST)
        if (savedRoutes.isEmpty()) {
            val all = getString(R.string.adv_route_all)
            val nonLocal = getString(R.string.adv_route_non_local)
            val nonChn = getString(R.string.adv_route_non_chn)

            store.putStringSet(Constants.SAVED_ROUTES_LIST, setOf(all, nonLocal, nonChn))

            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + all,
                "0.0.0.0/0\n::/0"
            )
            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + nonLocal,
                resources.getStringArray(R.array.all_routes_except_local).joinToString("\n")
            )
            store.putString(
                Constants.ROUTE_CONTENT_PREFIX + nonChn,
                resources.getStringArray(R.array.simple_route).joinToString("\n")
            )
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

    class AddressIterImpl(
        private val data: ArrayList<AddressPrefix>
    ) : AddressIter {
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
                    sb.add(Interface().apply {
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
                        address = AddressIterImpl(ArrayList<AddressPrefix>().apply {
                            for (ia in nif.interfaceAddresses) {
                                add(AddressPrefix().apply {
                                    address = ia.address.toString().trimStart('/')
                                    mask = ia.networkPrefixLength.toInt()
                                    broadcast = ia.broadcast?.toString()?.trimStart('/')
                                })
                            }
                        })
                    })
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
    return Json.decodeFromString<Set<String>>(data)
}

fun Store.putStringSet(key: String?, values: Set<String?>?) {
    putString(key, Json.encodeToString(values))
}

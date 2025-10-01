package io.github.asutorufa.yuhaiin

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import go.Seq
import kotlinx.serialization.json.Json
import yuhaiin.AddressIter
import yuhaiin.AddressPrefix
import yuhaiin.Interface
import yuhaiin.InterfaceIter
import yuhaiin.Interfaces
import yuhaiin.Store
import yuhaiin.Yuhaiin
import java.net.InetSocketAddress
import java.net.NetworkInterface

open class MainApplication : Application() {

    companion object {
        lateinit var store: Store
    }

    val connectivity by lazy { this.getSystemService<ConnectivityManager>()!! }

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


    private val uidDumper = UidDumper()

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)

        Yuhaiin.setSavePath(getExternalFilesDir("yuhaiin").toString())
        Yuhaiin.setDataDir(applicationInfo.dataDir)
        Yuhaiin.setInterfaces(GetInterfaces())
        Yuhaiin.setProcessDumper(uidDumper)
        store = Yuhaiin.getStore()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    class InterfaceIterImpl(
        private val data: ArrayList<Interface> // 或 MutableList<Interface>
    ) : InterfaceIter {
        private var index = 0

        override fun next(): Interface? {
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

    class AddressIterImpl(
        private val data: ArrayList<AddressPrefix> // 或 MutableList<Interface>
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
        override fun getInterfaces(): InterfaceIter? {
            val interfaces: ArrayList<NetworkInterface> =
                java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            val sb = ArrayList<Interface>(0)
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
                } catch (e: Exception) {
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

fun Store.getStringMap(key: String?): Map<String, String> {
    val data = getString(key)
    if (data.isEmpty()) return HashMap()
    return Json.decodeFromString<Map<String, String>>(data)
}

fun Store.putStringMap(key: String?, values: Map<String, String>) {
    putString(key, Json.encodeToString(values))
}

fun Store.getStringArrayList(key: String): ArrayList<String> {
    val data = getString(key)
    if (data.isEmpty()) return ArrayList()
    return Json.decodeFromString<ArrayList<String>>(data)
}

fun Store.putStringArrayList(key: String, values: ArrayList<String>) {
    putString(key, Json.encodeToString(values))
}

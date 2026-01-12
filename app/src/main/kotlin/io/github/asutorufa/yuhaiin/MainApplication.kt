package io.github.asutorufa.yuhaiin

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
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
        override fun dumpUid(
            p0: Int,
            srcIp: String?,
            srcPort: Int,
            destIp: String?,
            destPort: Int
        ): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connectivity.getConnectionOwnerUid(
                    p0,
                    InetSocketAddress(srcIp, srcPort),
                    InetSocketAddress(destIp, destPort)
                )
            } else {
                0
            }

        override fun getUidInfo(p0: Int): String = packageManager.getNameForUid(p0) ?: "unknown"
    }

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Yuhaiin.setSavePath(getExternalFilesDir("yuhaiin").toString())
        Yuhaiin.setInterfaces(GetInterfaces())
        Yuhaiin.setProcessDumper(UidDumper())
        store = Yuhaiin.getStore()
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

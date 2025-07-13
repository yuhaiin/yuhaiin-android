package io.github.asutorufa.yuhaiin

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import go.Seq
import kotlinx.serialization.json.Json
import yuhaiin.Interfaces
import yuhaiin.Store
import yuhaiin.Yuhaiin
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Locale

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

    inner class GetInterfaces : Interfaces {
        override fun getInterfacesAsString(): String? {
            val interfaces: ArrayList<NetworkInterface> =
                java.util.Collections.list(NetworkInterface.getNetworkInterfaces())

            val sb = StringBuilder()
            for (nif in interfaces) {
                try {
                    sb.append(
                        String.format(
                            Locale.ROOT,
                            "%s %d %d %b %b %b %b %b |",
                            nif.name,
                            nif.index,
                            nif.mtu,
                            nif.isUp,
                            nif.supportsMulticast(),
                            nif.isLoopback,
                            nif.isPointToPoint,
                            nif.supportsMulticast()
                        )
                    )

                    for (ia in nif.interfaceAddresses) {
                        val parts = ia.toString().split("/", limit = 0)
                        if (parts.size > 1) {
                            sb.append(
                                String.format(
                                    Locale.ROOT,
                                    "%s/%d ",
                                    parts[1],
                                    ia.networkPrefixLength
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
                sb.append("\n")
            }

            return sb.toString()
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

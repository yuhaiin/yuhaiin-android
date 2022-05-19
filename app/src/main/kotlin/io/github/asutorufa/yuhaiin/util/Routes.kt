package io.github.asutorufa.yuhaiin.util

import android.content.Context
import android.net.VpnService
import android.util.Log
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.util.Constants.ROUTE_CHN
import io.github.asutorufa.yuhaiin.util.Constants.ROUTE_NO_LOCAL

object Routes {
    fun addRoutes(context: Context, builder: VpnService.Builder, name: String) {
        val routes: Array<String> = if (ROUTE_CHN == name) {
            context.resources.getStringArray(R.array.simple_route)
        } else if (ROUTE_NO_LOCAL == name) {
            context.resources.getStringArray(R.array.all_routes_except_local)
        } else {
            arrayOf("0.0.0.0/0")
        }
        for (r in routes) {
            addRoute(builder, r)
        }
    }

    fun addRoute(builder: VpnService.Builder, cidr: String) {
        val cidrs = cidr.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        // Cannot handle 127.0.0.0/8
        if (cidrs.size == 2 && !cidrs[0].startsWith("127")) {
            try {
                val mask = cidrs[1].toInt()
                builder.addRoute(getNetworkId(cidrs[0], mask), mask)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("yuhaiin", "addRoute: " + cidr + " " + cidrs.contentToString())
            }
        }
    }

    private fun getNetworkId(ip: String, maskStr: Int): String {
        val mask = getMask(maskStr)
        val ips = ip.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val masks = mask.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in 0..3) {
            sb.append(ips[i].toInt() and masks[i].toInt())
            if (i != 3) {
                sb.append(".")
            }
        }
        return sb.toString()
    }

    private fun getMask(maskLength: Int): String {
        val binaryMask = -0x1 shl 32 - maskLength
        val sb = StringBuilder()
        var shift = 24
        while (shift > 0) {
            sb.append(binaryMask ushr shift and 0xFF)
            sb.append(".")
            shift -= 8
        }
        sb.append(binaryMask and 0xFF)
        return sb.toString()
    }
}
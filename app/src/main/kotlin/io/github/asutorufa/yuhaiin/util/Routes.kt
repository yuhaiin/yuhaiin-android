package io.github.asutorufa.yuhaiin.util

import android.content.Context
import android.net.VpnService
import android.util.Log
import io.github.asutorufa.yuhaiin.R
import yuhaiin.Yuhaiin.parseCIDR

object Routes {
    fun addRoutes(context: Context, builder: VpnService.Builder, name: String) {
        val routes: Array<String> = when (name) {
            context.resources.getString(R.string.adv_route_non_chn) -> {
                context.resources.getStringArray(R.array.simple_route)
            }
            context.resources.getString(R.string.adv_route_non_local) -> {
                context.resources.getStringArray(R.array.all_routes_except_local)
            }
            else -> {
                arrayOf("0.0.0.0/0")
            }
        }
        
        for (r in routes) addRoute(builder, r)
    }

    fun addRoute(builder: VpnService.Builder, cidr: String) {
        try {
            parseCIDR(cidr).apply {
                // Cannot handle 127.0.0.0/8
                if (!ip.startsWith("127")) builder.addRoute(ip, mask)
            }
        } catch (e: Exception) {
            Log.e("addRoute", "addRoute " + cidr + " failed: " + e.message)
        }
    }
}
package io.github.asutorufa.yuhaiin.service

import android.content.Context
import android.net.IpPrefix
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.getStringSet
import yuhaiin.TunAddress
import yuhaiin.Yuhaiin

/** Exact complement of the existing IPv4 preset; do not silently redefine local networks. */
internal val nonLocalExclusions =
    listOf(
        "10.0.0.0/8",
        "100.64.0.0/10",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "224.0.0.0/3",
    )

fun VpnService.Builder.configure(
    context: Context,
    address: TunAddress,
    mtu: Int,
): VpnService.Builder = apply {
    val store = MainApplication.store
    setMtu(mtu)
    setSession(context.getString(R.string.yuhaiin))
    if (store.getBoolean(Constants.PER_APP_KEY)) {
        val bypass = store.getBoolean(Constants.APP_BYPASS_KEY)
        val applications =
            store.getStringSet(Constants.APP_LIST_KEY).toMutableSet().apply {
                if (bypass) remove(BuildConfig.APPLICATION_ID) else add(BuildConfig.APPLICATION_ID)
            }
        applications.forEach { name ->
            try {
                if (bypass) addDisallowedApplication(name) else addAllowedApplication(name)
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                Log.w("VPN", "App no longer installed: $name", e)
            }
        }
    }
    addAddress(address.iPv4Address, 24).addRoute(address.iPv4, 24)
    addAddress(address.iPv6Address, 64).addRoute("2000::", 3).addRoute(address.iPv6, 64)
    val name = store.getString(Constants.ROUTE_KEY).ifBlank { Constants.ALL_ROUTE }
    val content = store.getString(Constants.ROUTE_CONTENT_PREFIX + name)
    val lines = content.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    val preset = context.resources.getStringArray(R.array.all_routes_except_local).toList()
    val useExclusions =
        Build.VERSION.SDK_INT >= 33 && name == Constants.NON_LOCAL_ROUTE && lines == preset
    if (useExclusions) {
        addRoute("0.0.0.0", 0)
        nonLocalExclusions.forEach {
            excludeRoute(
                IpPrefix(
                    java.net.InetAddress.getByName(it.substringBefore('/')),
                    it.substringAfter('/').toInt(),
                )
            )
        }
        lines.filter { ':' in it }.forEach { addCidr(it) }
    } else if (lines.isEmpty()) {
        addRoute("0.0.0.0", 0)
        if (Yuhaiin.isIPv6()) addRoute("::", 0)
    } else lines.forEach { addCidr(it) }
    addDnsServer(address.iPv4Portal)
    addDnsServer(address.iPv6Portal)
    Yuhaiin.addFakeDnsCidr { addRoute(it.ip, it.mask) }
    if (Build.VERSION.SDK_INT >= 29) {
        // false inherits underlying network meteredness; Android cannot force unmetered.
        setMetered(store.getString(Constants.METERED_MODE_KEY) == "metered")
        val port = store.getInt(Constants.HTTP_PORT_KEY)
        if (port in 1..65535 && store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
            setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", port))
    }
}

private fun VpnService.Builder.addCidr(cidr: String) {
    val prefix = Yuhaiin.parseCIDR(cidr)
    // Android refuses loopback routes. Other invalid rules fail startup visibly instead of being
    // ignored.
    if (!prefix.ip.startsWith("127.")) addRoute(prefix.ip, prefix.mask)
}

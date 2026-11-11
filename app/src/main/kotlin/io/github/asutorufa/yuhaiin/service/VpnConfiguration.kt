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
import io.github.asutorufa.yuhaiin.core.NativeCore

/** Exact complement of the existing IPv4 preset; do not silently redefine local networks. */
internal val nonLocalExclusions =
    listOf(
        "10.0.0.0/8",
        "100.64.0.0/10",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "224.0.0.0/3",
    )

/**
 * Builds the VPN interface requested by the core. Values owned by the core (addresses, DNS,
 * MTU, explicit routes, FakeIP pools) come from [config]; Android-only preferences are read here.
 */
fun VpnService.Builder.configure(
    context: Context,
    config: PlatformVpnConfig,
): VpnService.Builder = apply {
    val store = MainApplication.store
    setMtu(config.mtu)
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
    if (config.ipv4.isNotBlank()) addAddress(config.ipv4, config.ipv4Prefix)
    if (config.ipv6.isNotBlank()) addAddress(config.ipv6, config.ipv6Prefix)
    val name = store.getString(Constants.ROUTE_KEY).ifBlank { Constants.ALL_ROUTE }
    val plan =
        planRoutes(
            config,
            name,
            store.getString(Constants.ROUTE_CONTENT_PREFIX + name).lines(),
            context.resources.getStringArray(R.array.all_routes_except_local).toList(),
            Constants.NON_LOCAL_ROUTE,
            Build.VERSION.SDK_INT,
            NativeCore.isIPv6(),
        )
    // Invalid rules fail establishment visibly instead of being ignored: the core rejects the
    // whole list. One call parses every route.
    NativeCore.parseCidrs(plan.routes).forEach { addRoute(it.ip, it.mask) }
    if (Build.VERSION.SDK_INT >= 33)
        NativeCore.parseCidrs(plan.excludes).forEach {
            excludeRoute(IpPrefix(java.net.InetAddress.getByName(it.ip), it.mask))
        }
    config.dns.forEach { addDnsServer(it) }
    if (Build.VERSION.SDK_INT >= 29) {
        // false inherits underlying network meteredness; Android cannot force unmetered.
        setMetered(store.getString(Constants.METERED_MODE_KEY) == "metered")
        val port = store.getInt(Constants.HTTP_PORT_KEY)
        if (port in 1..65535 && store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
            setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", port))
    }
}

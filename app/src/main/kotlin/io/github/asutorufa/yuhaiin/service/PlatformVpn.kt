package io.github.asutorufa.yuhaiin.service

import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val platformJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * VPN configuration requested by the Go core (platformvpn.Config). The core owns these values;
 * Android preferences (per-app rules, the route profile, metered mode, HTTP proxy) are applied on
 * top of it when the interface is built.
 */
@Serializable
data class PlatformVpnConfig(
    val mtu: Int = 1500,
    val ipv4: String = "",
    val ipv4Prefix: Int = 0,
    val ipv6: String = "",
    val ipv6Prefix: Int = 0,
    val dns: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val fakeIpRoutes: List<String> = emptyList(),
    /**
     * "system_network" when System Network rules target this VPN. [routes] then is the complete
     * route set the core's System Network Manager resolved (the same policy engine desktop uses),
     * and the Android route profile is not applied.
     */
    val routeMode: String = "",
    val platformRevision: Long = 0,
) {
    companion object {
        fun parse(raw: String): PlatformVpnConfig = platformJson.decodeFromString(serializer(), raw)
    }
}

/** Runtime state of the platform VPN reported by the core (platformvpn.Status). */
@Serializable
data class PlatformVpnStatus(
    val state: String = "stopped",
    val inboundId: String = "",
    val generation: Long = 0,
    val applied: PlatformVpnConfig? = null,
    val pending: PlatformVpnConfig? = null,
    val lastError: String = "",
    val errorCode: String = "",
    val reconfigurations: Long = 0,
    val reconfigureFailures: Long = 0,
    val lastSwitchMillis: Long = 0,
    val startedAt: Long = 0,
    val changedAt: Long = 0,
) {
    /** Maps the core state onto the State used by notifications, the tile and the widget. */
    fun vpnState(): State =
        when (state) {
            "running",
            "reconfiguring" -> State.CONNECTED
            "establishing" -> State.CONNECTING
            "stopping" -> State.DISCONNECTING
            "failed" -> State.ERROR
            else -> State.DISCONNECTED
        }

    companion object {
        fun parse(raw: String): PlatformVpnStatus =
            runCatching { platformJson.decodeFromString(serializer(), raw) }
                .getOrDefault(PlatformVpnStatus(state = "failed", lastError = "invalid status"))
    }
}

@Serializable
data class VpnAuthorization(
    val prepared: Boolean,
    val consent: Boolean,
    val alwaysOn: Boolean = false,
    val lockdown: Boolean = false,
) {
    fun encode(): String = platformJson.encodeToString(serializer(), this)
}

/** IPv4 and IPv6 routes the VpnService.Builder receives, before per-app and DNS settings. */
data class RoutePlan(
    val routes: List<String>,
    val excludes: List<String>,
    /** True when the routes below cover the default route via exclusions (API 33+). */
    val defaultWithExclusions: Boolean = false,
)

/**
 * Computes VPN routes. A core routing mode installs exactly the generated routes; otherwise explicit
 * core routes win over the Android route profile. The TUN subnets,
 * the IPv6 global unicast block (kept from v1) and the core's active FakeIP pools are always added
 * so FakeIP answers stay routable.
 */
fun planRoutes(
    config: PlatformVpnConfig,
    profileName: String,
    profileLines: List<String>,
    nonLocalPreset: List<String>,
    nonLocalProfile: String,
    sdk: Int,
    ipv6Enabled: Boolean,
): RoutePlan {
    val routes = mutableListOf<String>()
    val excludes = mutableListOf<String>()
    fun subnet(address: String, prefix: Int) {
        if (address.isNotBlank() && prefix > 0) routes += networkOf(address, prefix)
    }
    subnet(config.ipv4, config.ipv4Prefix)
    if (config.routeMode.isNotBlank()) {
        // The core already resolved bypass networks into routes, so no exclusions are needed and
        // every API level gets the same result.
        subnet(config.ipv6, config.ipv6Prefix)
        routes += config.routes
        routes += config.fakeIpRoutes
        return RoutePlan(routes.filterNot { it.startsWith("127.") }.distinct(), emptyList())
    }
    if (config.ipv6.isNotBlank()) {
        routes += "2000::/3"
        subnet(config.ipv6, config.ipv6Prefix)
    }
    var withExclusions = false
    if (config.routes.isNotEmpty()) {
        routes += config.routes
        if (sdk >= 33) excludes += config.excludes
    } else {
        val lines = profileLines.map(String::trim).filter(String::isNotBlank)
        if (sdk >= 33 && profileName == nonLocalProfile && lines == nonLocalPreset) {
            withExclusions = true
            routes += "0.0.0.0/0"
            excludes += nonLocalExclusions
            routes += lines.filter { ':' in it }
        } else if (lines.isEmpty()) {
            routes += "0.0.0.0/0"
            if (ipv6Enabled) routes += "::/0"
        } else routes += lines
    }
    routes += config.fakeIpRoutes
    // Android refuses loopback routes.
    return RoutePlan(
        routes.filterNot { it.startsWith("127.") }.distinct(),
        excludes.distinct(),
        withExclusions,
    )
}

/** Masks [address] to its [prefix] network, e.g. 172.19.0.1/24 -> 172.19.0.0/24. */
fun networkOf(address: String, prefix: Int): String {
    val bytes = java.net.InetAddress.getByName(address).address
    var remaining = prefix
    for (i in bytes.indices) {
        val keep = remaining.coerceIn(0, 8)
        val mask = if (keep == 0) 0 else (0xff shl (8 - keep)) and 0xff
        bytes[i] = (bytes[i].toInt() and mask).toByte()
        remaining -= keep
    }
    val host = java.net.InetAddress.getByAddress(bytes).hostAddress!!.substringBefore('%')
    return "$host/$prefix"
}

package io.github.asutorufa.yuhaiin.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A route in CIDR form, as parsed by the Go core. */
data class CidrPrefix(val ip: String, val mask: Int)

/** One non-VPN Network observed by ConnectivityManager. */
data class UnderlyingNetwork(
    val handle: Long,
    val interfaceIndex: Int,
    val interfaceName: String,
    /** Whitespace-separated local IP addresses, without masks. */
    val addresses: String,
    val available: Boolean,
    val validated: Boolean,
    val vpn: Boolean,
)

/**
 * Typed access to the Go core's `yh_call` operations (see docs/android-c-abi.md in the core
 * repository). Every method is a thin request/response mapping; the core owns the behaviour.
 *
 * Blocking: these calls may take as long as the core needs (start, stop, health). Never call
 * them on the main thread, except the cheap [session] / [running] reads.
 */
object NativeCore {
    private fun call(op: String, build: (JsonObjectBuilder.() -> Unit)? = null): ByteArray =
        NativeBridge.nativeCall(op, build?.let { buildJsonObject(it).toString().toByteArray() })

    private fun callJson(op: String, build: (JsonObjectBuilder.() -> Unit)? = null) =
        Json.parseToJsonElement(call(op, build).decodeToString()).jsonObject

    /** Raw operation for the store transport and other pass-through callers. */
    fun callRaw(op: String, request: ByteArray?): ByteArray = NativeBridge.nativeCall(op, request)

    // ---- lifecycle ----

    fun init(savePath: String, configLockPath: String, managementTlsDir: String) {
        call("core.init") {
            put("savePath", savePath)
            put("configLockPath", configLockPath)
            put("managementTLSDir", managementTlsDir)
        }
    }

    fun start() {
        call("core.start")
    }

    fun stop() {
        call("core.stop")
    }

    class Session(val running: Boolean, val port: Int, val token: String)

    fun session(): Session {
        val o = callJson("core.session")
        return Session(
            o["running"]!!.jsonPrimitive.boolean,
            o["port"]!!.jsonPrimitive.int,
            o["token"]!!.jsonPrimitive.content,
        )
    }

    fun running(): Boolean = session().running

    fun httpPort(): Int = session().let { if (it.running) it.port else 0 }

    fun platformToken(): String = session().let { if (it.running) it.token else "" }

    fun nativeStatus(): String = call("core.native_status").decodeToString()

    fun checkHealth(): String = call("core.health").decodeToString()

    fun setMonitoringNetworkState(online: Boolean, metered: Boolean, foreground: Boolean) {
        call("core.monitoring") {
            put("online", online)
            put("metered", metered)
            put("foreground", foreground)
        }
    }

    // ---- VPN ----

    fun vpnEnabled(): Boolean = callJson("core.vpn_enabled")["enabled"]!!.jsonPrimitive.boolean

    fun setVpnEnabled(enabled: Boolean) {
        call("core.set_vpn_enabled") { put("enabled", enabled) }
    }

    fun vpnStatus(): String = call("vpn.status").decodeToString()

    /** True when [generation] was the live one and the core stopped it. */
    fun notifyVpnRevoked(generation: Long): Boolean =
        callJson("vpn.revoked") { put("generation", generation) }["applied"]!!.jsonPrimitive.boolean

    fun requestVpnReconfigure() {
        call("vpn.reconfigure")
    }

    // ---- network ----

    fun reportUnderlyingNetworks(networks: List<UnderlyingNetwork>, selectedHandle: Long) {
        call("network.report") {
            put("selected", selectedHandle)
            put(
                "networks",
                JsonArray(
                    networks.map {
                        buildJsonObject {
                            put("handle", it.handle)
                            put("interfaceIndex", it.interfaceIndex)
                            put("interfaceName", it.interfaceName)
                            put("addresses", it.addresses)
                            put("available", it.available)
                            put("validated", it.validated)
                            put("vpn", it.vpn)
                        }
                    }
                ),
            )
        }
    }

    fun isIPv6(): Boolean = callJson("net.is_ipv6")["ipv6"]!!.jsonPrimitive.boolean

    /** Parses CIDRs as the core does; an invalid entry fails the whole call. */
    fun parseCidrs(cidrs: List<String>): List<CidrPrefix> {
        if (cidrs.isEmpty()) return emptyList()
        val o =
            callJson("net.parse_cidr") {
                put("cidrs", JsonArray(cidrs.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
        return o["prefixes"]!!.jsonArray.map {
            val p = it.jsonObject
            CidrPrefix(p["ip"]!!.jsonPrimitive.content, p["mask"]!!.jsonPrimitive.int)
        }
    }

    // ---- configuration and proxy ----

    fun exportConfig(): ByteArray = NativeBridge.nativeCall("config.export", null)

    fun validateConfig(data: ByteArray) {
        NativeBridge.nativeCall("config.validate", data)
    }

    fun importConfig(data: ByteArray) {
        NativeBridge.nativeCall("config.import", data)
    }

    fun proxyGet(url: String): ByteArray = call("proxy.get") { put("url", url) }

    fun proxyDownload(url: String, destination: String) {
        call("proxy.download") {
            put("url", url)
            put("destination", destination)
        }
    }
}

package io.github.asutorufa.yuhaiin

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * On-device integration, executed by the opt-in device-regression workflow.
 * This opens an actual VpnService/TUN and checks the native inbound runtime,
 * not just the UI preference keys.
 */
class TunStackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun writeSelection(stack: String, cc: String) =
        runBlocking(Dispatchers.IO) {
            MainApplication.settings.commit {
                it.putString(Constants.TUN_STACK_KEY, stack)
                it.putString(Constants.TUN_CONGESTION_CONTROL_KEY, cc)
                it.putString(Constants.TUN_DRIVER_KEY, "system_gvisor")
            }
        }

    private fun startVpn(device: UiDevice) {
        compose.runOnUiThread { compose.activity.startService() }
        val deadline = SystemClock.uptimeMillis() + 25_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (compose.activity.state.value == State.CONNECTED) return
            if (compose.activity.state.value == State.ERROR)
                error("VPN startup failed: ${compose.activity.error.value}")
            runCatching { device.findObject(By.res("android", "button1"))?.click() }
            SystemClock.sleep(100)
        }
        error("VPN did not connect: ${compose.activity.state.value} / ${compose.activity.error.value}")
    }

    private fun stopVpn() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.vpnBinder?.stop()
        }
        compose.waitUntil(20_000) { compose.activity.state.value == State.DISCONNECTED }
    }

    private fun rpc(route: String): JSONObject {
        val port = MainApplication.store.getInt(Constants.WEB_PORT_KEY)
        check(port in 1..65535) { "Native API port not ready: $port" }
        val conn = URL("http://127.0.0.1:$port/api/v2/rpc/$route")
            .openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray()) }
            check(conn.responseCode == 200) { "$route returned HTTP ${conn.responseCode}" }
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun tunConfig(): Pair<String, JSONObject> {
        val items = rpc("inbounds.get").getJSONArray("items")
        for (i in 0 until items.length()) {
            val inbound = items.getJSONObject(i)
            val config = inbound.getJSONObject("protocol")
            if (config.getString("type") == "tun")
                return inbound.getString("id") to config.getJSONObject("tun")
        }
        error("No native TUN inbound")
    }

    private fun ingress(inboundID: String): Pair<Long, Long> {
        val items = rpc("inbounds.status").getJSONArray("items")
        for (i in 0 until items.length()) {
            val status = items.getJSONObject(i)
            if (status.getString("id") != inboundID) continue
            assertEquals("running", status.getString("status"))
            val stats = status.getJSONObject("statistics")
            return stats.getString("acceptedStreams").toLong() to
                stats.getString("receivedPackets").toLong()
        }
        error("No active TUN runtime status")
    }

    private fun generateIngress() {
        // Reserved TEST-NET/IPv6 documentation addresses avoid dependence on
        // external server availability. Verify ingress, not remote delivery.
        DatagramSocket().use { udp ->
            val payload = byteArrayOf(0x00, 0x42, 0x00, 0x00)
            udp.send(
                DatagramPacket(
                    payload, payload.size, InetAddress.getByName("198.51.100.21"), 9999
                )
            )
            runCatching {
                udp.send(
                    DatagramPacket(
                        payload, payload.size, InetAddress.getByName("2001:db8::21"), 53
                    )
                )
            }
        }
        Socket().use { tcp ->
            runCatching { tcp.connect(InetSocketAddress("198.51.100.21", 443), 1200) }
        }
    }

    @Test
    fun legacyAndLightweightControllersStartAndAcceptTCPUDP() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (Build.VERSION.SDK_INT >= 33)
            device.executeShellCommand(
                "pm grant ${BuildConfig.APPLICATION_ID} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
        val store = MainApplication.store
        val previousStack = store.getString(Constants.TUN_STACK_KEY)
        val previousCC = store.getString(Constants.TUN_CONGESTION_CONTROL_KEY)
        val previousDriver = store.getString(Constants.TUN_DRIVER_KEY)
        try {
            for ((stack, cc) in listOf(
                "" to "cubic",
                "lightweight" to "reno",
                "lightweight" to "cubic",
                "lightweight" to "bbr",
                "" to "bbr",
            )) {
                writeSelection(stack, cc)
                startVpn(device)
                val (id, cfg) = tunConfig()
                assertEquals(stack, cfg.optString("stack"))
                assertEquals(if (stack.isEmpty()) "" else cc, cfg.optString("congestionControl"))
                assertEquals(
                    if (stack.isEmpty()) "system_gvisor" else "channel",
                    cfg.getString("driver"),
                )
                val before = ingress(id)
                generateIngress()
                compose.waitUntil(5000) {
                    val after = ingress(id)
                    after.second > before.second && after.first > before.first
                }
                stopVpn()
            }
        } finally {
            runBlocking(Dispatchers.IO) {
                MainApplication.settings.commit {
                    it.putString(Constants.TUN_STACK_KEY, previousStack)
                    it.putString(Constants.TUN_CONGESTION_CONTROL_KEY, previousCC)
                    it.putString(Constants.TUN_DRIVER_KEY, previousDriver)
                }
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.vpnBinder?.stop()
            }
        }
    }
}

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

    private data class TunSelection(
        val stack: String,
        val congestionControl: String,
        val scheduler: String,
        val lossRecovery: String,
    )

    /** v2: TUN tuning belongs to the core's TUN inbound, edited through the core API
     * (as the Web UI does); a running VPN is rebuilt in place. */
    private fun writeSelection(stack: String, cc: String, scheduler: String, lossRecovery: String) {
        val inbound = tunInbound()
        val tun = inbound.getJSONObject("protocol").getJSONObject("tun")
        tun.put("stack", stack)
        tun.put("congestionControl", cc)
        tun.put("outputScheduler", scheduler)
        tun.put("lossRecovery", lossRecovery)
        rpc("inbound.put", inbound)
    }

    private fun startVpn(device: UiDevice) {
        compose.runOnUiThread {
            grantVpnConsentForLifecycleTest(compose.activity)
            compose.activity.startService()
        }
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

    private fun rpc(route: String, body: JSONObject = JSONObject()): JSONObject {
        val port = MainApplication.store.getInt(Constants.WEB_PORT_KEY)
        check(port in 1..65535) { "Native API port not ready: $port" }
        val conn = URL("http://127.0.0.1:$port/api/v2/rpc/$route")
            .openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            check(conn.responseCode == 200) {
                "$route returned HTTP ${conn.responseCode}: " +
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun tunInbound(): JSONObject {
        val items = rpc("inbounds.get").getJSONArray("items")
        for (i in 0 until items.length()) {
            val inbound = items.getJSONObject(i)
            if (inbound.getJSONObject("protocol").getString("type") == "tun") return inbound
        }
        error("No native TUN inbound")
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
    fun lightweightRecoveryModesStartAndAcceptTCPUDP() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (Build.VERSION.SDK_INT >= 33)
            device.executeShellCommand(
                "pm grant ${BuildConfig.APPLICATION_ID} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
        val store = MainApplication.store
        compose.waitUntil(20_000) { compose.activity.session.value.ready }
        val original = tunInbound()
        val previousStack = store.getString(Constants.TUN_STACK_KEY)
        val previousCC = store.getString(Constants.TUN_CONGESTION_CONTROL_KEY)
        val previousScheduler = store.getString(Constants.TUN_OUTPUT_SCHEDULER_KEY)
        val previousRecovery = store.getString(Constants.TUN_LOSS_RECOVERY_KEY)
        val previousDriver = store.getString(Constants.TUN_DRIVER_KEY)
        try {
            for (selection in listOf(
                TunSelection("", "cubic", "fq_codel", "classic"),
                TunSelection("lightweight", "reno", "fifo", "rack-tlp"),
                TunSelection("lightweight", "cubic", "fq_codel", "rack-tlp"),
                TunSelection("lightweight", "bbr", "fifo", "classic"),
                TunSelection("", "bbr", "fq_codel", "rack-tlp"),
            )) {
                writeSelection(
                    selection.stack,
                    selection.congestionControl,
                    selection.scheduler,
                    selection.lossRecovery,
                )
                startVpn(device)
                val (id, cfg) = tunConfig()
                // v2 stores the selection as edited; an empty stack means lightweight.
                assertEquals("lightweight", cfg.optString("stack").ifEmpty { "lightweight" })
                assertEquals(selection.congestionControl, cfg.getString("congestionControl"))
                assertEquals(selection.scheduler, cfg.getString("outputScheduler"))
                assertEquals(selection.lossRecovery, cfg.getString("lossRecovery"))
                assertEquals("channel", cfg.getString("driver"))
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
                    it.putString(Constants.TUN_OUTPUT_SCHEDULER_KEY, previousScheduler)
                    it.putString(Constants.TUN_LOSS_RECOVERY_KEY, previousRecovery)
                    it.putString(Constants.TUN_DRIVER_KEY, previousDriver)
                }
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.vpnBinder?.stop()
            }
            runCatching { rpc("inbound.put", original) }
        }
    }
}

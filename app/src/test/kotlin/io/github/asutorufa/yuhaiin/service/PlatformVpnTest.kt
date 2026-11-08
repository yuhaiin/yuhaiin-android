package io.github.asutorufa.yuhaiin.service

import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformVpnTest {
    private val core =
        PlatformVpnConfig(
            mtu = 9000,
            ipv4 = "172.19.0.1",
            ipv4Prefix = 24,
            ipv6 = "fdfe:dcba:9876::1",
            ipv6Prefix = 64,
            dns = listOf("172.19.0.2", "fdfe:dcba:9876::2"),
            fakeIpRoutes = listOf("198.18.0.0/16", "2001:2::/64"),
        )
    private val preset = listOf("1.0.0.0/8", "2.0.0.0/7")

    private fun plan(
        config: PlatformVpnConfig = core,
        profile: String = "All (Default)",
        lines: List<String> = emptyList(),
        sdk: Int = 34,
    ) = planRoutes(config, profile, lines, preset, "Non-Local IPs", sdk, ipv6Enabled = true)

    @Test
    fun parsesCoreConfigurationAndIgnoresUnknownFields() {
        val parsed =
            PlatformVpnConfig.parse(
                """{"mtu":1500,"ipv4":"10.1.0.1","ipv4Prefix":24,"dns":["10.1.0.2"],"routes":[],"excludes":[],"fakeIpRoutes":["198.18.0.0/16"],"platformRevision":3,"future":true}"""
            )
        assertEquals(1500, parsed.mtu)
        assertEquals(listOf("198.18.0.0/16"), parsed.fakeIpRoutes)
        assertEquals(3L, parsed.platformRevision)
    }

    @Test
    fun fakeIpPoolsFromTheCoreAreAlwaysRouted() {
        val routes = plan().routes
        assertTrue("198.18.0.0/16" in routes)
        assertTrue("2001:2::/64" in routes)
        assertTrue("172.19.0.0/24" in routes)
        assertTrue(networkOf("fdfe:dcba:9876::1", 64) in routes)
        assertTrue("0.0.0.0/0" in routes && "::/0" in routes)
    }

    @Test
    fun changedFakeIpPoolChangesTheRoutes() {
        val changed = plan(core.copy(fakeIpRoutes = listOf("100.64.0.0/16"))).routes
        assertTrue("100.64.0.0/16" in changed)
        assertFalse("198.18.0.0/16" in changed)
    }

    @Test
    fun explicitCoreRoutesWinOverTheAndroidProfile() {
        val result =
            plan(
                core.copy(routes = listOf("8.8.8.0/24"), excludes = listOf("8.8.8.8/32")),
                lines = listOf("1.1.1.0/24"),
            )
        assertTrue("8.8.8.0/24" in result.routes)
        assertFalse("1.1.1.0/24" in result.routes)
        assertFalse("0.0.0.0/0" in result.routes)
        assertEquals(listOf("8.8.8.8/32"), result.excludes)
        assertEquals(emptyList<String>(), plan(core.copy(routes = listOf("8.8.8.0/24"), excludes = listOf("8.8.8.8/32")), sdk = 30).excludes)
    }

    @Test
    fun nonLocalPresetUsesExclusionsOnApi33() {
        val result = plan(profile = "Non-Local IPs", lines = preset)
        assertTrue(result.defaultWithExclusions)
        assertTrue("0.0.0.0/0" in result.routes)
        assertEquals(nonLocalExclusions, result.excludes)
        val legacy = plan(profile = "Non-Local IPs", lines = preset, sdk = 30)
        assertFalse(legacy.defaultWithExclusions)
        assertTrue(legacy.routes.containsAll(preset))
    }

    @Test
    fun loopbackRoutesAreDropped() {
        assertFalse(plan(lines = listOf("127.0.0.0/8", "10.0.0.0/8")).routes.any { it.startsWith("127.") })
    }

    @Test
    fun networkOfMasksHostBits() {
        assertEquals("172.19.0.0/24", networkOf("172.19.0.1", 24))
        assertEquals("10.0.0.0/9", networkOf("10.127.1.1", 9))
        assertEquals("fdfe:dcba:9876:0:0:0:0:0/64", networkOf("fdfe:dcba:9876::1", 64))
    }

    @Test
    fun coreStatusMapsToConnectionState() {
        assertEquals(State.CONNECTED, PlatformVpnStatus(state = "running").vpnState())
        // The old interface keeps forwarding while a new one is built.
        assertEquals(State.CONNECTED, PlatformVpnStatus(state = "reconfiguring").vpnState())
        assertEquals(State.CONNECTING, PlatformVpnStatus(state = "establishing").vpnState())
        assertEquals(State.DISCONNECTING, PlatformVpnStatus(state = "stopping").vpnState())
        assertEquals(State.ERROR, PlatformVpnStatus(state = "failed").vpnState())
        assertEquals(State.DISCONNECTED, PlatformVpnStatus(state = "revoked").vpnState())
        assertEquals(State.DISCONNECTED, PlatformVpnStatus(state = "stopped").vpnState())
    }

    @Test
    fun invalidStatusIsAnErrorNotACrash() {
        assertEquals("failed", PlatformVpnStatus.parse("{").state)
        val parsed =
            PlatformVpnStatus.parse(
                """{"state":"running","generation":7,"applied":{"mtu":9000,"dns":[],"routes":[],"excludes":[],"fakeIpRoutes":[]},"reconfigurations":2,"changedAt":1}"""
            )
        assertEquals(7L, parsed.generation)
        assertEquals(9000, parsed.applied?.mtu)
    }

    @Test
    fun authorizationEncodesForTheCore() {
        assertEquals(
            """{"prepared":true,"consent":false,"alwaysOn":false,"lockdown":false}""",
            VpnAuthorization(prepared = true, consent = false).encode(),
        )
    }

    @Test
    fun coreBackgroundPolicy() {
        assertTrue(YuhaiinCoreService.coreNeeded("always", false, false, false))
        assertTrue(YuhaiinCoreService.coreNeeded("while_needed", true, false, false))
        assertTrue(YuhaiinCoreService.coreNeeded("while_needed", false, true, false))
        assertTrue(YuhaiinCoreService.coreNeeded("while_needed", false, false, true))
        assertFalse(YuhaiinCoreService.coreNeeded("while_needed", false, false, false))
    }
}

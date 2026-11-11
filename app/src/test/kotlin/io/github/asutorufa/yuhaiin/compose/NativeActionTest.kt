package io.github.asutorufa.yuhaiin.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeActionTest {
    private val page = "http://127.0.0.1:8080/#/docs/android"

    private fun parse(
        target: String,
        pageUrl: String? = page,
        mainFrame: Boolean = true,
        gesture: Boolean = true,
    ) = NativeAction.parse(target, pageUrl, 8080, mainFrame, gesture)

    @Test
    fun acceptsAllowlistedActionsFromTheLocalPage() {
        assertEquals(NativeAction.VPN_AUTHORIZE, parse("yuhaiin-android://action/vpn/authorize"))
        assertEquals(
            NativeAction.APPS_CONSENT,
            parse("yuhaiin-android://action/consent/apps", "https://127.0.0.1:8080/"),
        )
        assertEquals(NativeAction.LOCAL_NETWORK, parse("yuhaiin-android://action/network/local"))
    }

    @Test
    fun localNetworkActionRequiresAGestureOnTheLocalPage() {
        val target = "yuhaiin-android://action/network/local"
        assertNull(parse(target, gesture = false))
        assertNull(parse(target, pageUrl = "http://192.168.20.13:8080/"))
        assertNull(parse(target, mainFrame = false))
    }

    @Test
    fun rejectsUnknownActionsAndHosts() {
        assertNull(parse("yuhaiin-android://action/exec"))
        // Native screens moved to the Web UI; their old links do nothing.
        assertNull(parse("yuhaiin-android://action/backup"))
        assertNull(parse("yuhaiin-android://action/settings"))
        assertNull(parse("yuhaiin-android://evil/vpn/authorize"))
        assertNull(parse("intent://action/vpn/authorize"))
    }

    @Test
    fun rejectsRemotePagesStalePortsAndScriptedNavigation() {
        assertNull(parse("yuhaiin-android://action/vpn/authorize", "https://example.com/"))
        assertNull(parse("yuhaiin-android://action/vpn/authorize", "http://127.0.0.1:9999/"))
        assertNull(parse("yuhaiin-android://action/vpn/authorize", "http://192.168.1.2:8080/"))
        assertNull(parse("yuhaiin-android://action/vpn/authorize", null))
        assertNull(parse("yuhaiin-android://action/vpn/authorize", gesture = false))
        assertNull(parse("yuhaiin-android://action/vpn/authorize", mainFrame = false))
    }

    @Test
    fun localPage() {
        assertTrue(NativeAction.isLocalPage("http://127.0.0.1:8080/", 8080))
        assertFalse(NativeAction.isLocalPage("http://localhost.evil:8080/", 8080))
        assertFalse(NativeAction.isLocalPage("http://127.0.0.1:8080/", 0))
    }
}

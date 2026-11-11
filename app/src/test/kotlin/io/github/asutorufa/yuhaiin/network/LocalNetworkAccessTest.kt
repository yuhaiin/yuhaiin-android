package io.github.asutorufa.yuhaiin.network

import io.github.asutorufa.yuhaiin.network.LocalNetworkAccess.Prompt
import io.github.asutorufa.yuhaiin.network.LocalNetworkAccess.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    private fun prompt(
        state: State = State.DENIED,
        needed: Boolean = true,
        asked: Boolean = false,
        canAskAgain: Boolean = false,
    ) = LocalNetworkAccess.prompt(state, needed, asked, canAskAgain)

    @Test
    fun enforcedOnlyFromAndroid17() {
        assertFalse(LocalNetworkAccess.isEnforced(36))
        assertFalse(LocalNetworkAccess.isEnforced(24))
        assertTrue(LocalNetworkAccess.isEnforced(37))
        assertTrue(LocalNetworkAccess.isEnforced(38))
    }

    @Test
    fun permissionNameMatchesThePlatform() {
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetworkAccess.PERMISSION)
    }

    @Test
    fun neverPromptsWhenGrantedOrNotRequired() {
        assertEquals(Prompt.NONE, prompt(state = State.GRANTED))
        assertEquals(Prompt.NONE, prompt(state = State.NOT_REQUIRED))
        assertEquals(Prompt.NONE, prompt(state = State.NOT_REQUIRED, asked = false))
    }

    @Test
    fun neededWheneverTheCoreOrTheVpnIsInUse() {
        // Independent of Allow LAN: the core also dials LAN hosts and the VPN carries LAN traffic.
        assertTrue(LocalNetworkAccess.isNeeded(coreActive = true, vpnActive = false))
        assertTrue(LocalNetworkAccess.isNeeded(coreActive = false, vpnActive = true))
        assertTrue(LocalNetworkAccess.isNeeded(coreActive = true, vpnActive = true))
        assertFalse(LocalNetworkAccess.isNeeded(coreActive = false, vpnActive = false))
    }

    @Test
    fun deniedPermissionNeverBlocksTheCoreOrTheVpn() {
        // The prompt is purely informational: it has no "block" outcome.
        assertTrue(Prompt.entries.none { it.name.contains("BLOCK") })
        assertFalse(LocalNetworkAccess.isUsable(State.DENIED))
    }

    @Test
    fun doesNotAskWhileNothingIsInUse() {
        assertEquals(Prompt.NONE, prompt(needed = false))
        assertEquals(Prompt.NONE, prompt(needed = false, asked = true, canAskAgain = true))
    }

    @Test
    fun requestsTheSystemDialogOnlyOnce() {
        assertEquals(Prompt.REQUEST, prompt(asked = false))
        // After a refusal the user sees a notice, never a repeated dialog.
        assertEquals(Prompt.NOTICE_REQUEST, prompt(asked = true, canAskAgain = true))
    }

    @Test
    fun sendsTheUserToSettingsWhenTheSystemWillNotAskAgain() {
        assertEquals(Prompt.NOTICE_SETTINGS, prompt(asked = true, canAskAgain = false))
    }

    @Test
    fun revocationAfterAGrantFollowsTheSameRules() {
        // Granted earlier (asked = true), later revoked in system settings.
        assertEquals(Prompt.NOTICE_SETTINGS, prompt(asked = true, canAskAgain = false))
    }

    @Test
    fun usableUnlessDenied() {
        assertTrue(LocalNetworkAccess.isUsable(State.GRANTED))
        assertTrue(LocalNetworkAccess.isUsable(State.NOT_REQUIRED))
        assertFalse(LocalNetworkAccess.isUsable(State.DENIED))
    }
}

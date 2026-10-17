package io.github.asutorufa.yuhaiin.data

import io.github.asutorufa.yuhaiin.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Preferences for a future native cmd/android TUN startup bridge. */
class TunStackPreferencesTest {
    @Test
    fun legacyStackRemainsDefault() {
        assertEquals("", Settings.tunStack.default)
        assertEquals("cubic", Settings.tunCongestionControl.default)
        assertEquals("system_gvisor", Settings.tunDriver.default)
    }

    @Test
    fun stackAndCongestionPreferencesAreIndependentOfLegacyDriver() {
        assertEquals("android_tun_stack", Constants.TUN_STACK_KEY)
        assertEquals("android_tun_congestion_control", Constants.TUN_CONGESTION_CONTROL_KEY)
        assertFalse(Settings.tunStack.name == Settings.tunDriver.name)
        assertFalse(Settings.tunCongestionControl.name == Settings.tunDriver.name)
        assertEquals(1, Settings.all.count { it.name == Constants.TUN_STACK_KEY })
        assertEquals(1, Settings.all.count { it.name == Constants.TUN_CONGESTION_CONTROL_KEY })
    }
}

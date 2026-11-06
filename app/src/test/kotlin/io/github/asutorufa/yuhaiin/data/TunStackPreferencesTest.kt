package io.github.asutorufa.yuhaiin.data

import io.github.asutorufa.yuhaiin.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** UI preferences for the lightweight TUN implementation used by Android. */
class TunStackPreferencesTest {
    @Test
    fun lightweightOptionsMatchNativeDefaults() {
        assertEquals("cubic", Settings.tunCongestionControl.default)
        assertEquals("classic", Settings.tunLossRecovery.default)
        assertEquals("fifo", Settings.tunOutputScheduler.default)
    }

    @Test
    fun onlySupportedRuntimeOptionsAppearInSettingsSnapshot() {
        val keys =
            listOf(
                Constants.TUN_CONGESTION_CONTROL_KEY,
                Constants.TUN_LOSS_RECOVERY_KEY,
                Constants.TUN_OUTPUT_SCHEDULER_KEY,
            )
        keys.forEach { key ->
            assertEquals(1, Settings.all.count { it.name == key })
        }
        assertFalse(Settings.all.any { it.name == Constants.TUN_DRIVER_KEY })
        assertFalse(Settings.all.any { it.name == Constants.TUN_STACK_KEY })
    }
}

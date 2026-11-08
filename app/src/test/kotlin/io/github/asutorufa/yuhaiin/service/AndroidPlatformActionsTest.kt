package io.github.asutorufa.yuhaiin.service

import io.github.asutorufa.yuhaiin.update.AndroidUpdateRelease
import io.github.asutorufa.yuhaiin.update.AndroidUpdateState
import io.github.asutorufa.yuhaiin.update.UpdateChannel
import io.github.asutorufa.yuhaiin.update.UpdateStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidPlatformActionsTest {
    @Test
    fun updateStateExposesOnlyWhatTheWebUiNeeds() {
        val state =
            AndroidUpdateState(
                channel = UpdateChannel.BETA,
                stage = UpdateStage.DOWNLOADING,
                progress = 40,
                release =
                    AndroidUpdateRelease(
                        "v2-1.2.3", "v2-1.2.3", "notes", "2026-11-08", "app.apk",
                        "https://example/app.apk", null, 1234,
                    ),
            )
        val json = state.toJson("v2-1.0.0")
        assertEquals("beta", json.channel)
        assertEquals(listOf("stable", "beta", "main"), json.channels)
        assertEquals("DOWNLOADING", json.stage)
        assertEquals("v2-1.2.3", json.release?.version)
        assertEquals("v2-1.0.0", json.currentVersion)
        assertNull(AndroidUpdateState().toJson("x").release)
    }
}

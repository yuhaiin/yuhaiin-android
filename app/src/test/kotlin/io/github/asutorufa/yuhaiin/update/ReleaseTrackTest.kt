package io.github.asutorufa.yuhaiin.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseTrackTest {
    @Test
    fun v2ReleasesAreIsolatedFromTheStableTrack() {
        assertEquals("v2", releaseTrack("v2-preview.1", null))
        assertEquals("v2", releaseTrack("main", "v2-main-abc"))
        assertEquals("stable", releaseTrack("v2.0.0", "v2.0.0"))
        assertEquals("stable", releaseTrack("main", "main-abc"))
        assertEquals("stable", releaseTrack("v1.9.0-beta", null))
    }
}

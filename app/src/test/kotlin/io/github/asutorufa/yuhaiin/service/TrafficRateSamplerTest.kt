package io.github.asutorufa.yuhaiin.service

import java.util.Locale
import kotlin.test.*
import org.junit.Test

class TrafficRateSamplerTest {
    private fun status(download: String, upload: String = "0", startedAt: Long = 1) =
        NativeStatus(
            startedAt = startedAt,
            session =
                NativeSession(
                    download = download,
                    upload = upload,
                    totalDownload = download,
                    totalUpload = upload,
                ),
        )

    @Test
    fun usesActualElapsedTimeAndPublishesTheTransitionToIdle() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val sampler = TrafficRateSampler()
            assertNull(sampler.sample(status("0"), 1000))
            assertEquals(
                "↓(5.00 KiB): 1.00 KiB/S ↑(2.50 KiB): 512.00 B/S",
                sampler.sample(status("5120", "2560"), 6000),
            )
            assertEquals(
                "↓(5.00 KiB): 0.00 B/S ↑(2.50 KiB): 0.00 B/S",
                sampler.sample(status("5120", "2560"), 16000),
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun unsignedCountersKeepSmallDeltasAboveSignedLongRange() {
        val sampler = TrafficRateSampler()
        assertNull(sampler.sample(status("18446744073709550000"), 1000))
        val rate = sampler.sample(status("18446744073709551024"), 2000)!!
        assertTrue(rate.contains("${formatBytes("1024")}/S"), rate)
    }

    @Test
    fun counterResetAndSessionRestartEstablishANewBaseline() {
        val sampler = TrafficRateSampler()
        assertNull(sampler.sample(status("10000"), 1000))
        assertNull(sampler.sample(status("0"), 2000))
        assertNotNull(sampler.sample(status("1000"), 3000))
        assertNull(sampler.sample(status("2000", startedAt = 2), 4000))
        assertNotNull(sampler.sample(status("3000", startedAt = 2), 5000))
    }

    @Test
    fun invalidAndNonIncreasingSamplesDoNotPoisonTheBaseline() {
        val sampler = TrafficRateSampler()
        assertNull(sampler.sample(status("0"), 1000))
        assertNull(sampler.sample(status("invalid"), 2000))
        assertNull(sampler.sample(status("1000"), 1000))
        assertTrue(sampler.sample(status("1024"), 2000)!!.contains("${formatBytes("1024")}/S"))
        // A newly visible display must wait for a fresh interval, not average hidden traffic.
        assertNull(TrafficRateSampler().sample(status("1024"), 100000))
    }
}

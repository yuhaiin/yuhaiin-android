package io.github.asutorufa.yuhaiin.compose

import io.github.asutorufa.yuhaiin.service.NativeNodeHealth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class HealthSummaryTest {
    private val ok = NativeNodeHealth(id = "a", ok = true, latencyMs = 20)
    private val failed = NativeNodeHealth(id = "b", ok = false, error = "timeout")

    private fun summary(
        tcp: NativeNodeHealth? = null,
        udp: NativeNodeHealth? = null,
        configuredNodes: Int = 2,
        checking: Boolean = false,
        preparing: Boolean = false,
        requestFailed: Boolean = false,
    ) = summarizeHealth(tcp, udp, configuredNodes, checking, preparing, requestFailed)

    @Test
    fun allNodesReachable() = assertEquals(HealthSummary.REACHABLE, summary(ok, ok))

    @Test
    fun oneFailedNodeIsPartial() = assertEquals(HealthSummary.PARTIAL, summary(ok, failed))

    @Test
    fun allFailedNodesAreFailed() = assertEquals(HealthSummary.FAILED, summary(failed, failed))

    @Test
    fun anUncheckedConfiguredNodeIsNotReportedReachable() =
        assertEquals(HealthSummary.UNCHECKED, summary(ok, null))

    @Test
    fun aSingleConfiguredNodeCanBeReachable() =
        assertEquals(HealthSummary.REACHABLE, summary(ok, null, configuredNodes = 1))

    @Test
    fun preparingOutranksTheRetryInProgress() =
        assertEquals(
            HealthSummary.PREPARING,
            summary(failed, failed, checking = true, preparing = true),
        )

    @Test
    fun requestFailureIsFailed() =
        assertEquals(HealthSummary.FAILED, summary(requestFailed = true))

    @Test
    fun noNodesIsUnconfigured() =
        assertEquals(HealthSummary.UNCONFIGURED, summary(configuredNodes = 0))

    @Test
    fun identicalFailuresShareOneError() =
        assertEquals("timeout", sharedHealthError(failed, failed.copy(id = "c")))

    @Test
    fun differentOrPartialFailuresStayPerNode() {
        assertNull(sharedHealthError(failed, failed.copy(error = "refused")))
        assertNull(sharedHealthError(ok, failed))
        assertNull(sharedHealthError(failed, null))
    }
}

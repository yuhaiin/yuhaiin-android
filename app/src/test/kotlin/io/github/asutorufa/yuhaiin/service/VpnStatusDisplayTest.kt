package io.github.asutorufa.yuhaiin.service

import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlin.test.*
import org.junit.Test

class VpnStatusDisplayTest {
    @Test
    fun toggleFollowsTheConnectionState() {
        assertEquals(VpnToggle.CONNECT, VpnStatus(state = State.DISCONNECTED).toggle())
        assertEquals(VpnToggle.CONNECT, VpnStatus(state = State.ERROR).toggle())
        assertEquals(VpnToggle.STOP, VpnStatus(state = State.CONNECTING).toggle())
        assertEquals(VpnToggle.STOP, VpnStatus(state = State.CONNECTED).toggle())
        assertEquals(VpnToggle.STOPPING, VpnStatus(state = State.DISCONNECTING).toggle())
    }

    @Test
    fun aPauseOutranksTheStateBecauseSnoozeIsSetBeforeTheStop() {
        State.entries.forEach {
            assertEquals(VpnToggle.RESUME, VpnStatus(state = it, resumeAt = 1).toggle())
        }
    }

    @Test
    fun connectedSummarySkipsWhateverIsUnknown() {
        assertEquals("Wi-Fi · All", VpnStatus(network = "Wi-Fi").connectedSummary("All"))
        assertEquals("All", VpnStatus().connectedSummary("All"))
        assertEquals("Wi-Fi", VpnStatus(network = "Wi-Fi").connectedSummary(""))
        assertEquals("", VpnStatus().connectedSummary(""))
    }
}

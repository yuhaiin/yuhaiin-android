package io.github.asutorufa.yuhaiin.service

import kotlin.test.*
import org.junit.Test

class NativeStatusTest {
    @Test
    fun wireContractKeepsLargeTotalsAndOptionalNodes() {
        val value =
            decodeNativeStatus(
                """{"startedAt":123,"durationSeconds":5,"session":{"download":"18446744073709551615","upload":"0","totalDownload":"18446744073709551615","totalUpload":"0","active":3,"opened":"9","failed":"1"},"futureField":true}"""
            )!!
        assertNull(value.tcp)
        assertEquals("18446744073709551615", value.session.totalDownload)
        assertEquals(3, value.session.active)
        assertEquals("1", value.session.failed)
        assertNull(decodeNativeStatus("invalid"))
        assertNull(decodeNativeHealth("invalid"))
    }
}

package io.github.asutorufa.yuhaiin.compose

import io.github.asutorufa.yuhaiin.logging.LogLevel
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LogLevelTest {

    @Test
    fun testDebugFilter() {
        val filter = LogLevel.DEBUG
        assertTrue(LogLevel.DEBUG.enabled(filter))
        assertTrue(LogLevel.INFO.enabled(filter))
        assertTrue(LogLevel.WARN.enabled(filter))
        assertTrue(LogLevel.ERROR.enabled(filter))
    }

    @Test
    fun testInfoFilter() {
        val filter = LogLevel.INFO
        assertFalse(LogLevel.DEBUG.enabled(filter))
        assertTrue(LogLevel.INFO.enabled(filter))
        assertTrue(LogLevel.WARN.enabled(filter))
        assertTrue(LogLevel.ERROR.enabled(filter))
    }

    @Test
    fun testWarnFilter() {
        val filter = LogLevel.WARN
        assertFalse(LogLevel.DEBUG.enabled(filter))
        assertFalse(LogLevel.INFO.enabled(filter))
        assertTrue(LogLevel.WARN.enabled(filter))
        assertTrue(LogLevel.ERROR.enabled(filter))
    }

    @Test
    fun testErrorEnabled() {
        assertTrue(LogLevel.ERROR.enabled(LogLevel.DEBUG))
        assertTrue(LogLevel.ERROR.enabled(LogLevel.INFO))
        assertTrue(LogLevel.ERROR.enabled(LogLevel.WARN))
        assertTrue(LogLevel.ERROR.enabled(LogLevel.ERROR))
    }
}

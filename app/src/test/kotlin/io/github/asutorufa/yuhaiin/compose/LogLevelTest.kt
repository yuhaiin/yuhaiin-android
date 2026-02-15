package io.github.asutorufa.yuhaiin.compose

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogLevelTest {

    @Test
    fun testDebugEnabled() {
        assertTrue(LogLevel.DEBUG.enabled(LogLevel.DEBUG))
        assertFalse(LogLevel.DEBUG.enabled(LogLevel.INFO))
        assertFalse(LogLevel.DEBUG.enabled(LogLevel.WARN))
        assertFalse(LogLevel.DEBUG.enabled(LogLevel.ERROR))
    }

    @Test
    fun testInfoEnabled() {
        assertTrue(LogLevel.INFO.enabled(LogLevel.DEBUG))
        assertTrue(LogLevel.INFO.enabled(LogLevel.INFO))
        assertFalse(LogLevel.INFO.enabled(LogLevel.WARN))
        assertFalse(LogLevel.INFO.enabled(LogLevel.ERROR))
    }

    @Test
    fun testWarnEnabled() {
        assertTrue(LogLevel.WARN.enabled(LogLevel.DEBUG))
        assertTrue(LogLevel.WARN.enabled(LogLevel.INFO))
        assertTrue(LogLevel.WARN.enabled(LogLevel.WARN))
        assertFalse(LogLevel.WARN.enabled(LogLevel.ERROR))
    }

    @Test
    fun testErrorEnabled() {
        assertTrue(LogLevel.ERROR.enabled(LogLevel.DEBUG))
        assertTrue(LogLevel.ERROR.enabled(LogLevel.INFO))
        assertTrue(LogLevel.ERROR.enabled(LogLevel.WARN))
        assertTrue(LogLevel.ERROR.enabled(LogLevel.ERROR))
    }
}

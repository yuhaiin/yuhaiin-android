package io.github.asutorufa.yuhaiin.compose

import org.junit.Test
import kotlin.test.assertEquals

class LogcatTest {

    @Test
    fun testParseThreadTime() {
        val line = "05-26 11:02:36.886  5689  5689 D AndroidRuntime: CheckJNI is OFF"
        val log = parseLog(line)
        assertEquals("05-26 11:02:36.886", log.time)
        assertEquals(5689, log.pid)
        assertEquals(5689, log.tid)
        assertEquals(LogLevel.DEBUG, log.level)
        assertEquals("AndroidRuntime", log.tag)
        assertEquals("CheckJNI is OFF", log.content)
    }

    @Test
    fun testParseTime() {
        val line = "06-04 02:32:14.002 D/dalvikvm(  236): GC_CONCURRENT freed 580K, 51% free [...]"
        val log = parseLog(line)
        assertEquals("06-04 02:32:14.002", log.time)
        assertEquals(236, log.pid)
        assertEquals(LogLevel.DEBUG, log.level)
        assertEquals("dalvikvm", log.tag)
        assertEquals("GC_CONCURRENT freed 580K, 51% free [...]", log.content)
    }

    @Test
    fun testParseUnknown() {
        val line = "some random log line"
        val log = parseLog(line)
        assertEquals("some random log line", log.content)
    }
}

package io.github.asutorufa.yuhaiin.logging

import kotlin.test.*
import org.junit.Test

class LogBufferTest {
    @Test
    fun longRunningStreamStaysBoundedAndKeepsNewestEntries() {
        val buffer = LogBuffer(2000)
        repeat(100) { batch ->
            buffer.append((0..255).map { LogEntry(content = "${batch * 256 + it}") })
        }
        val entries = buffer.snapshot()
        assertEquals(2000, entries.size)
        assertEquals("25599", entries.last().content)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }

    @Test
    fun threadtimeAndLegacyTimeAreParsedWithoutDiscardingUnmatchedLines() {
        val current = parseLogv2("05-26 11:02:36.886  5689  5700 E AndroidRuntime: failure")
        assertEquals(LogLevel.ERROR, current.level)
        assertEquals(5689, current.pid)
        assertEquals(5700, current.tid)
        assertEquals("failure", current.content)
        assertEquals("GC", parseLogv2("06-04 02:32:14.002 D/dalvikvm(  236): GC").content)
        assertEquals("native stack trace", parseLogv2("native stack trace").content)
    }
}

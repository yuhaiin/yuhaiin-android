package io.github.asutorufa.yuhaiin.logging

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class LogReaderTest {
    @Test
    fun openingPageLoadsHistoryBeforeFollowingNewLogs() = runBlocking {
        val history = listOf(logLine("00.001", "starting"), logLine("00.002", "connected"))
        val buffer = LogBuffer()
        readLogcat(
            emptyList(),
            startProcess = { command ->
                logcatFixture(command, history, listOf(logLine("01.000", "live")))
            },
        ) {
            buffer.append(it)
        }
        assertEquals(listOf("starting", "connected", "live"), buffer.snapshot().map { it.content })
    }

    @Test
    fun initialHistoryStaysBoundedAndKeepsNewestEntries() = runBlocking {
        val history = (0..2000).map { logLine("00.001", "$it") }
        val buffer = LogBuffer()
        readLogcat(emptyList(), startProcess = { logcatFixture(it, history) }) { buffer.append(it) }
        assertEquals(2000, buffer.snapshot().size)
        assertEquals("1", buffer.snapshot().first().content)
        assertEquals("2000", buffer.snapshot().last().content)
    }

    @Test
    fun restartCatchesUpWithoutReplayingHistoryOrDroppingIdenticalNewMessages() = runBlocking {
        val cursor = LogcatCursor()
        val buffer = LogBuffer()
        val repeated = logLine("00.002", "repeated")
        val history = listOf(logLine("00.001", "starting"), repeated, repeated)
        readLogcat(emptyList(), cursor, { logcatFixture(it, history) }) { buffer.append(it) }

        val updated =
            history +
                repeated +
                logLine("00.002", "same timestamp") +
                logLine("01.000", "background")
        readLogcat(
            emptyList(),
            cursor,
            { command ->
                assertEquals("10-05 10:00:00.002", command.last())
                logcatFixture(command, updated, listOf(logLine("02.000", "live")))
            },
        ) {
            buffer.append(it)
        }
        assertEquals(
            listOf(
                "starting",
                "repeated",
                "repeated",
                "repeated",
                "same timestamp",
                "background",
                "live",
            ),
            buffer.snapshot().map { it.content },
        )
    }

    @Test
    fun clearDoesNotRestoreOldLogsOnRestart() = runBlocking {
        val cursor = LogcatCursor()
        val buffer = LogBuffer()
        val history = listOf(logLine("00.001", "starting"), logLine("00.002", "connected"))
        readLogcat(emptyList(), cursor, { logcatFixture(it, history) }) { buffer.append(it) }
        buffer.clear()
        readLogcat(emptyList(), cursor, { logcatFixture(it, history + logLine("01.000", "new")) }) {
            buffer.append(it)
        }
        assertEquals(listOf("new"), buffer.snapshot().map { it.content })
    }

    @Test
    fun cancellationBeforePublicationDoesNotSkipHistoryOnRestart() = runBlocking {
        val cursor = LogcatCursor()
        val history = listOf(logLine("00.001", "starting"), logLine("00.002", "connected"))
        assertFailsWith<CancellationException> {
            readLogcat(emptyList(), cursor, { logcatFixture(it, history) }) {
                throw CancellationException("page stopped before publication")
            }
        }
        val buffer = LogBuffer()
        readLogcat(emptyList(), cursor, { logcatFixture(it, history) }) { buffer.append(it) }
        assertEquals(listOf("starting", "connected"), buffer.snapshot().map { it.content })
    }

    @Test
    fun restartFiltersBufferHeadersButPreservesUnparsedMessagesAndExclusions() = runBlocking {
        val cursor = LogcatCursor()
        val buffer = LogBuffer()
        val header = "--------- beginning of main"
        val history = listOf(header, logLine("00.001", "starting"))
        readLogcat(emptyList(), cursor, { logcatFixture(it, history) }) { buffer.append(it) }
        readLogcat(
            listOf("excluded"),
            cursor,
            {
                logcatFixture(
                    it,
                    history,
                    listOf(
                        header,
                        "native stack trace",
                        logLine("01.000", "excluded"),
                        logLine("02.000", "live"),
                    ),
                )
            },
        ) {
            buffer.append(it)
        }
        assertEquals(
            listOf(header, "starting", "native stack trace", "live"),
            buffer.snapshot().map { it.content },
        )
    }

    @Test
    fun firstEntryIsPublishedBeforeBatchingCooldown() = runBlocking {
        val channel = Channel<LogEntry>(4096)
        channel.trySend(LogEntry(content = "live"))
        val firstRefresh = CompletableDeferred<List<LogEntry>>()
        val collector =
            launch(start = CoroutineStart.UNDISPATCHED) {
                collectLogBatches(channel) { firstRefresh.complete(it) }
            }
        try {
            assertTrue(firstRefresh.isCompleted)
            assertEquals("live", firstRefresh.await().single().content)
        } finally {
            collector.cancelAndJoin()
            channel.cancel()
        }
    }

    @Test
    fun queuedBurstPublishesNewestEntryInFirstRefresh() = runBlocking {
        val channel = Channel<LogEntry>(4096)
        repeat(4096) { channel.trySend(LogEntry(content = "$it")) }
        val firstRefresh = CompletableDeferred<List<LogEntry>>()
        val collector = launch { collectLogBatches(channel) { firstRefresh.complete(it) } }
        try {
            val entries = withTimeout(2000) { firstRefresh.await() }
            assertEquals("4095", entries.last().content)
        } finally {
            collector.cancelAndJoin()
            channel.cancel()
        }
    }

    private fun logLine(second: String, message: String) =
        "10-05 10:00:$second  1000  1001 I GoLog: $message"

    /** Simulates logcat's inclusive -T selection, followed by newly generated entries. */
    private fun logcatFixture(
        command: List<String>,
        history: List<String>,
        live: List<String> = emptyList(),
    ): Process {
        val tail = command[command.indexOf("-T") + 1]
        val selected =
            tail.toIntOrNull()?.let { history.takeLast(it) }
                ?: history.filter { parseLogv2(it).time >= tail }
        val input =
            ByteArrayInputStream((selected + live).joinToString("\n", postfix = "\n").toByteArray())
        return object : Process() {
            override fun getInputStream() = input

            override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())

            override fun getOutputStream() = ByteArrayOutputStream()

            override fun waitFor() = 0

            override fun exitValue() = 0

            override fun destroy() {
                input.close()
            }
        }
    }
}

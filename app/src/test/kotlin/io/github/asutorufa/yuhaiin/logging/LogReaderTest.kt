package io.github.asutorufa.yuhaiin.logging

import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
}

package io.github.asutorufa.yuhaiin.logging

import io.github.asutorufa.yuhaiin.compose.ACAutomaton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

private const val LOG_BATCH_CAPACITY = 4096

/** Cancellation destroys the child process before joining its blocking pipe reader. */
internal suspend fun readLogcat(
    exclusions: List<String>,
    cursor: LogcatCursor = LogcatCursor(),
    startProcess: (List<String>) -> Process = {
        ProcessBuilder(it).redirectErrorStream(true).start()
    },
    publish: (List<LogEntry>) -> Unit,
) = coroutineScope {
    val matcher =
        ACAutomaton().apply {
            exclusions.forEach(::insert)
            buildFail()
        }
    val channel = Channel<LogEntry>(LOG_BATCH_CAPACITY, BufferOverflow.DROP_OLDEST)
    val isReplay = cursor.replayFilter()
    val process =
        withContext(Dispatchers.IO) {
            startProcess(cursor.command())
        }
    val reader =
        launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader().use { input ->
                    while (isActive) {
                        val line = input.readLine() ?: break
                        if (!matcher.exist(line)) {
                            val entry = parseLogv2(line)
                            if (!isReplay(entry)) channel.trySend(entry)
                        }
                    }
                }
            } finally {
                channel.close()
            }
        }
    try {
        collectLogBatches(channel) { batch ->
            publish(batch)
            // Advance only after publication, never past entries still queued on cancellation.
            cursor.record(batch)
        }
    } finally {
        process.destroy()
        reader.cancelAndJoin()
        channel.cancel()
    }
}

internal suspend fun collectLogBatches(
    channel: ReceiveChannel<LogEntry>,
    publish: (List<LogEntry>) -> Unit,
) = coroutineScope {
    while (isActive) {
        val first = channel.receiveCatching().getOrNull() ?: break
        val batch = mutableListOf(first)
        // Drain the bounded queue in this refresh so bursts do not replay seconds behind live logs.
        for (index in 1 until LOG_BATCH_CAPACITY) {
            val next = channel.tryReceive().getOrNull() ?: break
            batch.add(next)
        }
        publish(batch)
        // Publish the first entry immediately, then coalesce busy streams to avoid excessive
        // redraws.
        delay(120)
    }
}

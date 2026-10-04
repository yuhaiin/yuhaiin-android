package io.github.asutorufa.yuhaiin.logging

import io.github.asutorufa.yuhaiin.compose.ACAutomaton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/** Cancellation destroys the child process before joining its blocking pipe reader. */
suspend fun readLogcat(exclusions: List<String>, publish: (List<LogEntry>) -> Unit) =
    coroutineScope {
        val matcher =
            ACAutomaton().apply {
                exclusions.forEach(::insert)
                buildFail()
            }
        val channel = Channel<LogEntry>(4096, BufferOverflow.DROP_OLDEST)
        val process =
            withContext(Dispatchers.IO) {
                ProcessBuilder("logcat", "-v", "threadtime", "-T", "1")
                    .redirectErrorStream(true)
                    .start()
            }
        val reader =
            launch(Dispatchers.IO) {
                try {
                    process.inputStream.bufferedReader().use { input ->
                        while (isActive) {
                            val line = input.readLine() ?: break
                            if (!matcher.exist(line)) channel.trySend(parseLogv2(line))
                        }
                    }
                } finally {
                    channel.close()
                }
            }
        try {
            while (isActive) {
                val first = channel.receiveCatching().getOrNull() ?: break
                delay(120)
                val batch = mutableListOf(first)
                repeat(255) { channel.tryReceive().getOrNull()?.let(batch::add) }
                publish(batch)
            }
        } finally {
            process.destroy()
            reader.cancelAndJoin()
            channel.cancel()
        }
    }

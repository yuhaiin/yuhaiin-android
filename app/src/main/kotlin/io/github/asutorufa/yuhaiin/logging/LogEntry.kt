package io.github.asutorufa.yuhaiin.logging

import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

enum class LogLevel(val tag: String, val priority: Int) {
    DEBUG("DEBUG", 0),
    INFO("INFO", 1),
    WARN("WARN", 2),
    ERROR("ERROR", 3);

    fun enabled(filter: LogLevel): Boolean = priority >= filter.priority
}

private val ids = AtomicLong()

data class LogEntry(
    val level: LogLevel = LogLevel.INFO,
    val time: String = "",
    val content: String = "",
    val tag: String = "",
    val pid: Int = 0,
    val tid: Int = 0,
    val id: Long = ids.incrementAndGet(),
) {
    fun line(): String = "$time $pid $tid ${level.tag.first()} $tag: $content"
}

private val threadTime =
    Pattern.compile(
        "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+(.+?)\\s*: (.*)$"
    )
private val time =
    Pattern.compile(
        "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\w)/(.+?)\\(\\s*(\\d+)\\): (.*)$"
    )

private fun level(tag: String) =
    when (tag) {
        "V",
        "D" -> LogLevel.DEBUG
        "W" -> LogLevel.WARN
        "E",
        "F" -> LogLevel.ERROR
        else -> LogLevel.INFO
    }

fun parseLogv2(line: String): LogEntry {
    val match = threadTime.matcher(line)
    if (match.matches())
        return LogEntry(
            level(match.group(4)!!),
            match.group(1)!!,
            match.group(6)!!,
            match.group(5)!!,
            match.group(2)?.toIntOrNull() ?: 0,
            match.group(3)?.toIntOrNull() ?: 0,
        )
    val fallback = time.matcher(line)
    if (fallback.matches())
        return LogEntry(
            level(fallback.group(2)!!),
            fallback.group(1)!!,
            fallback.group(5)!!,
            fallback.group(3)!!,
            fallback.group(4)?.toIntOrNull() ?: 0,
        )
    return LogEntry(content = line)
}

/** Bounded independently of Compose and safe for a producer and a UI/export consumer. */
class LogBuffer(private val capacity: Int = LOG_HISTORY_CAPACITY) {
    private val entries = java.util.ArrayDeque<LogEntry>()

    @Synchronized
    fun append(batch: List<LogEntry>): List<LogEntry> {
        batch.forEach {
            entries.addLast(it)
            if (entries.size > capacity) entries.removeFirst()
        }
        return entries.toList()
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized fun snapshot(): List<LogEntry> = entries.toList()
}

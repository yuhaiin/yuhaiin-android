package io.github.asutorufa.yuhaiin.logging

internal const val LOG_HISTORY_CAPACITY = 2000

/** Retained with the page's buffer so lifecycle restarts catch up without replaying it. */
internal class LogcatCursor {
    private var time: String? = null
    private val boundary = mutableMapOf<LogEntry, Int>()

    fun command(): List<String> =
        listOf("logcat", "-v", "threadtime", "-T", time ?: LOG_HISTORY_CAPACITY.toString())

    /** -T timestamps are inclusive; consume only the occurrences already published. */
    fun replayFilter(): (LogEntry) -> Boolean {
        val resumeTime = time
        val remaining = boundary.toMutableMap()
        return { entry ->
            if (
                resumeTime != null &&
                    entry.time.isEmpty() &&
                    entry.content.startsWith("--------- beginning of ")
            ) {
                true
            } else if (entry.time == resumeTime) {
                val key = entry.copy(id = 0)
                val count = remaining[key] ?: 0
                if (count > 0) remaining[key] = count - 1
                count > 0
            } else {
                false
            }
        }
    }

    fun record(entries: List<LogEntry>) {
        for (entry in entries) {
            if (entry.time.isEmpty()) continue
            if (entry.time != time) {
                time = entry.time
                boundary.clear()
            }
            val key = entry.copy(id = 0)
            boundary[key] = (boundary[key] ?: 0) + 1
        }
    }
}

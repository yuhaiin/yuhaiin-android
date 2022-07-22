package com.github.logviewer

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class LogItem internal constructor(line: String) {
    lateinit var time: Date
    var processId: Int = 0
    var threadId = 0
    var priority: String?
    var tag: String?
    var content: String?

    init {
        val matcher = sLogcatPattern.matcher(line)
        check(matcher.find()) { "logcat pattern not match: $line" }
        matcher.group(1)?.apply {
            time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).parse(this) as Date
        }
        matcher.group(2)?.apply { processId = toInt() }
        matcher.group(3)?.apply { threadId = toInt() }
        priority = matcher.group(4)
        tag = matcher.group(5)
        content = matcher.group(6)
    }

    val colorRes: Int?
        get() = LOGCAT_COLORS[priority]

    fun isFiltered(filter: String?): Boolean =
        SUPPORTED_FILTERS.indexOf(priority) >= SUPPORTED_FILTERS.indexOf(filter)

    companion object {
        private const val PRIORITY_VERBOSE = "V"
        private const val PRIORITY_DEBUG = "D"
        private const val PRIORITY_INFO = "I"
        private const val PRIORITY_WARNING = "W"
        private const val PRIORITY_ERROR = "E"
        private const val PRIORITY_FATAL = "F"
        private val sLogcatPattern = Pattern.compile(
            "([0-9^-]+-[0-9^ ]+ [0-9^:]+:[0-9^:]+\\.[0-9]+) +([0-9]+) +([0-9]+) ([VDIWEF]) ((?!: ).)+: (.*)"
        )

        private val LOGCAT_COLORS = mapOf(
            PRIORITY_VERBOSE to R.color.logcat_verbose,
            PRIORITY_DEBUG to R.color.logcat_debug,
            PRIORITY_INFO to R.color.logcat_info,
            PRIORITY_WARNING to R.color.logcat_warning,
            PRIORITY_ERROR to R.color.logcat_error,
            PRIORITY_FATAL to R.color.logcat_fatal
        )

        private val SUPPORTED_FILTERS = arrayOf(
            PRIORITY_VERBOSE,
            PRIORITY_DEBUG,
            PRIORITY_INFO,
            PRIORITY_WARNING,
            PRIORITY_ERROR,
            PRIORITY_FATAL
        )

        val IGNORED_LOG = arrayOf(
            "--------- beginning of crash",
            "--------- beginning of main",
            "--------- beginning of system"
        )
    }
}
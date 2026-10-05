package io.github.asutorufa.yuhaiin.logging

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rotations retain the bounded log buffer; collection itself follows the visible page lifecycle.
 */
class LogViewModel : ViewModel() {
    val buffer = LogBuffer()
    internal val cursor = LogcatCursor()
    private val _entries = MutableStateFlow(emptyList<LogEntry>())
    val entries = _entries.asStateFlow()

    fun publish(entries: List<LogEntry>) {
        _entries.value = entries
    }
}

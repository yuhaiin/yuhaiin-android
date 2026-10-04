package io.github.asutorufa.yuhaiin.compose.route

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.asutorufa.yuhaiin.data.RouteRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yuhaiin.Yuhaiin

const val MAX_ROUTE_BYTES = 2 * 1024 * 1024

data class RouteDraft(
    val text: String = "",
    val original: String = "",
    val loaded: Boolean = false,
    val validating: Boolean = true,
    val invalidLine: Int? = null,
    val failure: String? = null,
    val saving: Boolean = false,
) {
    val dirty
        get() = text != original

    val valid
        get() = loaded && !validating && invalidLine == null && failure == null
}

/** SavedState contains only the route identifier. Large drafts live in atomic app-private files. */
class RouteDraftViewModel(
    application: Application,
    handle: SavedStateHandle,
    val routeName: String,
) : AndroidViewModel(application) {
    private val _draft = MutableStateFlow(RouteDraft())
    val draft = _draft.asStateFlow()
    private val edits = MutableStateFlow<String?>(null)
    private val file: AtomicFile
    private val disk = Mutex()
    @Volatile private var discarding = false

    init {
        handle["routeName"] = routeName
        val id =
            MessageDigest.getInstance("SHA-256").digest(routeName.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        file = AtomicFile(File(application.noBackupFilesDir, "route-drafts/$id.txt"))
        viewModelScope.launch {
            try {
                val original = RouteRepository.content(routeName)
                val text =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                file.openRead().use { input ->
                                    require(file.baseFile.length() <= MAX_ROUTE_BYTES)
                                    val bytes = input.readBytes()
                                    require(bytes.size <= MAX_ROUTE_BYTES)
                                    bytes.toString(Charsets.UTF_8)
                                }
                            }
                            .getOrDefault(original)
                    }
                _draft.value = RouteDraft(text = text, original = original, loaded = true)
                edits.value = text
            } catch (e: Exception) {
                _draft.update { it.copy(failure = e.message) }
            }
        }
        viewModelScope.launch {
            edits.filterNotNull().collectLatest { text ->
                delay(250)
                withContext(Dispatchers.IO) {
                    try {
                        disk.withLock {
                            if (!discarding && _draft.value.dirty && _draft.value.text == text)
                                writeDraft(text)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _draft.update { it.copy(failure = e.message, validating = false) }
                        return@withContext
                    }
                    var invalid: Int? = null
                    for ((index, line) in text.lineSequence().withIndex()) {
                        ensureActive()
                        if (line.isBlank()) continue
                        if (runCatching { Yuhaiin.parseCIDR(line.trim()) }.isFailure) {
                            invalid = index + 1
                            break
                        }
                    }
                    _draft.update { current ->
                        if (current.text == text)
                            current.copy(validating = false, invalidLine = invalid)
                        else current
                    }
                }
            }
        }
    }

    fun edit(text: String) {
        if (text == _draft.value.text) return
        if (text.toByteArray().size > MAX_ROUTE_BYTES) {
            _draft.update {
                it.copy(
                    failure =
                        getApplication<Application>()
                            .getString(io.github.asutorufa.yuhaiin.R.string.route_too_large)
                )
            }
            return
        }
        _draft.update { it.copy(text = text, validating = true, failure = null) }
        edits.value = text
    }

    private fun writeDraft(text: String) {
        file.baseFile.parentFile?.mkdirs()
        val output = file.startWrite()
        try {
            output.write(text.toByteArray())
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    suspend fun save(): Boolean {
        val current = _draft.value
        if (!current.valid || current.saving) return false
        _draft.update { it.copy(saving = true) }
        return try {
            RouteRepository.save(routeName, current.text)
            withContext(Dispatchers.IO) {
                disk.withLock {
                    discarding = true
                    file.delete()
                    _draft.update { it.copy(original = current.text, saving = false) }
                    discarding = false
                }
            }
            true
        } catch (e: Exception) {
            _draft.update { it.copy(saving = false, failure = e.message) }
            false
        }
    }

    suspend fun discard() {
        withContext(Dispatchers.IO) {
            disk.withLock {
                discarding = true
                file.delete()
                _draft.update { it.copy(text = it.original, failure = null, validating = true) }
                discarding = false
            }
        }
        edits.value = _draft.value.original
    }
}

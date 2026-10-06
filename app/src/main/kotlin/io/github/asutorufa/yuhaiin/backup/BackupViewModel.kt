package io.github.asutorufa.yuhaiin.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupState(
    val busy: Boolean = false,
    val preview: BackupPreview? = null,
    val error: String? = null,
    val completed: Boolean = false,
)

class BackupViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(BackupState())
    val state = mutableState.asStateFlow()
    private var pending: ByteArray? = null

    fun run(operation: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null, completed = false)
        viewModelScope.launch {
            try {
                operation()
                mutableState.value = mutableState.value.copy(busy = false, completed = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value =
                    mutableState.value.copy(busy = false, error = error.message ?: error.toString())
            }
        }
    }

    fun preview(data: ByteArray, preview: BackupPreview) {
        pending = data
        mutableState.value = mutableState.value.copy(preview = preview)
    }

    fun cancelRestore() {
        if (mutableState.value.busy) return
        pending = null
        mutableState.value = mutableState.value.copy(preview = null, completed = false)
    }

    fun restore(disconnect: suspend () -> Unit) = run {
        val data = requireNotNull(pending) { "Select a backup first" }
        disconnect()
        ConfigBackup.restore(data)
        pending = null
        mutableState.value = mutableState.value.copy(preview = null)
    }
}

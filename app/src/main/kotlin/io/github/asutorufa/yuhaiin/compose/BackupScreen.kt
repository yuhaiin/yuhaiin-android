package io.github.asutorufa.yuhaiin.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.backup.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, disconnect: suspend () -> Unit) {
    val context = LocalContext.current
    val model: BackupViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) model.run { ConfigBackup.export(context, uri) }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                model.run {
                    val (data, preview) = ConfigBackup.read(context, uri)
                    model.preview(data, preview)
                }
        }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.backup_back)) }
                },
            )
        }
    ) { padding ->
        ReadingPane {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.backup_description))
                Text(
                    stringResource(R.string.backup_credentials),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = !state.busy,
                    onClick = {
                        val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                        export.launch("yuhaiin-backup-$date.json")
                    },
                ) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(
                    enabled = !state.busy,
                    onClick = { model.run { ConfigBackup.share(context) } },
                ) {
                    Text(stringResource(R.string.backup_share))
                }
                OutlinedButton(
                    enabled = !state.busy,
                    onClick = {
                        import.launch(
                            arrayOf("application/json", "application/octet-stream", "text/plain")
                        )
                    },
                ) {
                    Text(stringResource(R.string.backup_import))
                }
                if (state.busy) MaterialLinearProgressIndicator()
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.completed && state.preview == null)
                    Text(stringResource(R.string.backup_completed))
            }
        }
    }
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = { model.cancelRestore() },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            R.string.backup_preview,
                            preview.createdAt,
                            preview.nodes,
                            preview.routes,
                            preview.preferences,
                        )
                    )
                    Text(stringResource(R.string.backup_restore_hint))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.busy) MaterialLinearProgressIndicator()
                }
            },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { model.restore(disconnect) }) {
                    Text(stringResource(R.string.backup_restore))
                }
            },
            dismissButton = {
                TextButton(enabled = !state.busy, onClick = { model.cancelRestore() }) {
                    Text(stringResource(R.string.backup_cancel))
                }
            },
        )
    }
}

package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.update.AndroidUpdateState
import io.github.asutorufa.yuhaiin.update.UpdateChannel
import io.github.asutorufa.yuhaiin.update.UpdateManager
import io.github.asutorufa.yuhaiin.update.UpdateStage

@Composable
fun UpdateCard(
    manager: UpdateManager?,
    proxyReady: Boolean,
    startProxy: () -> Unit,
) {
    if (manager == null) return

    val state by manager.state.collectAsStateWithLifecycle()
    var channelMenuExpanded by remember { mutableStateOf(false) }
    val busy =
        state.checking ||
            state.stage == UpdateStage.DOWNLOADING ||
            state.stage == UpdateStage.VERIFYING ||
            state.stage == UpdateStage.INSTALLING ||
            state.stage == UpdateStage.WAITING_PERMISSION

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.software_update),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.software_update_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!proxyReady) {
                Text(
                    stringResource(R.string.update_start_proxy_before_check),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.update_proxy_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = startProxy, enabled = !busy) {
                    Text(stringResource(R.string.update_start_proxy))
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = { channelMenuExpanded = true },
                        enabled = !busy && proxyReady,
                    ) {
                        Text(state.channel.value.uppercase())
                    }
                    DropdownMenu(
                        expanded = channelMenuExpanded,
                        onDismissRequest = { channelMenuExpanded = false },
                    ) {
                        UpdateChannel.entries.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text(channel.value.uppercase()) },
                                onClick = {
                                    channelMenuExpanded = false
                                    manager.setChannel(channel)
                                },
                            )
                        }
                    }
                }

                Button(onClick = manager::check, enabled = !busy && proxyReady) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(stringResource(R.string.update_check))
                }
            }

            state.release?.let { release ->
                Text(
                    stringResource(R.string.update_available, release.version),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.update_asset, release.assetName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = manager::apply,
                    enabled = !busy && proxyReady && state.stage != UpdateStage.COMPLETED,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        stringResource(if (busy) R.string.update_updating else R.string.update_now)
                    )
                }
            }

            if (
                state.stage == UpdateStage.DOWNLOADING ||
                    state.stage == UpdateStage.VERIFYING ||
                    state.stage == UpdateStage.INSTALLING
            ) {
                Text(updateStageLabel(state), style = MaterialTheme.typography.bodyMedium)
                if (state.stage == UpdateStage.DOWNLOADING && state.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.update_progress,
                            formatBytes(state.downloadedBytes),
                            formatBytes(state.totalBytes),
                            state.progress,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.stage == UpdateStage.DOWNLOADING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.update_downloading_proxy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.update_progress,
                            formatBytes(state.downloadedBytes),
                            formatBytes(state.totalBytes),
                            state.progress,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            state.release
                ?.notes
                ?.takeIf { it.isNotBlank() }
                ?.let { notes ->
                    var expanded by
                        androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                    androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(R.string.update_notes))
                    }
                    if (expanded) Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            if (state.stage == UpdateStage.WAITING_PERMISSION) {
                OutlinedButton(onClick = { manager.resumePendingInstall(openSettings = true) }) {
                    Text(stringResource(R.string.update_resume))
                }
            }
            state.reason?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error?.let {
                Text(
                    stringResource(R.string.update_failed, it),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.stage == UpdateStage.COMPLETED) {
                Text(stringResource(R.string.update_install_completed))
            }
        }
    }
}

@Composable
private fun updateStageLabel(state: AndroidUpdateState): String =
    when (state.stage) {
        UpdateStage.DOWNLOADING -> stringResource(R.string.update_stage_downloading)
        UpdateStage.VERIFYING -> stringResource(R.string.update_stage_verifying)
        UpdateStage.INSTALLING -> stringResource(R.string.update_stage_installing)
        else -> stringResource(R.string.update_stage_updating)
    }

private fun formatBytes(value: Long): String =
    if (value < 1024 * 1024) {
        "${(value / 1024).coerceAtLeast(1)} KB"
    } else {
        "%.1f MB".format(value / (1024f * 1024f))
    }

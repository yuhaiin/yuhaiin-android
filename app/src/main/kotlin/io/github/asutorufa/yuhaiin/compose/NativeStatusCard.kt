package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.*
import java.text.DateFormat
import java.util.Date

@Composable
fun NativeStatusCard(status: VpnStatus, connected: Boolean, checkHealth: () -> Unit) {
    val snapshot = remember(status.nativeStatus) { decodeNativeStatus(status.nativeStatus) }
    val health = remember(status.nativeHealth) { decodeNativeHealth(status.nativeHealth) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.native_health_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Info, stringResource(R.string.native_health_about))
                    }
                    val checkLabel =
                        stringResource(
                            if (status.healthChecking) R.string.native_health_checking
                            else R.string.native_health_check
                        )
                    IconButton(
                        enabled = !status.healthChecking,
                        onClick = checkHealth,
                        modifier = Modifier.semantics { contentDescription = checkLabel },
                    ) {
                        if (status.healthChecking)
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
                NodeHealth("TCP", snapshot?.tcp, health?.tcp, status.healthChecking)
                NodeHealth("UDP", snapshot?.udp, health?.udp, status.healthChecking)
                if (status.healthError.isNotBlank())
                    Text(
                        status.healthError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (health != null && !status.healthChecking)
                    Text(
                        stringResource(
                            R.string.native_health_checked,
                            DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(Date(health.checkedAt)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            if (snapshot != null) {
                if (connected) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SessionSummary(snapshot, connected)
            }
        }
    }
    if (showHelp)
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.native_health_title)) },
            text = { Text(stringResource(R.string.native_health_hint)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
}

@Composable
private fun NodeHealth(
    protocol: String,
    node: NativeNode?,
    health: NativeNodeHealth?,
    checking: Boolean,
) {
    val current = health?.takeIf { it.id == node?.id }
    val label =
        when {
            node == null -> stringResource(R.string.native_health_unconfigured)
            checking -> stringResource(R.string.native_health_checking)
            current == null -> stringResource(R.string.native_health_unknown)
            current.ok -> stringResource(R.string.native_health_ok, current.latencyMs)
            else -> stringResource(R.string.native_health_failed)
        }
    val color =
        when {
            current == null || checking -> MaterialTheme.colorScheme.onSurfaceVariant
            current.ok -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                protocol,
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                node?.name?.ifBlank { node.id } ?: label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (current?.ok == false && current.error.isNotBlank() && !checking)
                Text(
                    current.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
        }
        if (node != null)
            Surface(color = color.copy(alpha = 0.10f), shape = MaterialTheme.shapes.small) {
                Text(
                    label,
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 140.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
    }
}

@Composable
private fun SessionSummary(snapshot: NativeStatus, connected: Boolean) {
    Text(
        stringResource(
            if (connected) R.string.native_session_title else R.string.native_session_last
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    if (!connected) {
        val seconds = snapshot.durationSeconds
        Text(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(snapshot.startedAt)) +
                " · " +
                "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SessionMetric(
            stringResource(R.string.native_session_download),
            formatBytes(snapshot.session.download),
            Modifier.weight(1f),
            prominent = true,
        )
        SessionMetric(
            stringResource(R.string.native_session_upload),
            formatBytes(snapshot.session.upload),
            Modifier.weight(1f),
            prominent = true,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SessionMetric(
            stringResource(
                if (connected) R.string.native_session_active
                else R.string.native_session_last_active
            ),
            snapshot.session.active.toString(),
            Modifier.weight(1f),
        )
        SessionMetric(
            stringResource(R.string.native_session_opened),
            snapshot.session.opened,
            Modifier.weight(1f),
        )
        SessionMetric(
            stringResource(R.string.native_session_failed),
            snapshot.session.failed,
            Modifier.weight(1f),
        )
    }
    Text(
        stringResource(
            R.string.native_total_bytes,
            formatBytes(snapshot.session.totalDownload),
            formatBytes(snapshot.session.totalUpload),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SessionMetric(
    label: String,
    value: String,
    modifier: Modifier,
    prominent: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            style =
                if (prominent) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.titleMedium,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

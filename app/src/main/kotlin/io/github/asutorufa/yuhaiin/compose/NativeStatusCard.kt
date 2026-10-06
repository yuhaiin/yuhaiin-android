package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.*
import java.text.DateFormat
import java.util.Date

@Composable
fun NativeStatusCard(status: VpnStatus, connected: Boolean, checkHealth: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeStatusContent(status, connected, checkHealth)
        }
    }
}

@Composable
private fun NativeStatusContent(status: VpnStatus, connected: Boolean, checkHealth: () -> Unit) {
    val snapshot = remember(status.nativeStatus) { decodeNativeStatus(status.nativeStatus) }
    val health = remember(status.nativeHealth) { decodeNativeHealth(status.nativeHealth) }
    if (connected) {
        HorizontalDivider()
        Text(
            stringResource(R.string.native_health_title),
            style = MaterialTheme.typography.titleMedium,
        )
        NodeHealth("TCP", snapshot?.tcp, health?.tcp, status.healthChecking)
        NodeHealth("UDP", snapshot?.udp, health?.udp, status.healthChecking)
        if (status.healthError.isNotBlank())
            Text(status.healthError, color = MaterialTheme.colorScheme.error)
        if (health != null && !status.healthChecking)
            Text(
                stringResource(
                    R.string.native_health_checked,
                    DateFormat.getTimeInstance().format(Date(health.checkedAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        Text(
            stringResource(R.string.native_health_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(enabled = !status.healthChecking, onClick = checkHealth) {
            Text(
                stringResource(
                    if (status.healthChecking) R.string.native_health_checking
                    else R.string.native_health_check
                )
            )
        }
    }
    if (snapshot != null) {
        HorizontalDivider()
        Text(
            stringResource(
                if (connected) R.string.native_session_title else R.string.native_session_last
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!connected) {
            Text(DateFormat.getDateTimeInstance().format(Date(snapshot.startedAt)))
            val seconds = snapshot.durationSeconds
            Text(
                stringResource(
                    R.string.connection_duration,
                    "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60),
                )
            )
        }
        Text(
            stringResource(
                R.string.native_session_bytes,
                formatBytes(snapshot.session.download),
                formatBytes(snapshot.session.upload),
            )
        )
        Text(
            stringResource(
                if (connected) R.string.native_session_counts
                else R.string.native_session_last_counts,
                snapshot.session.active,
                snapshot.session.opened,
                snapshot.session.failed,
            )
        )
        Text(
            stringResource(
                R.string.native_total_bytes,
                formatBytes(snapshot.session.totalDownload),
                formatBytes(snapshot.session.totalUpload),
            )
        )
    }
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
            checking -> stringResource(R.string.native_health_checking)
            node == null -> stringResource(R.string.native_health_unconfigured)
            current == null -> stringResource(R.string.native_health_unknown)
            current.ok -> stringResource(R.string.native_health_ok, current.latencyMs)
            else -> stringResource(R.string.native_health_failed)
        }
    Text(
        "$protocol · ${node?.name?.ifBlank { node.id } ?: "—"} · $label",
        color =
            if (current?.ok == false && !checking) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
    )
    if (current?.ok == false && current.error.isNotBlank() && !checking)
        Text(current.error, style = MaterialTheme.typography.bodySmall)
}

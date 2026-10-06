package io.github.asutorufa.yuhaiin.compose

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.VpnStatus
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import io.github.asutorufa.yuhaiin.service.labelResource
import kotlinx.coroutines.delay

@Composable
fun ConnectionCard(
    state: State,
    error: String?,
    start: () -> Unit,
    stop: () -> Unit,
    open: () -> Unit,
    status: VpnStatus = VpnStatus(),
    snooze: (Int) -> Unit = {},
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var pauseMenu by remember { mutableStateOf(false) }
    LaunchedEffect(status.connectedAt, status.resumeAt) {
        while (status.connectedAt > 0 || status.resumeAt > 0) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val busy = state == State.CONNECTING || state == State.DISCONNECTING
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (state == State.CONNECTED) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    if (status.resumeAt > 0) R.string.status_paused else state.labelResource()
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(
                    if (state == State.CONNECTED) R.string.connection_active
                    else R.string.connection_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state == State.CONNECTED) {
                val seconds =
                    if (status.connectedAt > 0) ((now - status.connectedAt) / 1000).coerceAtLeast(0)
                    else 0
                Text(
                    stringResource(
                        R.string.connection_duration,
                        "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60),
                    )
                )
                Text(status.speed.ifBlank { stringResource(R.string.speed_waiting) })
                Text(
                    stringResource(
                        R.string.connection_network,
                        status.network.ifBlank { "—" },
                        status.mtu,
                    )
                )
                Text("IPv4: ${status.ipv4.ifBlank { "—" }}")
                Text("IPv6: ${status.ipv6.ifBlank { "—" }}")
                Text(stringResource(R.string.connection_route, routeLabel(status.route)))
            }
            if (status.resumeAt > 0) {
                val minutes =
                    ((status.resumeAt - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) /
                        60_000
                Text(stringResource(R.string.pause_remaining, minutes.toInt()))
                Text(
                    stringResource(R.string.snooze_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = stop) { Text(stringResource(R.string.cancel_resume)) }
            }
            if (error != null)
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            if (busy) MaterialLinearProgressIndicator()
            if (state == State.CONNECTED) {
                Box {
                    TextButton(onClick = { pauseMenu = true }) {
                        Text(stringResource(R.string.snooze))
                    }
                    DropdownMenu(expanded = pauseMenu, onDismissRequest = { pauseMenu = false }) {
                        listOf(5, 15, 30).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.snooze_minutes, minutes)) },
                                onClick = {
                                    pauseMenu = false
                                    snooze(minutes)
                                },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = if (state == State.CONNECTED) stop else start, enabled = !busy) {
                    Text(
                        stringResource(
                            if (state == State.CONNECTED) R.string.Stop
                            else if (status.resumeAt > 0) R.string.resume_now else R.string.Connect
                        )
                    )
                }
                if (state == State.CONNECTED)
                    OutlinedButton(onClick = open) {
                        Text(stringResource(R.string.open_dashboard))
                    }
            }
        }
    }
}

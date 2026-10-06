package io.github.asutorufa.yuhaiin.compose

import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(status.connectedAt, status.resumeAt) {
        while (status.connectedAt > 0 || status.resumeAt > 0) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val connected = state == State.CONNECTED
    val busy = state == State.CONNECTING || state == State.DISCONNECTING
    val paused = status.resumeAt > 0
    val action =
        stringResource(
            if (connected) R.string.Stop else if (paused) R.string.resume_now else R.string.Connect
        )
    val accent =
        when {
            state == State.ERROR -> MaterialTheme.colorScheme.error
            connected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.animateContentSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(color = accent.copy(alpha = 0.12f), shape = CircleShape) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_vpn),
                            null,
                            Modifier.size(24.dp),
                            tint = accent,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            if (paused) R.string.status_paused else state.labelResource()
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (connected) {
                        val seconds =
                            if (status.connectedAt > 0)
                                ((now - status.connectedAt) / 1000).coerceAtLeast(0)
                            else 0
                        Text(
                            stringResource(
                                R.string.connection_duration,
                                "%02d:%02d:%02d"
                                    .format(seconds / 3600, seconds / 60 % 60, seconds % 60),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = connected,
                    enabled = !busy,
                    onCheckedChange = { if (it) start() else stop() },
                    modifier = Modifier.semantics { contentDescription = action },
                )
            }
            if (busy) MaterialLinearProgressIndicator()
            if (connected) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConnectionBadge(status.network.ifBlank { "—" })
                    ConnectionBadge(routeLabel(status.route))
                }
                ConnectionRates(status.speed)
            } else if (!paused && !busy) {
                Text(
                    stringResource(R.string.connection_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (paused) {
                val minutes =
                    ((status.resumeAt - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) /
                        60_000
                Text(stringResource(R.string.pause_remaining, minutes.toInt()))
                Text(
                    stringResource(R.string.snooze_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = stop, enabled = !busy) {
                    Text(stringResource(R.string.cancel_resume))
                }
            }
            if (!error.isNullOrBlank())
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        error,
                        Modifier.fillMaxWidth().padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            if (connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { detailsExpanded = !detailsExpanded },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.connection_details),
                                Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                if (detailsExpanded) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription =
                                    stringResource(
                                        if (detailsExpanded) R.string.connection_details_hide
                                        else R.string.connection_details_show
                                    ),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(onClick = open) {
                        Icon(
                            painterResource(R.drawable.open_in_browser),
                            stringResource(R.string.open_dashboard),
                        )
                    }
                    Box {
                        IconButton(onClick = { pauseMenu = true }) {
                            Icon(painterResource(R.drawable.pause), stringResource(R.string.snooze))
                        }
                        DropdownMenu(
                            expanded = pauseMenu,
                            onDismissRequest = { pauseMenu = false },
                        ) {
                            listOf(5, 15, 30).forEach { minutes ->
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.snooze_minutes, minutes))
                                    },
                                    onClick = {
                                        pauseMenu = false
                                        snooze(minutes)
                                    },
                                )
                            }
                        }
                    }
                }
                if (detailsExpanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConnectionDetail("MTU", status.mtu.toString())
                            ConnectionDetail("IPv4", status.ipv4.ifBlank { "—" })
                            ConnectionDetail("IPv6", status.ipv6.ifBlank { "—" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionDetail(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            Modifier.width(44.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private val trafficRates = Regex("""↓\([^)]*\):\s*(.*?)\s+↑\([^)]*\):\s*(.*)""")

@Composable
private fun ConnectionRates(speed: String) {
    // Keep unfamiliar notifier formats visible instead of dropping rate updates.
    val rates = remember(speed) { trafficRates.matchEntire(speed)?.groupValues }
    if (rates == null) {
        Text(
            speed.ifBlank { stringResource(R.string.speed_waiting) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ConnectionRate(
            stringResource(R.string.connection_download_rate),
            rates[1],
            Modifier.weight(1f),
        )
        ConnectionRate(
            stringResource(R.string.connection_upload_rate),
            rates[2],
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConnectionRate(label: String, rate: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(rate, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

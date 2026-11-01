package io.github.asutorufa.yuhaiin.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

// errProbePolicyUnavailable in yuhaiin's pkg/node/probe_policy.go. If the text ever changes the
// check simply reads as failed, as it did before this distinction existed.
private const val PROBE_NOT_READY = "probe routing and traffic policy is not ready"
private const val PROBE_RETRY_MILLIS = 3_000L
private const val MAX_PROBE_RETRIES = 4

private val NativeNodeHealth.probeNotReady
    get() = !ok && error == PROBE_NOT_READY

internal enum class HealthSummary {
    PREPARING,
    CHECKING,
    UNCONFIGURED,
    UNCHECKED,
    REACHABLE,
    PARTIAL,
    FAILED,
}

/** [tcp] and [udp] are results for the currently configured nodes only. */
internal fun summarizeHealth(
    tcp: NativeNodeHealth?,
    udp: NativeNodeHealth?,
    configuredNodes: Int,
    checking: Boolean,
    preparing: Boolean,
    requestFailed: Boolean,
): HealthSummary {
    val results = listOfNotNull(tcp, udp)
    return when {
        preparing -> HealthSummary.PREPARING
        checking -> HealthSummary.CHECKING
        configuredNodes == 0 -> HealthSummary.UNCONFIGURED
        requestFailed -> HealthSummary.FAILED
        results.isEmpty() -> HealthSummary.UNCHECKED
        results.none { it.ok } -> HealthSummary.FAILED
        results.all { it.ok } && results.size == configuredNodes -> HealthSummary.REACHABLE
        results.any { !it.ok } -> HealthSummary.PARTIAL
        else -> HealthSummary.UNCHECKED
    }
}

/** The one error every failed node shares, so it can be shown once instead of per node. */
internal fun sharedHealthError(tcp: NativeNodeHealth?, udp: NativeNodeHealth?): String? =
    if (tcp == null || udp == null || tcp.ok || udp.ok || tcp.error != udp.error) null
    else tcp.error.ifBlank { null }

@Composable
fun NativeStatusCard(status: VpnStatus, connected: Boolean, checkHealth: () -> Unit) {
    val snapshot = remember(status.nativeStatus) { decodeNativeStatus(status.nativeStatus) }
    val health = remember(status.nativeHealth) { decodeNativeHealth(status.nativeHealth) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tcpHealth = health?.tcp?.takeIf { it.id == snapshot?.tcp?.id }
    val udpHealth = health?.udp?.takeIf { it.id == snapshot?.udp?.id }
    var retries by remember(status.connectedAt) { mutableIntStateOf(0) }
    val preparing =
        connected &&
            retries < MAX_PROBE_RETRIES &&
            listOfNotNull(tcpHealth, udpHealth).any { it.probeNotReady }
    val currentCheckHealth by rememberUpdatedState(checkHealth)
    // Readiness is transient after connecting, but give up so a lasting fault still shows.
    LaunchedEffect(preparing, health?.checkedAt, status.healthChecking) {
        if (preparing && !status.healthChecking) {
            delay(PROBE_RETRY_MILLIS)
            retries++
            currentCheckHealth()
        }
    }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, AppMotion.effects())
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (connected) {
                Column {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(
                                onClickLabel =
                                    stringResource(
                                        if (expanded) R.string.native_health_hide
                                        else R.string.native_health_show
                                    ),
                                onClick = { expanded = !expanded },
                            )
                            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.native_health_title),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HealthSummaryBadge(
                            summarizeHealth(
                                tcpHealth,
                                udpHealth,
                                listOfNotNull(snapshot?.tcp, snapshot?.udp).size,
                                status.healthChecking,
                                preparing,
                                status.healthError.isNotBlank(),
                            ),
                            listOfNotNull(tcpHealth, udpHealth).maxOfOrNull { it.latencyMs } ?: 0,
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            Modifier.size(20.dp).rotate(chevronRotation),
                        )
                    }
                    AnimatedVisibility(
                        expanded,
                        enter = AppMotion.reveal,
                        exit = AppMotion.conceal,
                    ) {
                        Column(
                            Modifier.animateContentSize()
                                .padding(horizontal = 20.dp)
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val shared = sharedHealthError(tcpHealth, udpHealth)
                            NodeHealth(
                                "TCP",
                                snapshot?.tcp,
                                tcpHealth,
                                status.healthChecking,
                                preparing,
                                showError = shared == null,
                            )
                            NodeHealth(
                                "UDP",
                                snapshot?.udp,
                                udpHealth,
                                status.healthChecking,
                                preparing,
                                showError = shared == null,
                            )
                            val failure =
                                status.healthError.ifBlank { null }
                                    ?: shared?.takeIf { !preparing && !status.healthChecking }
                            if (failure != null)
                                Text(
                                    failure,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (health != null && !status.healthChecking)
                                        stringResource(
                                            R.string.native_health_checked,
                                            DateFormat.getTimeInstance(DateFormat.SHORT)
                                                .format(Date(health.checkedAt)),
                                        )
                                    else "",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { showHelp = true }) {
                                    Icon(
                                        Icons.Default.Info,
                                        stringResource(R.string.native_health_about),
                                    )
                                }
                                val checkLabel =
                                    stringResource(
                                        if (status.healthChecking) R.string.native_health_checking
                                        else R.string.native_health_check
                                    )
                                IconButton(
                                    enabled = !status.healthChecking,
                                    onClick = checkHealth,
                                    modifier =
                                        Modifier.semantics { contentDescription = checkLabel },
                                ) {
                                    if (status.healthChecking)
                                        CircularProgressIndicator(
                                            Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    else Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
            if (snapshot != null) {
                Column(
                    Modifier.padding(horizontal = 20.dp)
                        .padding(top = if (connected) 0.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (connected)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SessionSummary(snapshot, connected)
                }
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
private fun HealthSummaryBadge(summary: HealthSummary, latencyMs: Long) {
    val label =
        when (summary) {
            HealthSummary.PREPARING -> stringResource(R.string.native_health_preparing)
            HealthSummary.CHECKING -> stringResource(R.string.native_health_checking)
            HealthSummary.UNCONFIGURED -> stringResource(R.string.native_health_unconfigured)
            HealthSummary.UNCHECKED -> stringResource(R.string.native_health_unknown)
            HealthSummary.REACHABLE -> stringResource(R.string.native_health_ok, latencyMs)
            HealthSummary.PARTIAL -> stringResource(R.string.native_health_partial)
            HealthSummary.FAILED -> stringResource(R.string.native_health_failed)
        }
    val color by
        animateColorAsState(
            when (summary) {
                HealthSummary.REACHABLE -> MaterialTheme.colorScheme.primary
                HealthSummary.PARTIAL,
                HealthSummary.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            AppMotion.effects(),
        )
    Surface(color = color.copy(alpha = 0.10f), shape = MaterialTheme.shapes.small) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 180.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun NodeHealth(
    protocol: String,
    node: NativeNode?,
    current: NativeNodeHealth?,
    checking: Boolean,
    preparing: Boolean,
    showError: Boolean,
) {
    val waiting = preparing && current?.probeNotReady == true
    val label =
        when {
            node == null -> stringResource(R.string.native_health_unconfigured)
            waiting -> stringResource(R.string.native_health_preparing)
            checking -> stringResource(R.string.native_health_checking)
            current == null -> stringResource(R.string.native_health_unknown)
            current.ok -> stringResource(R.string.native_health_ok, current.latencyMs)
            else -> stringResource(R.string.native_health_failed)
        }
    val color by
        animateColorAsState(
            when {
                current == null || checking || waiting ->
                    MaterialTheme.colorScheme.onSurfaceVariant
                current.ok -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
            AppMotion.effects(),
        )
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
            val failed = current?.ok == false && current.error.isNotBlank()
            if (failed && !checking && !waiting && showError)
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SessionTransfer(
            stringResource(R.string.native_session_download),
            formatBytes(snapshot.session.download),
            Modifier.weight(1f),
        )
        SessionTransfer(
            stringResource(R.string.native_session_upload),
            formatBytes(snapshot.session.upload),
            Modifier.weight(1f),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SessionCount(
            snapshot.session.active.toString(),
            stringResource(
                if (connected) R.string.native_session_active
                else R.string.native_session_last_active
            ),
        )
        SessionCount(snapshot.session.opened, stringResource(R.string.native_session_opened))
        SessionCount(
            snapshot.session.failed,
            stringResource(R.string.native_session_failed),
            MaterialTheme.colorScheme.error.takeIf { snapshot.session.failed != "0" },
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
private fun SessionTransfer(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Value and label share a line, so a long label cannot break the row into uneven columns. */
@Composable
private fun SessionCount(value: String, label: String, valueColor: Color? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

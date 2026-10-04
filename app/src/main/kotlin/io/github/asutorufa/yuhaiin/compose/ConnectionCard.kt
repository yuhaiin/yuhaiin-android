package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State

@Composable
fun ConnectionCard(
    state: State,
    error: String?,
    start: () -> Unit,
    stop: () -> Unit,
    open: () -> Unit,
) {
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
                    when (state) {
                        State.CONNECTED -> R.string.status_connected
                        State.CONNECTING -> R.string.status_connecting
                        State.DISCONNECTING -> R.string.status_disconnecting
                        State.DISCONNECTED -> R.string.status_disconnected
                        State.ERROR -> R.string.status_error
                    }
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
            if (error != null)
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = if (state == State.CONNECTED) stop else start, enabled = !busy) {
                    Text(
                        stringResource(
                            if (state == State.CONNECTED) R.string.Stop else R.string.Connect
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

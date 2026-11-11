package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.R

/**
 * Explains why LAN devices cannot reach this phone while Allow LAN is on (Android 17 blocks LAN
 * TCP without ACCESS_LOCAL_NETWORK). Non-modal and dismissible, so a refusal is never repeated as
 * a dialog; the system dialog itself is only shown from an explicit action.
 */
@Composable
fun LocalNetworkNotice(openSettings: Boolean, onAction: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.local_network_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        if (openSettings) R.string.local_network_body_settings
                        else R.string.local_network_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.local_network_dismiss)) }
                    TextButton(onClick = onAction) {
                        Text(
                            stringResource(
                                if (openSettings) R.string.local_network_open_settings
                                else R.string.local_network_allow
                            )
                        )
                    }
                }
            }
        }
    }
}

package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.data.Settings
import io.github.asutorufa.yuhaiin.service.VpnActions
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State

@Composable
fun RouteShortcutPicker(activity: MainActivity, action: String?) {
    if (action != VpnActions.ROUTES) return
    val values by MainApplication.settings.snapshot.collectAsStateWithLifecycle()
    val dismiss = { activity.navigationAction.value = null }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.adv_route_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                values.setting(Settings.routes).forEach { route ->
                    TextButton(
                        onClick = {
                            MainApplication.settings.set(Settings.route, route)
                            dismiss()
                            if (activity.state.value == State.CONNECTED) activity.reconnect()
                        }
                    ) {
                        Text(routeLabel(route))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

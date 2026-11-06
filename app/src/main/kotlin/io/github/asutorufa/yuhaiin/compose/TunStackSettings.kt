package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.data.Settings

/** Android always runs the lightweight TUN stack; only expose active runtime options. */
@Composable
internal fun TunStackSettings(values: Map<String, Any>) {
    val settings = MainApplication.settings
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.tun_stack_title)) },
            supportingContent = { Text(stringResource(R.string.tun_stack_lightweight_note)) },
            leadingContent = {
                Icon(painterResource(R.drawable.delivery_truck_bolt_24px), null)
            },
        )
        ListPreferenceSetting(
            stringResource(R.string.tun_congestion_title),
            painterResource(R.drawable.speed_24px),
            linkedMapOf("reno" to "Reno", "cubic" to "CUBIC", "bbr" to "BBR"),
            values.setting(Settings.tunCongestionControl),
        ) { settings.set(Settings.tunCongestionControl, it) }
        ListPreferenceSetting(
            stringResource(R.string.tun_loss_recovery_title),
            painterResource(R.drawable.speed_24px),
            linkedMapOf(
                "classic" to stringResource(R.string.tun_loss_recovery_classic),
                "rack-tlp" to "RACK-TLP",
            ),
            values.setting(Settings.tunLossRecovery),
            summary = stringResource(R.string.tun_loss_recovery_note),
        ) { settings.set(Settings.tunLossRecovery, it) }
        ListPreferenceSetting(
            stringResource(R.string.tun_output_scheduler_title),
            painterResource(R.drawable.speed_24px),
            linkedMapOf("fifo" to "FIFO", "fq_codel" to "FQ-CoDel"),
            values.setting(Settings.tunOutputScheduler),
            summary = stringResource(R.string.tun_output_scheduler_note),
        ) { settings.set(Settings.tunOutputScheduler, it) }
    }
}

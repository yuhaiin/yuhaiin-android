package io.github.asutorufa.yuhaiin.compose

import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.data.Settings

/** The stack chooses an implementation, not the persisted device driver. */
@Composable
internal fun TunStackSettings(values: Map<String, Any>) {
    val settings = MainApplication.settings
    val lightweight = values.setting(Settings.tunStack) == "lightweight"
    ListPreferenceSetting(
        stringResource(R.string.tun_stack_title),
        painterResource(R.drawable.delivery_truck_bolt_24px),
        linkedMapOf(
            "" to stringResource(R.string.tun_stack_legacy),
            "lightweight" to stringResource(R.string.tun_stack_lightweight),
        ),
        values.setting(Settings.tunStack),
        summary = if (lightweight) stringResource(R.string.tun_stack_experimental_note) else null,
    ) { settings.set(Settings.tunStack, it) }

    if (lightweight) {
        ListPreferenceSetting(
            stringResource(R.string.tun_congestion_title),
            painterResource(R.drawable.speed_24px),
            linkedMapOf("reno" to "Reno", "cubic" to "CUBIC", "bbr" to "BBR"),
            values.setting(Settings.tunCongestionControl),
        ) { settings.set(Settings.tunCongestionControl, it) }
        ListPreferenceSetting(
            stringResource(R.string.tun_output_scheduler_title),
            painterResource(R.drawable.speed_24px),
            linkedMapOf("fifo" to "FIFO", "fq_codel" to "FQ-CoDel"),
            values.setting(Settings.tunOutputScheduler),
            summary = stringResource(R.string.tun_output_scheduler_note),
        ) { settings.set(Settings.tunOutputScheduler, it) }
        ListItem(
            headlineContent = { Text(stringResource(R.string.adv_tun_driver_title)) },
            supportingContent = { Text(stringResource(R.string.tun_lightweight_driver_note)) },
            leadingContent = {
                Icon(painterResource(R.drawable.delivery_truck_bolt_24px), null)
            },
        )
    } else {
        ListPreferenceSetting(
            stringResource(R.string.adv_tun_driver_title),
            painterResource(R.drawable.delivery_truck_bolt_24px),
            stringArrayResource(R.array.tun_drivers_value)
                .zip(stringArrayResource(R.array.tun_drivers))
                .toMap(),
            values.setting(Settings.tunDriver),
        ) { settings.set(Settings.tunDriver, it) }
    }
}

package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.data.SettingKey
import io.github.asutorufa.yuhaiin.data.Settings
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingCompose(
    vpnState: State,
    stopService: () -> Unit,
    startService: () -> Unit,
    addresses: List<String>,
    onOpenAbout: () -> Unit,
    onOpenAppList: () -> Unit,
    onOpenRouteConfig: () -> Unit,
    onOpenWebView: () -> Unit,
    onOpenLogcat: () -> Unit,
    error: String? = null,
    status: io.github.asutorufa.yuhaiin.service.VpnStatus =
        io.github.asutorufa.yuhaiin.service.VpnStatus(),
    snooze: (Int) -> Unit = {},
) {
    val settings = MainApplication.settings
    val values by settings.snapshot.collectAsStateWithLifecycle()
    val settingsError by settings.failure.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { settings.refresh() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.yuhaiin)) }) }) { padding
        ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp
            Row {
                if (wide)
                    NavigationRail {
                        val destinations =
                            listOf(
                                Triple(R.drawable.apps, R.string.adv_app_list_title, onOpenAppList),
                                Triple(
                                    R.drawable.router,
                                    R.string.route_config_title,
                                    onOpenRouteConfig,
                                ),
                                Triple(R.drawable.adb, R.string.logcat, onOpenLogcat),
                                Triple(R.drawable.handyman, R.string.about, onOpenAbout),
                            )
                        destinations.forEach { (icon, label, action) ->
                            NavigationRailItem(
                                selected = false,
                                onClick = action,
                                icon = { Icon(painterResource(icon), null) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                ReadingPane {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            ConnectionCard(
                                vpnState,
                                error ?: settingsError,
                                startService,
                                stopService,
                                onOpenWebView,
                                status,
                                snooze,
                            )
                        }
                        item {
                            Text(
                                stringResource(R.string.settings_reconnect_hint),
                                Modifier.padding(horizontal = 24.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item { SectionHeading(stringResource(R.string.connection)) }
                        item { PortsInputForm(MainApplication.store, addresses) }
                        item {
                            BooleanPreference(
                                R.string.append_http_proxy_to_vpn_title,
                                Settings.httpProxy,
                                values,
                                R.drawable.http,
                                R.string.append_http_proxy_to_vpn_sum,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.allow_lan_title,
                                Settings.allowLan,
                                values,
                                R.drawable.lan,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.network_speed_title,
                                Settings.speed,
                                values,
                                R.drawable.speed_24px,
                                R.string.network_speed_sum,
                            )
                        }
                        item {
                            TextPreference(
                                R.string.metered_mode,
                                Settings.metered,
                                values,
                                R.array.metered_values,
                                R.array.metered_entries,
                                R.drawable.speed_24px,
                            )
                            Text(
                                stringResource(R.string.metered_hint),
                                Modifier.padding(horizontal = 24.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        item { SectionHeading(stringResource(R.string.route_section)) }
                        item {
                            val routes =
                                values.setting(Settings.routes).associateWith { routeLabel(it) }
                            ListPreferenceSetting(
                                stringResource(R.string.adv_route_title),
                                painterResource(R.drawable.router),
                                routes,
                                values.setting(Settings.route),
                            ) {
                                settings.set(Settings.route, it)
                            }
                        }
                        item {
                            SettingsItem(
                                stringResource(R.string.route_config_title),
                                icon = painterResource(R.drawable.router),
                                onClick = onOpenRouteConfig,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.adv_per_app_title,
                                Settings.perApp,
                                values,
                                R.drawable.settop_component,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.adv_app_bypass_title,
                                Settings.bypass,
                                values,
                                R.drawable.alt_route,
                                R.string.adv_app_bypass_sum,
                            )
                        }
                        item {
                            SettingsItem(
                                stringResource(R.string.adv_app_list_title),
                                stringResource(R.string.adv_app_list_sum),
                                painterResource(R.drawable.apps),
                                onOpenAppList,
                            )
                        }
                        item { SectionHeading(stringResource(R.string.background_battery)) }
                        item {
                            TextPreference(
                                R.string.process_lookup_mode_title,
                                Settings.processLookup,
                                values,
                                R.array.process_lookup_mode_values,
                                R.array.process_lookup_mode_entries,
                                R.drawable.person,
                            )
                        }
                        item {
                            TextPreference(
                                R.string.vpn_mtu_profile_title,
                                Settings.mtu,
                                values,
                                R.array.vpn_mtu_profile_values,
                                R.array.vpn_mtu_profile_entries,
                                R.drawable.speed_24px,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.register_underlying_network_callback_title,
                                Settings.trackNetwork,
                                values,
                                R.drawable.hub,
                                R.string.register_underlying_network_callback_summary,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.adv_auto_connect_title,
                                Settings.autoConnect,
                                values,
                                R.drawable.auto_mode,
                            )
                        }
                        item {
                            TextPreference(
                                R.string.boot_connect_policy_title,
                                Settings.bootPolicy,
                                values,
                                R.array.boot_connect_policy_values,
                                R.array.boot_connect_policy_entries,
                                R.drawable.auto_mode,
                            )
                        }
                        item { SectionHeading(stringResource(R.string.advanced)) }
                        item {
                            TextPreference(
                                R.string.adv_tun_driver_title,
                                Settings.tunDriver,
                                values,
                                R.array.tun_drivers_value,
                                R.array.tun_drivers,
                                R.drawable.delivery_truck_bolt_24px,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.sniff_title,
                                Settings.sniff,
                                values,
                                R.drawable.spoke,
                            )
                        }
                        item {
                            BooleanPreference(
                                R.string.dns_dns_hijacking_title,
                                Settings.dnsHijacking,
                                values,
                                R.drawable.dns,
                            )
                        }
                        item { SectionHeading(stringResource(R.string.debug)) }
                        item {
                            SettingsItem(
                                stringResource(R.string.logcat),
                                icon = painterResource(R.drawable.adb),
                                onClick = onOpenLogcat,
                            )
                        }
                        item {
                            SettingsItem(
                                stringResource(R.string.about),
                                stringResource(R.string.about_summary),
                                painterResource(R.drawable.handyman),
                                onOpenAbout,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BooleanPreference(
    title: Int,
    key: SettingKey<Boolean>,
    values: Map<String, Any>,
    icon: Int,
    summary: Int? = null,
) {
    SwitchSetting(
        stringResource(title),
        summary?.let { stringResource(it) },
        painterResource(icon),
        values.setting(key),
    ) {
        MainApplication.settings.set(key, it)
    }
}

@Composable
private fun TextPreference(
    title: Int,
    key: SettingKey<String>,
    values: Map<String, Any>,
    entryKeys: Int,
    entryLabels: Int,
    icon: Int,
) {
    val entries = stringArrayResource(entryKeys).zip(stringArrayResource(entryLabels)).toMap()
    ListPreferenceSetting(
        stringResource(title),
        painterResource(icon),
        entries,
        values.setting(key),
    ) {
        MainApplication.settings.set(key, it)
    }
}

@Composable
fun routeLabel(id: String): String =
    when (id) {
        Constants.ALL_ROUTE -> stringResource(R.string.route_all_label)
        Constants.NON_LOCAL_ROUTE -> stringResource(R.string.route_non_local_label)
        Constants.NON_CHINESE_ROUTE -> stringResource(R.string.route_non_chinese_label)
        else -> id
    }

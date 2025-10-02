package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.logviewer.LogcatActivity
import yuhaiin.Store

@SuppressLint("ContextCastToActivity")
@Composable
@Preview
fun SettingCompose(
    modifier: Modifier = Modifier,
    navController: NavController? = null,
    store: Store? = null,
) {
    val context = LocalContext.current as Activity

    var appendHttpProxy by rememberSaveable {
        mutableStateOf(store?.getBoolean("append_http_proxy_to_vpn") ?: false)
    }
    var allowLan by rememberSaveable { mutableStateOf(store?.getBoolean("allow_lan") ?: false) }
    var networkSpeed by rememberSaveable {
        mutableStateOf(
            store?.getBoolean("network_speed") ?: false
        )
    }
    var tunDriver by rememberSaveable {
        mutableStateOf(store?.getString("Tun Driver"))
    }
    var route by rememberSaveable {
        mutableStateOf(store?.getString("route"))
    }
    var sniff by rememberSaveable { mutableStateOf(store?.getBoolean("Sniff") ?: false) }
    var dnsHijacking by rememberSaveable {
        mutableStateOf(
            store?.getBoolean("dns_hijacking") ?: false
        )
    }
    var autoConnect by rememberSaveable {
        mutableStateOf(
            store?.getBoolean("auto_connect") ?: false
        )
    }
    var perApp by rememberSaveable { mutableStateOf(store?.getBoolean("per_app") ?: false) }
    var appBypass by rememberSaveable { mutableStateOf(store?.getBoolean("app_bypass") ?: false) }
    var httpProxyPort by rememberSaveable { mutableIntStateOf(store?.getInt("http_port") ?: 0) }
    var yuhaiinPort by rememberSaveable { mutableIntStateOf(store?.getInt("yuhaiin_port") ?: 0) }


    LazyColumn(modifier = modifier.fillMaxSize()) {
        // ---- Connection ----
        item {
            Text(
                text = stringResource(R.string.connection),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            PortsInputForm(httpProxyPort, yuhaiinPort) {
                httpProxyPort = it
                store?.putInt("http_port", it)
            }
        }
        item {
            SwitchSetting(
                title = stringResource(R.string.append_http_proxy_to_vpn_title),
                summary = "HTTP proxy will be used directly from without going through the virtual NIC device(Android 10+)",
                checked = appendHttpProxy,
                onCheckedChange = {
                    appendHttpProxy = it
                    store?.putBoolean("append_http_proxy_to_vpn", it)
                }
            )
        }
        item {
            SwitchSetting(
                title = stringResource(R.string.allow_lan_title),
                icon = painterResource(R.drawable.lan),
                checked = allowLan,
                onCheckedChange = {
                    allowLan = it
                    store?.putBoolean("allow_lan", it)
                }
            )
        }
        item {
            SwitchSetting(
                title = stringResource(R.string.network_speed_title),
                summary = stringResource(R.string.network_speed_sum),
                icon = painterResource(R.drawable.speed_24px),
                checked = networkSpeed,
                onCheckedChange = {
                    networkSpeed = it
                    store?.putBoolean("network_speed", it)
                }
            )
        }

        // ---- Advanced ----
        item {
            Text(
                text = stringResource(R.string.advanced),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            ListPreferenceSetting(
                title = stringResource(R.string.adv_tun_driver_title),
                icon = painterResource(R.drawable.handyman),
                entries = stringArrayResource(R.array.tun_drivers),
                selected = tunDriver ?: stringResource(R.string.tun_driver_fdbased),
                onSelectedChange = {
                    tunDriver = it
                    store?.putString("Tun Driver", it)
                }
            )
        }
        item {
            ListPreferenceSetting(
                title = stringResource(R.string.adv_route_title),
                icon = painterResource(R.drawable.router),
                entries = stringArrayResource(R.array.adv_routes),
                selected = route ?: stringResource(R.string.adv_route_all),
                onSelectedChange = {
                    route = it
                    store?.putString("route", it)
                }
            )
        }
        item {
            SwitchSetting(
                title = stringResource(R.string.sniff_title),
                icon = painterResource(R.drawable.router),
                checked = sniff,
                onCheckedChange = {
                    sniff = it
                    store?.putBoolean("Sniff", it)
                }
            )
        }
        item {
            SwitchSetting(
                title = stringResource(R.string.dns_dns_hijacking_title),
                checked = dnsHijacking,
                onCheckedChange = {
                    dnsHijacking = it
                    store?.putBoolean("dns_hijacking", it)
                }
            )
        }
        item {
            SwitchSetting(
                title = stringResource(R.string.adv_auto_connect_title),
                icon = painterResource(R.drawable.auto_mode),
                checked = autoConnect,
                onCheckedChange = {
                    autoConnect = it
                    store?.putBoolean("auto_connect", it)
                }
            )
        }
        item {
            SwitchSetting(
                title = stringResource(R.string.adv_per_app_title),
                icon = painterResource(R.drawable.settop_component),
                checked = perApp,
                onCheckedChange = {
                    perApp = it
                    store?.putBoolean("per_app", it)
                }
            )
        }
        if (perApp) {
            item {
                SwitchSetting(
                    title = stringResource(R.string.adv_app_bypass_title),
                    summary = stringResource(R.string.adv_app_bypass_sum),
                    icon = painterResource(R.drawable.alt_route),
                    checked = appBypass,
                    onCheckedChange = {
                        appBypass = it
                        store?.putBoolean("app_bypass", it)
                    }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.adv_app_list_title),
                    summary = stringResource(R.string.adv_app_list_sum),
                    icon = painterResource(R.drawable.apps),
                    onClick = { navController?.navigate("APPLIST") }
                )
            }
        }

        // ---- Debug ----
        item {
            Text(
                text = stringResource(R.string.debug),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            SettingsItem(
                title = stringResource(R.string.logcat_name),
                icon = painterResource(R.drawable.adb),
                onClick = {

                    val logcatExcludeRules = arrayListOf(
                        ".*]: processMotionEvent MotionEvent \\{ action=ACTION_.*",
                        ".*]: dispatchPointerEvent handled=true, event=MotionEvent \\{ action=ACTION_.*",
                        ".*Davey! duration=.*",
                        // android popup window select text debug log
                        ".*Attempted to finish an input event but the input event receiver has already been disposed.*",
                        ".*endAllActiveAnimators on .* with handle.*",
                        ".*Initializing SystemTextClassifier,.*",
                        ".*TextClassifier called on main thread.*",
                        ".*android added item .*",
                        ".*No package ID .* found for ID.*",
                        ".*eglMakeCurrent:.*",
                        ".*NotificationManager: io.github.asutorufa.yuhaiin: notify.*",
                        ".*InputEventReceiver_DOT: IER.scheduleInputVsync.*",
                        ".*ViewRootImpl@.*[.*]: .*"
                    )
                    context.startActivity(
                        LogcatActivity.intent(logcatExcludeRules, context)
                    )
                }
            )
        }
    }
}

@Composable
@Preview
fun SettingsItem(
    title: String = "Test Item",
    summary: String? = null,
    icon: Painter? = rememberVectorPainter(Icons.Filled.Settings),
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
@Preview
fun SwitchSetting(
    title: String = "Test Switch",
    summary: String? = null,
    icon: Painter? = rememberVectorPainter(Icons.Filled.Settings),
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
@Preview
fun ListPreferenceSetting(
    title: String = "Preview Title",
    summary: String? = null,
    icon: Painter? = null,
    entries: Array<String> = Array(5) { i ->
        return@Array "Preview Entries $i"
    },
    selected: String = "",
    onSelectedChange: (String) -> Unit = {}
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    SettingsItem(
        title = title,
        summary = summary ?: selected,
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                }) {
                    Text("Close")
                }
            },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEachIndexed { index, entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectedChange(entry)
                                    showDialog = false
                                }
                        ) {
                            RadioButton(
                                selected = entry == selected,
                                onClick = {}
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                entry,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun PortsInputForm(
    httpHost: Int = 1080,
    yuhaiinHost: Int = 50051,
    onConfirmed: (host: Int) -> Unit = {},
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var http by rememberSaveable { mutableStateOf(httpHost) }

    SettingsItem(
        title = "Ports",
        icon = painterResource(R.drawable.vpn_lock),
        onClick = { showDialog = true }
    )


    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = stringResource(R.string.ports_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = http.toString(),
                        onValueChange = { it ->
                            if (it.all { it.isDigit() }) {
                                val number = it.toIntOrNull()
                                if (number == null || number in 0..65535) {
                                    http = it.toInt()
                                }
                            }
                        },
                        label = { Text("HTTP & SOCKS5") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = yuhaiinHost.toString(),
                        onValueChange = { },
                        label = { Text("YUHAIIN") },
                        singleLine = true,
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmed(http)
                        showDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
        )
    }
}

package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import yuhaiin.Store

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@SuppressLint("ContextCastToActivity")
@Composable
@Preview
fun SharedTransitionScope.SettingCompose(
    navController: NavController? = null,
    store: Store? = null,
    animatedContentScope: AnimatedContentScope? = null,
    startService: () -> Unit = {},
    stopService: () -> Unit = {},
    vpnState: State = State.DISCONNECTED,
) {
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


    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

//    val focusRequester =  FocusRequester()
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }
    val blur = remember { Animatable(0f) }

    LaunchedEffect(fabMenuExpanded) {
        if (fabMenuExpanded) blur.animateTo(30f, tween(400))
        else blur.animateTo(0f, tween(400))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(stringResource(R.string.yuhaiin))
                }
            )
        },
        floatingActionButtonPosition = FabPosition.EndOverlay,
        floatingActionButton = {
            FloatingActionButtonMenu(
                modifier = if (animatedContentScope != null)
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("OPEN_LOGCAT_FAB"),
                        animatedVisibilityScope = animatedContentScope,
                    )
                else Modifier,
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded }
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                }
            ) {
                val rotation by animateFloatAsState(targetValue = if (vpnState == State.CONNECTED) 90f else 0f)

                if (vpnState == State.CONNECTED) {
                    FloatingActionButtonMenuItem(
                        modifier = if (animatedContentScope != null)
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_WEBVIEW"),
                                animatedVisibilityScope = animatedContentScope,
                            )
                        else Modifier
                            .semantics { isTraversalGroup = true },
                        onClick = {
                            navController?.navigate("WebView")
                        },
                        text = { Text(text = "Open") },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.open_in_browser),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                    )
                }

                if (vpnState == State.CONNECTED || vpnState == State.DISCONNECTED)
                    FloatingActionButtonMenuItem(
                        modifier = Modifier.semantics {
                            isTraversalGroup = true
                        },
                        onClick = {
                            if (vpnState == State.DISCONNECTED) startService()
                            else stopService()
                        },
                        text = {
                            Icon(
                                painter = painterResource(
                                    if (vpnState == State.CONNECTED)
                                        R.drawable.stop else R.drawable.play_arrow
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(30.dp)
                                    .rotate(rotation)
                            )
                            Text(
                                text = if (vpnState == State.CONNECTED) "Stop" else "Start"
                            )

                        },
                        icon = {}
                    )
                else ContainedLoadingIndicator(
                    modifier = Modifier.size(50.dp)
                )
            }
        },
        content = { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                            Modifier.background(
                                Color.Black.copy(
                                    alpha = if (fabMenuExpanded) 0.5f else 0f
                                )
                            )
                        else Modifier
                    )
                    .graphicsLayer {
                        renderEffect =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur.value > 0) RenderEffect.createBlurEffect(
                                blur.value, blur.value, Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                            else null
                    }
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.connection),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
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
                        entries = stringArrayResource(R.array.tun_drivers_value).zip(
                            stringArrayResource(R.array.tun_drivers)
                        ).toMap(),
                        selected = tunDriver ?: stringResource(R.string.tun_driver_fdbased_value),
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
                        entries = stringArrayResource(R.array.adv_routes).associateWith { it },
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
                            modifier = if (animatedContentScope != null) Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_APP_LIST_APP"),
                                animatedVisibilityScope = animatedContentScope,
                            ) else Modifier,
                            textModifier = if (animatedContentScope != null) Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_APP_LIST_TITLE"),
                                animatedVisibilityScope = animatedContentScope,
                            ) else Modifier,
                            iconModifier = if (animatedContentScope != null) Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_APP_LIST_ICON"),
                                animatedVisibilityScope = animatedContentScope,
                            ) else Modifier,
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
                        onClick = { navController?.navigate("LOGCAT") }
                    )
                }
            }

            if (fabMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            fabMenuExpanded = false
                        }
                )
            }
        })
}

@Composable
@Preview
fun SettingsItem(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    textColumnModifier: Modifier = Modifier,
    title: String = "Test Item",
    summary: String? = null,
    icon: Painter? = rememberVectorPainter(Icons.Filled.Settings),
    onClick: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = iconModifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(textColumnModifier.weight(1f)) {
            Text(
                modifier = textModifier,
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ListPreferenceSetting(
    title: String? = null,
    icon: Painter? = null,
    entries: Map<String, String> = hashMapOf(
        "one" to "1",
        "two" to "2",
        "three" to "3"
    ),
    selected: String = "",
    onSelectedChange: (String) -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        true
                    ),
                label = { if (title != null) Text(title) },
                value = entries[selected] ?: "NotSelect",
                onValueChange = { },
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                leadingIcon = {
                    if (icon != null) Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                for ((key, value) in entries) {
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onSelectedChange(key)
                        },
                        text = {
                            Text(value)
                        }
                    )
                }
            }
        }
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
    var http by rememberSaveable { mutableIntStateOf(httpHost) }

    SettingsItem(
        title = "Ports",
        icon = painterResource(R.drawable.vpn_lock),
        onClick = { showDialog = true },
    )

    AnimatedVisibility(
        visible = showDialog,
        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut()
    ) {
        AlertDialog(
            modifier = Modifier.animateEnterExit(
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ),
            onDismissRequest = { showDialog = false },
            title = {
                Text(text = stringResource(R.string.ports_title))
            },
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

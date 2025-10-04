package io.github.asutorufa.yuhaiin.compose

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import yuhaiin.Store

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
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
    var tunDriver by rememberSaveable { mutableStateOf(store?.getString("Tun Driver")) }
    var route by rememberSaveable { mutableStateOf(store?.getString("route")) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }
    val blur = remember { Animatable(0f) }

    LaunchedEffect(fabMenuExpanded) {
        if (fabMenuExpanded) blur.animateTo(30f, tween(400))
        else blur.animateTo(0f, tween(400))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(stringResource(R.string.yuhaiin))
                }
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButtonMenu(
                modifier = Modifier.thenIfNotNull(animatedContentScope) {
                    sharedBounds(
                        sharedContentState = rememberSharedContentState("OPEN_LOGCAT_FAB"),
                        animatedVisibilityScope = it,
                    )
                },
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded }
                    ) {
                        val imageVector by remember {
                            derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add }
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
                        modifier = Modifier
                            .thenIfNotNull(animatedContentScope) {
                                sharedBounds(
                                    sharedContentState = rememberSharedContentState("OPEN_WEBVIEW"),
                                    animatedVisibilityScope = it,
                                )
                            }
                            .semantics { isTraversalGroup = true },
                        onClick = {
                            navController?.navigate("WebView")
                        },
                        text = { Text(text = stringResource(R.string.Open)) },
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
                                text = if (vpnState == State.CONNECTED) stringResource(R.string.Stop) else stringResource(
                                    R.string.Connect
                                )
                            )

                        },
                        icon = {}
                    )
                else ContainedLoadingIndicator(
                    modifier = Modifier.size(60.dp)
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
                    PortsInputForm(store)
                }
                item {
                    SwitchStore(
                        title = R.string.append_http_proxy_to_vpn_title,
                        summary = R.string.append_http_proxy_to_vpn_sum,
                        store = store,
                        storeKey = "append_http_proxy_to_vpn",
                    )
                }
                item {
                    SwitchStore(
                        title = R.string.allow_lan_title,
                        icon = R.drawable.lan,
                        store = store,
                        storeKey = "allow_lan",
                    )
                }
                item {
                    SwitchStore(
                        title = R.string.network_speed_title,
                        summary = R.string.network_speed_sum,
                        icon = R.drawable.speed_24px,
                        store = store,
                        storeKey = "network_speed",
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
                    SwitchStore(
                        title = R.string.sniff_title,
                        icon = R.drawable.router,
                        store = store,
                        storeKey = "sniff",
                    )
                }
                item {
                    SwitchStore(
                        title = R.string.dns_dns_hijacking_title,
                        icon = R.drawable.router,
                        store = store,
                        storeKey = "dns_hijacking",
                    )
                }
                item {
                    SwitchStore(
                        title = R.string.adv_auto_connect_title,
                        icon = R.drawable.auto_mode,
                        store = store,
                        storeKey = "auto_connect",
                    )
                }
                item {
                    SwitchStore(
                        title = R.string.adv_per_app_title,
                        icon = R.drawable.settop_component,
                        store = store,
                        storeKey = "per_app",
                    )
                }
                item {
                    SwitchStore(
                        title = R.string.adv_app_bypass_title,
                        summary = R.string.adv_app_bypass_sum,
                        icon = R.drawable.alt_route,
                        store = store,
                        storeKey = "app_bypass",
                    )
                }
                item {
                    SettingsItem(
                        textColumnModifier = Modifier.thenIfNotNull(animatedContentScope) {
                            sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_APP_LIST_TITLE"),
                                animatedVisibilityScope = it,
                            )
                        },
                        iconModifier = Modifier.thenIfNotNull(animatedContentScope) {
                            sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_APP_LIST_ICON"),
                                animatedVisibilityScope = it,
                            )
                        },
                        title = stringResource(R.string.adv_app_list_title),
                        summary = stringResource(R.string.adv_app_list_sum),
                        icon = painterResource(R.drawable.apps),
                        onClick = { navController?.navigate("APPLIST") }
                    )
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
fun SwitchStore(
    @StringRes title: Int = R.string.app_name,
    @StringRes summary: Int? = null,
    @DrawableRes icon: Int? = null,
    store: Store? = null,
    storeKey: String = "",
) {
    var checked by rememberSaveable { mutableStateOf(store?.getBoolean(storeKey) ?: false) }

    SwitchSetting(
        title = stringResource(title),
        icon = if (icon != null) painterResource(icon) else null,
        summary = if (summary != null) stringResource(summary) else null,
        checked = checked,
        onCheckedChange = {
            checked = it
            store?.putBoolean(storeKey, it)
        }
    )
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
                        text = { Text(value) },
                        trailingIcon = {
                            if (selected == key) Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = key,
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
fun PortsInputForm(
    store: Store? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }


    SettingsItem(
        title = "Ports",
        icon = painterResource(R.drawable.vpn_lock),
        onClick = { showBottomSheet = true },
    )

    if (showBottomSheet) {
        var http by rememberSaveable { mutableIntStateOf(store?.getInt("http_port") ?: 0) }
        var yuhaiin by rememberSaveable {
            mutableIntStateOf(
                store?.getInt("yuhaiin_port") ?: 0
            )
        }

        ModalBottomSheet(
            onDismissRequest = {
                store?.putInt("http_port", http)
                showBottomSheet = false
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.3f)
                    .padding(horizontal = 16.dp)
            ) {
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
                    value = yuhaiin.toString(),
                    onValueChange = { },
                    label = { Text("YUHAIIN") },
                    singleLine = true,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                )
            }
        }
    }
}

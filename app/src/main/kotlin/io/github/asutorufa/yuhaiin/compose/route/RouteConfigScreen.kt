package io.github.asutorufa.yuhaiin.compose.route

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.compose.*
import io.github.asutorufa.yuhaiin.data.RouteRepository
import io.github.asutorufa.yuhaiin.data.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.RouteConfigScreen(
    animatedContentScope: AnimatedContentScope?,
    onBack: () -> Unit,
    onOpenRouteEdit: (String) -> Unit,
) {
    val values by MainApplication.settings.snapshot.collectAsStateWithLifecycle()
    val routes = values.setting(Settings.routes).toList().sorted()
    val active = values.setting(Settings.route)
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDetail by rememberSaveable { mutableStateOf<String?>(null) }
    var leaveDetail by rememberSaveable { mutableStateOf(false) }
    val revision by RouteRepository.revision.collectAsStateWithLifecycle()
    val detailModel = detail?.let { routeDraftModel(it) }
    val detailFlow =
        remember(detailModel) {
            detailModel?.draft ?: kotlinx.coroutines.flow.MutableStateFlow(RouteDraft())
        }
    val dirtyDetail by detailFlow.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { MainApplication.settings.refresh() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.route_config_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (dirtyDetail.dirty) leaveDetail = true else onBack() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.route_config_add_route))
                    }
                },
            )
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        BoxWithConstraints(Modifier.fillMaxSize().scaffoldBody(padding)) {
            val wide = maxWidth >= 840.dp
            Row {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = bottom + 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.route_current, routeLabel(active)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (error != null)
                        item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                    items(routes, key = { it }) { name ->
                        val count by
                            produceState<Int?>(null, name, revision) {
                                value =
                                    RouteRepository.content(name).lineSequence().count {
                                        it.isNotBlank()
                                    }
                            }
                        val dismiss = rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismiss.currentValue) {
                            if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                delete = name
                                dismiss.reset()
                            }
                        }
                        val countAlpha by animateFloatAsState(if (count == null) 0f else 1f)
                        val container by
                            animateColorAsState(
                                if (wide && name == detail)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                AppMotion.effects(),
                            )
                        SwipeToDismissBox(
                            state = dismiss,
                            modifier = Modifier.animateItem().clip(MaterialTheme.shapes.large),
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart =
                                name !in RouteRepository.presets && delete == null,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete))
                                }
                            },
                        ) {
                            ListItem(
                                modifier =
                                    Modifier.clickable {
                                        if (wide) {
                                            if (name != detail && dirtyDetail.dirty)
                                                pendingDetail = name
                                            else detail = name
                                        } else onOpenRouteEdit(name)
                                    },
                                colors = ListItemDefaults.colors(containerColor = container),
                                headlineContent = {
                                    Text(
                                        routeLabel(name),
                                        modifier =
                                            if (!wide && animatedContentScope != null)
                                                Modifier.sharedBounds(
                                                    rememberSharedContentState("ROUTE_NAME_$name"),
                                                    animatedContentScope,
                                                )
                                            else Modifier,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            if (name in RouteRepository.presets)
                                                R.string.route_preset_count
                                            else R.string.route_custom_count,
                                            count ?: 0,
                                        ),
                                        // Hidden, not absent, so loading does not resize the row.
                                        Modifier.graphicsLayer { alpha = countAlpha },
                                    )
                                },
                                leadingContent = { Icon(painterResource(R.drawable.router), null) },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (active == name)
                                            Icon(
                                                Icons.Default.Check,
                                                stringResource(R.string.route_selected),
                                            )
                                        else
                                            TextButton(
                                                onClick = {
                                                    MainApplication.settings.set(
                                                        Settings.route,
                                                        name,
                                                    )
                                                }
                                            ) {
                                                Text(stringResource(R.string.use_route))
                                            }
                                        // Swiping alone is hard to find and unusable with TalkBack.
                                        if (name !in RouteRepository.presets)
                                            IconButton(onClick = { delete = name }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    stringResource(R.string.delete),
                                                )
                                            }
                                    }
                                },
                            )
                        }
                    }
                }
                if (wide) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Crossfade(
                        detail,
                        Modifier.weight(1.3f).fillMaxHeight().padding(bottom = bottom),
                        label = "route detail",
                    ) { name ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (name != null)
                                RouteEditScreen(
                                    name,
                                    null,
                                    onBack = { detail = null },
                                    embedded = true,
                                )
                            else Text(stringResource(R.string.route_choose_detail))
                        }
                    }
                }
            }
        }
    }
    if (pendingDetail != null || leaveDetail)
        AlertDialog(
            onDismissRequest = {
                pendingDetail = null
                leaveDetail = false
            },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            detailModel?.discard()
                            if (leaveDetail) onBack() else detail = pendingDetail
                            pendingDetail = null
                            leaveDetail = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDetail = null
                        leaveDetail = false
                    }
                ) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    if (delete != null)
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text(stringResource(R.string.route_config_delete_confirm_title)) },
            text = { Text(stringResource(R.string.route_config_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = delete ?: return@TextButton
                        scope.launch {
                            runCatching { RouteRepository.delete(name) }
                                .onSuccess {
                                    if (detail == name) {
                                        detailModel?.discard()
                                        detail = null
                                    }
                                }
                                .onFailure { error = it.message }
                            delete = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { delete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    if (showAdd) {
        var name by rememberSaveable { mutableStateOf("") }
        val valid =
            name.isNotBlank() &&
                name.length <= 80 &&
                name !in routes &&
                name.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.route_config_add_route)) },
            text = {
                OutlinedTextField(
                    name,
                    { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.route_config_name_hint)) },
                    isError = name.isNotEmpty() && !valid,
                    supportingText = {
                        if (name.isNotEmpty() && !valid)
                            Text(
                                stringResource(
                                    if (name in routes) R.string.route_name_exists
                                    else R.string.route_config_error_name_invalid
                                )
                            )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        scope.launch {
                            runCatching { RouteRepository.create(name) }
                                .onSuccess {
                                    showAdd = false
                                    onOpenRouteEdit(name)
                                }
                                .onFailure { error = it.message }
                        }
                    },
                ) {
                    Text(stringResource(R.string.route_config_add_route))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

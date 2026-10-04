package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.data.InstalledApp
import io.github.asutorufa.yuhaiin.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListComponent(onBack: () -> Unit) {
    val repository = MainApplication.installedApps
    val settings by MainApplication.settings.snapshot.collectAsStateWithLifecycle()
    val selected = settings.setting(Settings.applications)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        error = null
        runCatching { repository.load(reload > 0) }
            .onSuccess { apps = it }
            .onFailure { error = it.message }
    }
    val visible =
        remember(apps, query, filter, selected) {
            apps.orEmpty().filter { app ->
                (filter != "selected" || app.packageName in selected) &&
                    (filter != "system" || app.system) &&
                    (filter != "user" || !app.system) &&
                    (app.name.contains(query, true) || app.packageName.contains(query, true))
            }
        }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adv_app_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { reload++ }) {
                        Icon(
                            painterResource(R.drawable.refresh_24px),
                            stringResource(R.string.refresh),
                        )
                    }
                },
            )
        }
    ) { padding ->
        ReadingPane(Modifier.padding(padding)) {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    trailingIcon = {
                        if (query.isNotEmpty())
                            TextButton(onClick = { query = "" }) {
                                Text(stringResource(R.string.clear))
                            }
                    },
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                            "all" to R.string.apps_all,
                            "user" to R.string.apps_user,
                            "selected" to R.string.apps_selected,
                            "system" to R.string.apps_system,
                        )
                        .forEach { (id, label) ->
                            FilterChip(
                                selected = filter == id,
                                onClick = { filter = id },
                                label = { Text(stringResource(label)) },
                            )
                        }
                }
                Text(
                    stringResource(R.string.apps_selection_summary, selected.size, visible.size),
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_reconnect_hint),
                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    error != null ->
                        Column(Modifier.padding(24.dp)) {
                            Text(error!!, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                        }
                    apps == null ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    visible.isEmpty() ->
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                stringResource(R.string.apps_no_results),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            TextButton(
                                onClick = {
                                    query = ""
                                    filter = "all"
                                }
                            ) {
                                Text(stringResource(R.string.reset_filters))
                            }
                        }
                    else ->
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(visible, key = { it.packageName }) { app ->
                                val checked = app.packageName in selected
                                val icon by
                                    produceState<android.graphics.Bitmap?>(null, app.packageName) {
                                        value = repository.icon(app.packageName)
                                    }
                                ListItem(
                                    modifier =
                                        Modifier.toggleable(checked, role = Role.Checkbox) { enabled
                                            ->
                                            val next =
                                                selected.toMutableSet().apply {
                                                    if (enabled) add(app.packageName)
                                                    else remove(app.packageName)
                                                }
                                            MainApplication.settings.set(
                                                Settings.applications,
                                                next,
                                            )
                                        },
                                    colors =
                                        ListItemDefaults.colors(
                                            containerColor =
                                                if (checked)
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surface
                                        ),
                                    headlineContent = {
                                        Text(
                                            app.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            app.packageName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = {
                                        Box(Modifier.size(40.dp)) {
                                            icon?.let {
                                                Image(
                                                    it.asImageBitmap(),
                                                    null,
                                                    Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = { Checkbox(checked, onCheckedChange = null) },
                                )
                            }
                        }
                }
            }
        }
    }
}

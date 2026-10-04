package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.logging.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatCompose(excludeList: ArrayList<String>? = null, onBack: () -> Unit) {
    val model: io.github.asutorufa.yuhaiin.logging.LogViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val buffer = model.buffer
    val logs by model.entries.collectAsStateWithLifecycle()
    var paused by rememberSaveable { mutableStateOf(false) }
    val currentlyPaused by rememberUpdatedState(paused)
    var follow by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(LogLevel.DEBUG) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<LogEntry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val list = rememberLazyListState()
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            runCatching {
                readLogcat(excludeList.orEmpty()) { batch ->
                    val current = buffer.append(batch)
                    if (!currentlyPaused) model.publish(current)
                }
            }
                .onFailure {
                    if (it !is kotlinx.coroutines.CancellationException) error = it.message
                }
        }
    }
    val visible =
        remember(logs, filter, query) {
            logs.filter {
                it.level.enabled(filter) &&
                    (it.content.contains(query, true) || it.tag.contains(query, true))
            }
        }
    LaunchedEffect(visible.lastOrNull()?.id, follow, paused) {
        if (follow && !paused && visible.isNotEmpty()) list.scrollToItem(visible.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logcat)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            paused = !paused
                            if (!paused) model.publish(buffer.snapshot())
                        }
                    ) {
                        Icon(
                            painterResource(
                                if (paused) R.drawable.play_arrow else R.drawable.pause
                            ),
                            stringResource(if (paused) R.string.resume else R.string.pause),
                        )
                    }
                    IconButton(
                        onClick = {
                            buffer.clear()
                            model.publish(emptyList())
                        }
                    ) {
                        Icon(painterResource(R.drawable.clear_all), stringResource(R.string.clear))
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatching { exportLogs(context, visible) }
                                    .onFailure { error = it.message }
                            }
                        }
                    ) {
                        Icon(painterResource(R.drawable.save), stringResource(R.string.export_logs))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilterChip(
                        follow,
                        onClick = { follow = !follow },
                        label = { Text(stringResource(R.string.follow_logs)) },
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (visible.isNotEmpty())
                                    list.animateScrollToItem(visible.lastIndex)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.latest_logs))
                    }
                }
            }
        },
    ) { padding ->
        ReadingPane(Modifier.padding(padding)) {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(stringResource(R.string.search_logs)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                )
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogLevel.entries.forEach { level ->
                        FilterChip(
                            filter == level,
                            onClick = { filter = level },
                            label = { Text(level.tag) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.log_count, visible.size, logs.size),
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (error != null)
                    Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                if (visible.isEmpty())
                    Text(stringResource(R.string.logs_empty), Modifier.padding(24.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = list,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(visible, key = { it.id }) { entry ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { selected = entry }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color =
                                        when (entry.level) {
                                            LogLevel.ERROR ->
                                                MaterialTheme.colorScheme.errorContainer
                                            LogLevel.WARN ->
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            else ->
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text(
                                        entry.level.tag.first().toString(),
                                        Modifier.padding(horizontal = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Text(
                                    "${entry.time}  ${entry.tag}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                entry.content,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                maxLines = 5,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    selected?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SelectionContainer {
                Text(
                    entry.line(),
                    Modifier.heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

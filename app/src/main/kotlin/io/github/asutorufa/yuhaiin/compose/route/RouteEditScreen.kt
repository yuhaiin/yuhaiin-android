package io.github.asutorufa.yuhaiin.compose.route

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.compose.copyText
import io.github.asutorufa.yuhaiin.compose.routeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.RouteEditScreen(
    routeName: String,
    animatedContentScope: AnimatedContentScope?,
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val tooLarge = stringResource(R.string.route_too_large)
    val model = routeDraftModel(routeName)
    val draft by model.draft.collectAsStateWithLifecycle()
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var importingError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val requestBack = { if (draft.dirty) confirmExit = true else onBack() }
    BackHandler(enabled = draft.dirty) { confirmExit = true }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)!!.use {
                                val bytes = java.io.ByteArrayOutputStream()
                                val chunk = ByteArray(8192)
                                while (true) {
                                    val size = it.read(chunk)
                                    if (size < 0) break
                                    require(bytes.size() + size <= MAX_ROUTE_BYTES) { tooLarge }
                                    bytes.write(chunk, 0, size)
                                }
                                bytes.toString("UTF-8")
                            }
                        }
                    }
                        .onSuccess { model.edit(it) }
                        .onFailure { importingError = it.message }
                }
        }
    Scaffold(
        contentWindowInsets =
            if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                windowInsets =
                    if (embedded) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                title = {
                    Text(
                        routeLabel(routeName),
                        modifier =
                            if (animatedContentScope != null)
                                Modifier.sharedBounds(
                                    rememberSharedContentState("ROUTE_NAME_$routeName"),
                                    animatedContentScope,
                                )
                            else Modifier,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = draft.valid && !draft.saving,
                        onClick = {
                            scope.launch { if (model.save()) onBack() }
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = draft.loaded && !draft.saving,
                    onClick = { import.launch(arrayOf("text/*", "application/octet-stream")) },
                ) {
                    Text(stringResource(R.string.import_text))
                }
                TextButton(
                    enabled = draft.loaded && !draft.saving,
                    onClick = { copyText(context, draft.text) },
                ) {
                    Text(stringResource(R.string.copy))
                }
                TextButton(enabled = draft.loaded && !draft.saving, onClick = { model.edit("") }) {
                    Text(stringResource(R.string.clear))
                }
            }
            Text(
                stringResource(R.string.route_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (draft.validating) LinearProgressIndicator(Modifier.fillMaxWidth())
            val error = draft.failure ?: importingError
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            draft.invalidLine?.let {
                Text(
                    stringResource(R.string.route_invalid_line, it),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                draft.text,
                model::edit,
                enabled = draft.loaded && !draft.saving,
                label = { Text(stringResource(R.string.route_config_content_hint)) },
                isError = draft.invalidLine != null,
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
            )
        }
    }
    if (confirmExit)
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            model.discard()
                            confirmExit = false
                            onBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
}

@Composable
fun routeDraftModel(routeName: String): RouteDraftViewModel {
    val context = LocalContext.current
    return viewModel<RouteDraftViewModel>(
        key = "route:$routeName",
        factory =
            viewModelFactory {
                initializer {
                    RouteDraftViewModel(
                        context.applicationContext as android.app.Application,
                        createSavedStateHandle(),
                        routeName,
                    )
                }
            },
    )
}

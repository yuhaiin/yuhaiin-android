package io.github.asutorufa.yuhaiin.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuhaiin.Store

fun copyText(context: Context, text: String) {
    context
        .getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("yuhaiin", text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortsInputForm(store: Store?, addresses: List<String>) {
    var open by rememberSaveable { mutableStateOf(false) }
    val values by MainApplication.settings.snapshot.collectAsStateWithLifecycle()
    val port = values.setting(Settings.httpPort)
    SettingsItem(
        stringResource(R.string.listener_title),
        stringResource(R.string.listener_summary, port),
        painterResource(R.drawable.vpn_lock),
    ) {
        open = true
    }
    if (open) {
        var draft by rememberSaveable { mutableStateOf(port.toString()) }
        var saved by remember { mutableStateOf(false) }
        val webPort by
            produceState(0) {
                value = withContext(Dispatchers.IO) { store?.getInt(Constants.WEB_PORT_KEY) ?: 0 }
            }
        val valid = draft.toIntOrNull()?.let { it in 0..65535 } == true
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val failure by MainApplication.settings.failure.collectAsStateWithLifecycle()
        var saveError by remember { mutableStateOf<String?>(null) }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).imePadding()) {
                Column(
                    Modifier.weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.listener_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.listener_port_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        draft,
                        {
                            if (it.length <= 5 && it.all(Char::isDigit)) {
                                draft = it
                                saved = false
                            }
                        },
                        label = { Text(stringResource(R.string.listener_proxy_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !valid,
                        supportingText = {
                            if (!valid) Text(stringResource(R.string.port_invalid))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.listener_web_port, webPort),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.listener_save_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (addresses.isNotEmpty()) {
                        Text(
                            stringResource(R.string.listener_addresses),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        addresses.forEach { address ->
                            TextButton(
                                onClick = { copyText(context, address.substringBefore(" (")) }
                            ) {
                                Text(address)
                            }
                        }
                    }
                    if (saved)
                        Text(
                            stringResource(R.string.settings_saved),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    (saveError ?: failure)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        enabled = valid,
                        onClick = {
                            MainApplication.settings.set(Settings.httpPort, draft.toInt())
                            scope.launch {
                                runCatching { MainApplication.settings.flush() }
                                    .onSuccess {
                                        saved = MainApplication.settings.failure.value == null
                                    }
                                    .onFailure { saveError = it.message }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    TextButton(onClick = { open = false }) {
                        Text(stringResource(if (saved) R.string.close else R.string.cancel))
                    }
                }
            }
        }
    }
}

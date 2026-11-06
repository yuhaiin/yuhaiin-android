package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Separate, feature-specific disclosures: dismissing either one never grants consent. */
@Composable
fun DataDisclosureDialog(
    purpose: Purpose,
    error: String? = null,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (purpose == Purpose.VPN) R.string.vpn_disclosure_title
                    else R.string.apps_disclosure_title
                )
            )
        },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(
                        if (purpose == Purpose.VPN) R.string.vpn_disclosure_body
                        else R.string.apps_disclosure_body
                    )
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.privacy_agree)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.privacy_not_now)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit, disconnect: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var confirmWithdrawal by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var withdrawing by remember { mutableStateOf(false) }
    val choiceFailed = stringResource(R.string.privacy_choice_failed)
    val contactFailed = stringResource(R.string.privacy_contact_failed)
    val withdrawn = stringResource(R.string.privacy_withdrawn)
    val sections =
        listOf(
            R.string.privacy_developer_title to R.string.privacy_developer_body,
            R.string.vpn_disclosure_title to R.string.vpn_disclosure_body,
            R.string.apps_disclosure_title to R.string.apps_disclosure_body,
            R.string.privacy_storage_title to R.string.privacy_storage_body,
            R.string.privacy_network_title to R.string.privacy_network_body,
            R.string.privacy_retention_title to R.string.privacy_retention_body,
        )
    Scaffold(
        contentWindowInsets = appContentInsets,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { ScreenTitle(stringResource(R.string.privacy_policy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        ReadingPane(Modifier.scaffoldBody(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Text(stringResource(R.string.privacy_effective_date)) }
                for ((title, body) in sections) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(title),
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(stringResource(body))
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            runCatching {
                                    uriHandler.openUri(
                                        "https://github.com/yuhaiin/yuhaiin-android/issues"
                                    )
                                }
                                .onFailure {
                                    message = contactFailed
                                }
                        }
                    ) {
                        Text(stringResource(R.string.privacy_contact))
                    }
                }
                item {
                    OutlinedButton(enabled = !withdrawing, onClick = { confirmWithdrawal = true }) {
                        Text(stringResource(R.string.privacy_withdraw))
                    }
                }
                if (message != null) item { Text(message!!) }
            }
        }
    }
    if (confirmWithdrawal)
        AlertDialog(
            onDismissRequest = { confirmWithdrawal = false },
            title = { Text(stringResource(R.string.privacy_withdraw)) },
            text = { Text(stringResource(R.string.privacy_withdraw_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWithdrawal = false
                        withdrawing = true
                        scope.launch {
                            runCatching {
                                    withContext(Dispatchers.IO) {
                                        PrivacyConsent.forContext(context).revoke()
                                        MainApplication.installedApps.clear()
                                    }
                                    disconnect()
                                }
                                .onSuccess {
                                    message = withdrawn
                                }
                                .onFailure {
                                    message = choiceFailed
                                }
                            withdrawing = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.privacy_withdraw))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdrawal = false }) {
                    Text(stringResource(R.string.privacy_not_now))
                }
            },
        )
}

package io.github.asutorufa.yuhaiin.compose

import android.os.Build
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.update.UpdateManager

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AboutScreen(
    navController: NavController? = null,
    updateManager: UpdateManager? = null,
    proxyReady: Boolean = false,
    startProxy: () -> Unit = {},
    animatedContentScope: AnimatedContentScope? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        modifier = Modifier.thenIfNotNull(animatedContentScope) {
                            sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_ABOUT_TITLE"),
                                animatedVisibilityScope = it,
                            )
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                UpdateCard(
                    manager = updateManager,
                    proxyReady = proxyReady,
                    startProxy = startProxy,
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.handyman),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .thenIfNotNull(animatedContentScope) {
                                    sharedBounds(
                                        sharedContentState = rememberSharedContentState("OPEN_ABOUT_ICON"),
                                        animatedVisibilityScope = it,
                                    )
                                },
                        )
                        Text("Yuhaiin", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.about_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.about_version_information),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        VersionRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                        VersionRow(
                            stringResource(R.string.about_version_code),
                            BuildConfig.VERSION_CODE.toString(),
                        )
                        VersionRow(
                            stringResource(R.string.about_commit),
                            BuildConfig.GIT_COMMIT.ifBlank { stringResource(R.string.unknown) },
                        )
                        VersionRow(
                            stringResource(R.string.about_android),
                            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        )
                        VersionRow(
                            stringResource(R.string.about_abi),
                            Build.SUPPORTED_ABIS.firstOrNull() ?: stringResource(R.string.unknown),
                        )
                        VersionRow(stringResource(R.string.about_package), BuildConfig.APPLICATION_ID)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

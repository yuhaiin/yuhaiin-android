package io.github.asutorufa.yuhaiin

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class AppListData(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable,
    val isSystemApp: Boolean = false,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppListComponent(navController: NavController, packageManager: PackageManager) {
    val checkedApps = remember {
        mutableStateSetOf(
            *MainApplication.store.getStringSet("app_list").toTypedArray()
        )
    }

    val data by produceState<MutableList<AppListData>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val packages = packageManager.getInstalledApplications(PackageManager.GET_PERMISSIONS)

            val checkedApps = MainApplication.store.getStringSet("app_list")
            val apps = mutableListOf<AppListData>()

            packages.sortBy { it.loadLabel(packageManager).toString() }

            var index = 0
            packages.forEach {
                val app = AppListData(
                    it.loadLabel(packageManager).toString(), // app name
                    it.packageName, it.loadIcon(packageManager), // icon
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) > 0, // is system
                )
                if (checkedApps.contains(app.packageName)) apps.add(index++, app)
                else apps.add(app)
            }

            apps
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MainApplication.store.putStringSet("app_list", checkedApps.toSet())
        }
    }

    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                modifier = Modifier,
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                onSearch = {},
                placeholder = {
                    if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "Filter",
                            textAlign = TextAlign.Center,
                        )
                    }
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
            )
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                state = searchBarState,
                scrollBehavior = scrollBehavior,
                inputField = inputField,
                navigationIcon = {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                        tooltip = { PlainTooltip { Text("Back") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (data == null) {
                    LoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(55.dp)
                    )
                } else
                    AppList(apps = data!!, checkedApps = checkedApps, filter = textFieldState.text)
            }
        }
    )
}

@Composable
@Preview
fun AppList(
    modifier: Modifier = Modifier,
    checkedApps: SnapshotStateSet<String> = SnapshotStateSet(),
    apps: MutableList<AppListData> = mutableListOf(
        AppListData(
            "test",
            "com.example.test",
            Color.RED.toDrawable(),
            false
        ),
        AppListData(
            "test",
            "com.example.test",
            Color.RED.toDrawable(),
            false
        )
    ),
    filter: CharSequence? = null
) {
    val filteredApps = remember(apps, filter) {
        if (filter.isNullOrBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.packageName.contains(filter, ignoreCase = true) ||
                        app.appName.contains(filter, ignoreCase = true)
            }
        }
    }

    LazyColumn(modifier = modifier) {
        items(filteredApps) { app ->
            AppListItem(app, onClick = {
                if (checkedApps.contains(app.packageName)) checkedApps.remove(app.packageName)
                else checkedApps.add(app.packageName)
            }, checkedApps = checkedApps)
        }
    }
}

@Composable
@Preview
fun AppListItem(
    app: AppListData = AppListData(
        "test",
        "com.example.test",
        Color.RED.toDrawable(),
        false
    ),
    checkedApps: SnapshotStateSet<String> = SnapshotStateSet(),
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = remember(app.appIcon) {
                app.appIcon.toBitmap(height = 128, width = 128).asImageBitmap()
            },
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .padding(8.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Checkbox(
            checked = checkedApps.contains(app.packageName),
            onCheckedChange = null,
            modifier = Modifier
                .padding(start = 2.dp, end = 20.dp)
        )
    }
}
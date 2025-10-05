package io.github.asutorufa.yuhaiin.compose

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.getStringSet
import io.github.asutorufa.yuhaiin.putStringSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AppListData(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable? = null,
    val isSystemApp: Boolean = false,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun PreviewAppListComponent() {
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            AppListComponent(animatedVisibilityScope = this@AnimatedVisibility)
        }
    }
}

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun SharedTransitionScope.AppListComponent(
    navController: NavController? = null,
    packageManager: PackageManager? = null,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val checkedApps = remember {
        mutableStateSetOf(
            *MainApplication.Companion.store.getStringSet("app_list").toTypedArray()
        )
    }

    val data by produceState<MutableList<AppListData>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            val packages = packageManager?.getInstalledApplications(PackageManager.GET_PERMISSIONS)

            val checkedApps = MainApplication.Companion.store.getStringSet("app_list")
            val apps = mutableListOf<AppListData>()

            packages?.sortBy { it.loadLabel(packageManager).toString() }

            var index = 0
            packages?.forEach {
                val app = AppListData(
                    it.loadLabel(packageManager).toString(), // app name
                    it.packageName, it.loadIcon(packageManager), // icon
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) > 0, // is system
                )
                if (checkedApps.contains(app.packageName)) apps.add(index++, app)
                else apps.add(app)
            }

            (System.currentTimeMillis() - startTime).apply {
                if (this < 500) delay(500 - this)
            }
            apps
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MainApplication.Companion.store.putStringSet("app_list", checkedApps.toSet())
        }
    }

    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            FlexibleBottomAppBar(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState("OPEN_APP_LIST_TITLE"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                scrollBehavior = scrollBehavior,
            ) {
                IconButton(
                    onClick = { navController?.popBackStack() },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                SearchBar(
                    state = searchBarState,
                    inputField = {
                        SearchBarDefaults.InputField(
                            searchBarState = searchBarState,
                            textFieldState = textFieldState,
                            onSearch = {},
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                        )
                    }
                )
            }
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (data == null) LoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(55.dp)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState("OPEN_APP_LIST_ICON"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                    )
                    else AppList(
                        modifier = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState("OPEN_APP_LIST_ICON"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                        apps = data!!,
                        checkedApps = checkedApps,
                        filter = textFieldState.text
                    )
                }
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
            appIcon = null,
            false
        ),
        AppListData(
            "test",
            "com.example.test",
            appIcon = null,
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

    val statusBarHeight = WindowInsets.statusBars
        .getTop(LocalDensity.current)

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp, top = statusBarHeight.dp)
    ) {
        items(filteredApps) { app ->
            Card(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
            ) {
                AppListItem(app, onClick = {
                    if (checkedApps.contains(app.packageName)) checkedApps.remove(app.packageName)
                    else checkedApps.add(app.packageName)
                }, checkedApps = checkedApps)
            }
        }
    }


}

@Composable
@Preview
fun AppListItem(
    app: AppListData = AppListData(
        "test",
        "com.example.test",
        null,
        false
    ),
    checkedApps: SnapshotStateSet<String> = SnapshotStateSet(),
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.appIcon != null) Image(
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
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Checkbox(
            checked = checkedApps.contains(app.packageName),
            onCheckedChange = null,
            enabled = false,
            modifier = Modifier
                .padding(start = 2.dp, end = 20.dp)
        )
    }
}
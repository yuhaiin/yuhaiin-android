package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class AppListData(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable,
    val isSystemApp: Boolean = false,
)

@SuppressLint("ViewModelConstructorInComposable")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppListComponent(packageManager: PackageManager) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        if (data == null) {
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(55.dp)
            )
        } else
            AppList(apps = data!!, checkedApps = checkedApps)
    }
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
) {

    LazyColumn(modifier = modifier) {
        items(apps) { app ->
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
            .heightIn(min = 48.dp), // 对应 minTouchTargetSize
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.appIcon.toBitmap(height = 60, width = 60).asImageBitmap(),
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
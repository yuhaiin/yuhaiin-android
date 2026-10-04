package io.github.asutorufa.yuhaiin.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Metadata is cached separately from a bounded, lazily loaded icon cache. */
data class InstalledApp(val name: String, val packageName: String, val system: Boolean)

class InstalledAppsRepository(private val packages: PackageManager) {
    private val lock = Mutex()
    private var cached: List<InstalledApp>? = null
    private var loadedAt = 0L
    private val icons = LruCache<String, Bitmap>(128)

    suspend fun load(force: Boolean = false): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (!force && System.currentTimeMillis() - loadedAt < 60_000)
                    cached?.let {
                        return@withLock it
                    }
                val applications =
                    packages
                        .getInstalledApplications(0)
                        .map { app ->
                            InstalledApp(
                                packages.getApplicationLabel(app).toString(),
                                app.packageName,
                                app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                            )
                        }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                cached = applications
                loadedAt = System.currentTimeMillis()
                if (force) icons.evictAll()
                applications
            }
        }

    suspend fun icon(packageName: String): Bitmap? =
        withContext(Dispatchers.IO) {
            icons.get(packageName)
                ?: runCatching {
                    packages.getApplicationIcon(packageName).toBitmap(96, 96).also {
                        icons.put(packageName, it)
                    }
                }
                    .getOrNull()
        }
}

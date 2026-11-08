package io.github.asutorufa.yuhaiin.service

import android.graphics.Bitmap
import io.github.asutorufa.yuhaiin.MainApplication
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import yuhaiin.AppCatalog
import yuhaiin.AppIconProvider

@Serializable
private data class CatalogApp(val name: String, val packageName: String, val system: Boolean)

/**
 * Installed applications for per-app VPN in the Web UI. The app-list disclosure gates it exactly
 * like the native app list; without it the platform API reports consent_required. Icons are
 * served as 96 px PNGs from the repository's bounded cache.
 */
object InstalledAppCatalog : AppCatalog, AppIconProvider {
    override fun installedApps(): String {
        val apps =
            try {
                runBlocking { MainApplication.installedApps.load() }
            } catch (e: IllegalStateException) {
                throw IllegalStateException("consent_required: ${e.message}", e)
            }
        return Json.encodeToString(
            apps.map { CatalogApp(it.name, it.packageName, it.system) }
        )
    }

    override fun appIcon(packageName: String): ByteArray {
        if (!MainApplication.installedApps.hasConsent())
            throw IllegalStateException("consent_required: app list consent is required")
        val icon =
            runBlocking { MainApplication.installedApps.icon(packageName) }
                ?: throw IllegalArgumentException("not_found: $packageName")
        return ByteArrayOutputStream().use {
            icon.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
    }
}

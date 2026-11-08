package io.github.asutorufa.yuhaiin.service

import io.github.asutorufa.yuhaiin.MainApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import yuhaiin.AppCatalog

@Serializable
private data class CatalogApp(val name: String, val packageName: String, val system: Boolean)

/**
 * Installed applications for per-app VPN in the Web UI. The app-list disclosure gates it exactly
 * like the native app list; without it the platform API reports consent_required.
 */
object InstalledAppCatalog : AppCatalog {
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
}

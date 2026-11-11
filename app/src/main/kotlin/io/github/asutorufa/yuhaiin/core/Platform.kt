package io.github.asutorufa.yuhaiin.core

/** Installed applications for per-app VPN; errors start with "consent_required". */
interface AppCatalog {
    fun installedApps(): String
}

/** Launcher icons as PNG; errors start with "consent_required" or "not_found". */
interface AppIconProvider {
    fun appIcon(packageName: String): ByteArray
}

/** Operations of the core's platform API that need Android or must stop the core itself. */
interface PlatformActions {
    fun restoreConfig(path: String)
    fun restartCore()
    fun privacyState(): String
    fun withdrawConsent()
    fun updateState(): String
    fun updateAction(action: String, arg: String)
    fun appInfo(): String
}

/** Registers the Android implementations behind the core's host callbacks. */
object PlatformHost {
    private val json = kotlinx.serialization.json.Json.Default

    private fun field(request: ByteArray?, name: String): String =
        json.parseToJsonElement(request!!.decodeToString())
            .let { it as kotlinx.serialization.json.JsonObject }[name]!!
            .let { (it as kotlinx.serialization.json.JsonPrimitive).content }

    fun install(catalog: AppCatalog, icons: AppIconProvider, actions: PlatformActions) {
        NativeHost.register("apps.list") { catalog.installedApps().toByteArray() }
        NativeHost.register("apps.icon") { icons.appIcon(field(it, "package")) }
        NativeHost.register("platform.restore_config") { actions.restoreConfig(field(it, "path")); null }
        NativeHost.register("platform.restart_core") { actions.restartCore(); null }
        NativeHost.register("platform.privacy_state") { actions.privacyState().toByteArray() }
        NativeHost.register("platform.withdraw_consent") { actions.withdrawConsent(); null }
        NativeHost.register("platform.update_state") { actions.updateState().toByteArray() }
        NativeHost.register("platform.update_action") {
            actions.updateAction(field(it, "action"), field(it, "arg")); null
        }
        NativeHost.register("platform.app_info") { actions.appInfo().toByteArray() }
    }
}

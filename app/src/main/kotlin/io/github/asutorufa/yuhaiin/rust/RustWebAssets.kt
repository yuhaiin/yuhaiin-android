package io.github.asutorufa.yuhaiin.rust

import android.content.Context
import java.io.File
import java.io.FileNotFoundException

/** Installs the bundled React management UI into private app storage. */
object RustWebAssets {
    fun install(context: Context): String {
        val assets = context.assets
        try {
            assets.open("web/index.html").use { }
        } catch (_: FileNotFoundException) {
            return ""
        }

        val root = File(context.filesDir, "rust-web")
        val marker = File(root, ".version")
        if (marker.readTextOrNull() != io.github.asutorufa.yuhaiin.BuildConfig.GIT_COMMIT) {
            root.deleteRecursively()
            root.mkdirs()
            copyTree(assets, "web", root)
            marker.writeText(io.github.asutorufa.yuhaiin.BuildConfig.GIT_COMMIT)
        }
        return root.path
    }

    private fun copyTree(
        assets: android.content.res.AssetManager,
        source: String,
        destination: File,
    ) {
        val children = assets.list(source).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(source).use { input -> destination.outputStream().use(input::copyTo) }
            return
        }
        children.forEach { child ->
            copyTree(assets, "$source/$child", File(destination, child))
        }
    }

    private fun File.readTextOrNull(): String? =
        if (isFile) runCatching { readText() }.getOrNull() else null
}

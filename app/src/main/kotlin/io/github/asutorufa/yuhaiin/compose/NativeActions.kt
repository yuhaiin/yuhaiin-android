package io.github.asutorufa.yuhaiin.compose

import java.net.URI

/**
 * The fixed set of OS dialogs the Web UI may ask for. There is no JavaScript bridge: the page
 * navigates to `yuhaiin-android://action/<path>` from a user gesture, and the WebView client
 * accepts it only for a main-frame navigation of the local management page. Consent is never
 * granted by the page itself; these open the in-app disclosures, the Android VPN dialog and the local network permission request.
 */
enum class NativeAction(val path: String) {
    VPN_AUTHORIZE("vpn/authorize"),
    APPS_CONSENT("consent/apps"),
    /** Android 17 local network permission: asks again, or opens system settings if blocked. */
    LOCAL_NETWORK("network/local");

    companion object {
        const val SCHEME = "yuhaiin-android"

        fun parse(
            target: String,
            pageUrl: String?,
            port: Int,
            mainFrame: Boolean,
            gesture: Boolean,
        ): NativeAction? {
            if (!mainFrame || !gesture || !isLocalPage(pageUrl, port)) return null
            val uri = runCatching { URI(target) }.getOrNull() ?: return null
            if (uri.scheme != SCHEME || uri.host != "action") return null
            val path = uri.path.orEmpty().trim('/')
            return entries.firstOrNull { it.path == path }
        }

        /** True for the management origin of the running core, over HTTP or HTTPS. */
        fun isLocalPage(url: String?, port: Int): Boolean {
            if (url == null || port <= 0) return false
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return (uri.scheme == "http" || uri.scheme == "https") &&
                uri.host == "127.0.0.1" &&
                uri.port == port
        }
    }
}

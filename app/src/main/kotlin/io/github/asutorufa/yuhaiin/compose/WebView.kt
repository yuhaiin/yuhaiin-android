package io.github.asutorufa.yuhaiin.compose

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.CoreSession
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.CoreState
import kotlinx.coroutines.launch
import yuhaiin.Yuhaiin

class WebPageState(private val savedState: SavedStateHandle) : ViewModel() {
    // History survives rotation in memory; only a small URL goes into the system saved state.
    var history: Bundle? = null
    var url: String?
        get() = savedState["url"]
        set(value) {
            savedState["url"] = value
        }
}

/** Builds the local management URL, keeping the saved path only for the same origin. */
internal fun managementUrl(port: Int, saved: String?): String {
    val uri = saved?.let(Uri::parse)
    val local = NativeAction.isLocalPage(saved, port) || uri?.host == "127.0.0.1"
    return Uri.Builder()
        .scheme("http")
        .encodedAuthority("127.0.0.1:$port")
        .encodedPath(if (local) uri?.encodedPath ?: "/" else "/")
        .encodedQuery(if (local) uri?.encodedQuery else null)
        .encodedFragment(if (local) uri?.encodedFragment else null)
        .build()
        .toString()
}

/**
 * Gives the WebView the core's per-start platform session. The cookie is HttpOnly, SameSite=Strict
 * and scoped to the platform API path, so page scripts cannot read it, other sites cannot send it,
 * and LAN browsers never have it.
 */
private fun installPlatformSession(port: Int, token: String) {
    val cookies = CookieManager.getInstance()
    val value =
        "${Yuhaiin.PlatformCookieName}=$token; Path=/android/; HttpOnly; SameSite=Strict"
    cookies.setCookie("http://127.0.0.1:$port", value)
    cookies.flush()
}

/**
 * The v2 main screen: the yuhaiin Web UI of the running core. The WebView lives in the UI process;
 * the core lives in ":bg", so a renderer crash never stops the core and a VPN reconfiguration
 * never reloads the page. Only a new port or token (core restart) reconnects.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewComponent(
    session: CoreSession,
    onRetry: () -> Unit,
    onNativeAction: (NativeAction) -> Unit,
) {
    val state: WebPageState = viewModel()
    val context = LocalContext.current
    val renderError by rememberUpdatedState(stringResource(R.string.web_render_failed))
    val pageBackground = MaterialTheme.colorScheme.surface.toArgb()
    val nativeAction by rememberUpdatedState(onNativeAction)
    var view by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var renderer by remember { mutableIntStateOf(0) }
    // A dead renderer is replaced only on an explicit retry, never in a loop.
    var rendererGone by remember { mutableStateOf(false) }
    // True while the Core-hosted recovery page loads; its history must not replace the app's.
    var recoveryNavigation by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(it.resultCode, it.data))
            fileCallback = null
        }
    val port = if (session.ready) session.port else 0
    // A new token or port means the core restarted; the old page cannot reach the platform API.
    val identity = "$port/${session.token}/$renderer"

    fun updateHistory(web: WebView) {
        canBack = web.canGoBack()
        if (NativeAction.isLocalPage(web.url, port)) state.url = web.url
    }

    // Opens the Core's bundled Web UI so a broken OTA frontend can be repaired or reverted.
    // A fresh WebView is created for the live port; the old history is not carried over.
    fun openRecovery() {
        if (port <= 0) return
        recoveryNavigation = true
        state.history = null
        state.url = "http://127.0.0.1:$port/__ui_recovery"
        error = null
        rendererGone = false
        loading = true
        renderer++
    }

    BackHandler(enabled = canBack && error == null) { view?.goBack() }
    // The Web UI draws under the status and navigation bars and pads itself with
    // env(safe-area-inset-top/bottom); only the sides (cutout) are kept native.
    Scaffold(contentWindowInsets = appContentInsets.only(WindowInsetsSides.Horizontal)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (port > 0 && !rendererGone)
                key(identity) {
                    AndroidView(
                        factory = { ctx ->
                            installPlatformSession(port, session.token)
                            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                            WebView(ctx).apply {
                                layoutParams =
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                setBackgroundColor(pageBackground)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(false)
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                // No addJavascriptInterface: device functions go through the
                                // authenticated platform API or the fixed NativeAction links.
                                setDownloadListener { url, userAgent, disposition, mime, _ ->
                                    // Script-generated blobs cannot be saved without a
                                    // JavaScript bridge; the Web UI uses one-time links.
                                    if (url.startsWith("blob:")) return@setDownloadListener
                                    if (!NativeAction.isLocalPage(url, port)) return@setDownloadListener
                                    runCatching {
                                            val name = URLUtil.guessFileName(url, disposition, mime)
                                            val request =
                                                DownloadManager.Request(Uri.parse(url))
                                                    .setMimeType(mime)
                                                    .addRequestHeader("User-Agent", userAgent)
                                                    .setNotificationVisibility(
                                                        DownloadManager.Request
                                                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                                    )
                                                    .setDestinationInExternalPublicDir(
                                                        Environment.DIRECTORY_DOWNLOADS,
                                                        name,
                                                    )
                                            ctx.getSystemService(DownloadManager::class.java)
                                                .enqueue(request)
                                        }
                                        .onFailure {
                                            Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show()
                                        }
                                }
                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onShowFileChooser(
                                            web: WebView,
                                            callback: ValueCallback<Array<Uri>>,
                                            params: FileChooserParams,
                                        ): Boolean {
                                            fileCallback?.onReceiveValue(null)
                                            fileCallback = callback
                                            return try {
                                                filePicker.launch(params.createIntent())
                                                true
                                            } catch (_: ActivityNotFoundException) {
                                                fileCallback = null
                                                false
                                            }
                                        }
                                    }
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            web: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            val url = request.url
                                            if (
                                                url.scheme in listOf("http", "https") &&
                                                    url.host == "127.0.0.1" &&
                                                    url.port == port
                                            )
                                                return false
                                            if (url.scheme == NativeAction.SCHEME) {
                                                NativeAction.parse(
                                                        url.toString(),
                                                        web.url,
                                                        port,
                                                        request.isForMainFrame,
                                                        request.hasGesture(),
                                                    )
                                                    ?.let(nativeAction)
                                                return true
                                            }
                                            if (
                                                request.isForMainFrame &&
                                                    request.hasGesture() &&
                                                    url.scheme in listOf("http", "https")
                                            )
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, url)
                                                    )
                                                }
                                            return true
                                        }

                                        override fun onPageStarted(
                                            web: WebView,
                                            url: String?,
                                            favicon: Bitmap?,
                                        ) {
                                            loading = true
                                            error = null
                                        }

                                        override fun onPageFinished(web: WebView, url: String?) {
                                            loading = false
                                            updateHistory(web)
                                            if (url?.endsWith("/__ui_recovery") == true) recoveryNavigation = false
                                        }

                                        override fun doUpdateVisitedHistory(
                                            web: WebView,
                                            url: String?,
                                            reload: Boolean,
                                        ) {
                                            updateHistory(web)
                                        }

                                        override fun onReceivedError(
                                            web: WebView,
                                            request: WebResourceRequest,
                                            failure: WebResourceError,
                                        ) {
                                            if (request.isForMainFrame) {
                                                loading = false
                                                error = failure.description.toString()
                                            }
                                        }

                                        override fun onReceivedHttpError(
                                            web: WebView,
                                            request: WebResourceRequest,
                                            response: WebResourceResponse,
                                        ) {
                                            if (request.isForMainFrame) {
                                                loading = false
                                                error = "HTTP ${response.statusCode}"
                                            }
                                        }

                                        override fun onRenderProcessGone(
                                            web: WebView,
                                            detail: RenderProcessGoneDetail,
                                        ): Boolean {
                                            // Only the renderer died; the core is unaffected.
                                            loading = false
                                            error = renderError
                                            state.history = null
                                            rendererGone = true
                                            return true
                                        }
                                    }
                                view = this
                                val saved = state.url
                                // A restarted core can choose another port. Never restore a
                                // history whose origin now belongs to a stale listener.
                                val history =
                                    state.history.takeIf { NativeAction.isLocalPage(saved, port) }
                                if (history == null || restoreState(history) == null)
                                    loadUrl(managementUrl(port, saved))
                            }
                        },
                        onRelease = { web ->
                            if (error == null && !recoveryNavigation) state.history = Bundle().also(web::saveState)
                            web.stopLoading()
                            web.webViewClient = WebViewClient()
                            web.webChromeClient = null
                            web.removeAllViews()
                            web.destroy()
                            if (view === web) view = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            androidx.compose.animation.AnimatedVisibility(
                loading && error == null && port > 0,
                enter = fadeIn(AppMotion.effects()),
                exit = fadeOut(AppMotion.effects()),
            ) {
                MaterialLinearProgressIndicator()
            }
            if (port == 0 || error != null || rendererGone)
                CoreStateScreen(
                    session,
                    error,
                    onRetry = {
                        error = null
                        when {
                            rendererGone -> {
                                rendererGone = false
                                renderer++
                            }
                            session.ready -> view?.reload() ?: run { renderer++ }
                            else -> onRetry()
                        }
                    },
                    // Only meaningful once the core listens; without it there is nothing to host.
                    onRecovery = if (port > 0) ::openRecovery else null,
                )
        }
    }
}

/** Native loading and failure screen, shown while the core is unavailable. */
@Composable
private fun CoreStateScreen(
    session: CoreSession,
    error: String?,
    onRetry: () -> Unit,
    onRecovery: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmLogs by remember { mutableStateOf(false) }
    if (confirmLogs)
        AlertDialog(
            onDismissRequest = { confirmLogs = false },
            text = { Text(stringResource(R.string.log_share_disclosure)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogs = false
                        scope.launch { runCatching { shareLogs(context) } }
                    }
                ) {
                    Text(stringResource(R.string.log_share_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogs = false }) {
                    Text(stringResource(R.string.privacy_not_now))
                }
            },
        )
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val starting = error == null && session.state == CoreState.STARTING
            if (starting)
                Box(contentAlignment = Alignment.Center) {
                    MaterialCircularProgressIndicator()
                    // The wavy ring is 240dp with ~19dp of stroke and wave on each side.
                    Text(
                        stringResource(R.string.core_starting),
                        modifier = Modifier.width(150.dp),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            else
                Text(
                    when {
                        error != null -> error
                        session.state == CoreState.FAILED ->
                            stringResource(R.string.core_start_failed)
                        else -> stringResource(R.string.core_stopped_hint)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            if (error == null && session.error.isNotBlank())
                Text(
                    session.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            if (!starting)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRetry) {
                        Text(
                            stringResource(
                                if (session.ready) R.string.retry else R.string.start_core
                            )
                        )
                    }
                    // The Web UI's log page needs a running core.
                    OutlinedButton(onClick = { confirmLogs = true }) {
                        Text(stringResource(R.string.open_logs))
                    }
                }
            if (!starting && onRecovery != null)
                TextButton(onClick = onRecovery) {
                    Text(stringResource(R.string.web_restore_bundled))
                }
        }
    }
}

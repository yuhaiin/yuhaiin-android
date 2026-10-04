package io.github.asutorufa.yuhaiin.compose

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.asutorufa.yuhaiin.BuildConfig
import io.github.asutorufa.yuhaiin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebPageState(private val savedState: SavedStateHandle) : ViewModel() {
    // History survives rotation in memory; only a small URL goes into the system saved state.
    var history: Bundle? = null
    var url: String?
        get() = savedState["url"]
        set(value) {
            savedState["url"] = value
        }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewComponent(onBack: () -> Unit, getPort: () -> Int) {
    val state: WebPageState = viewModel()
    val context = LocalContext.current
    val renderError by rememberUpdatedState(stringResource(R.string.web_render_failed))
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var port by remember { mutableIntStateOf(0) }
    LaunchedEffect(generation) { port = withContext(Dispatchers.IO) { getPort() } }
    fun updateHistory(web: WebView) {
        canBack = web.canGoBack()
        canForward = web.canGoForward()
        state.url = web.url
    }
    fun refresh() {
        scope.launch {
            port = withContext(Dispatchers.IO) { getPort() }
            error = null
            val current = view
            if (current != null && port > 0) {
                val path = current.url?.let(Uri::parse)
                current.loadUrl(
                    Uri.Builder()
                        .scheme("http")
                        .encodedAuthority("127.0.0.1:$port")
                        .encodedPath(path?.encodedPath ?: "/")
                        .encodedQuery(path?.encodedQuery)
                        .encodedFragment(path?.encodedFragment)
                        .build()
                        .toString()
                )
            } else generation++
        }
    }
    BackHandler(enabled = canBack && error == null) { view?.goBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.open_dashboard)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = ::refresh) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(enabled = canBack, onClick = { view?.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.web_back))
                    }
                    IconButton(enabled = canForward, onClick = { view?.goForward() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            stringResource(R.string.web_forward),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (port > 0)
                key(generation) {
                    AndroidView(
                        factory = { ctx ->
                            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                            WebView(ctx).apply {
                                // Give WebView a viewport instead of its wrap-content default.
                                // A zero initial viewport also collapses CSS vh/dvh page layouts.
                                layoutParams =
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                webChromeClient = WebChromeClient()
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            web: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            val url = request.url
                                            if (
                                                url.scheme == "http" &&
                                                    url.host == "127.0.0.1" &&
                                                    url.port == port
                                            )
                                                return false
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
                                            loading = false
                                            error = renderError
                                            state.history = null
                                            // Remove the dead instance. Retry creates a new
                                            // renderer and view.
                                            port = 0
                                            return true
                                        }
                                    }
                                view = this
                                val saved = state.url?.let(Uri::parse)
                                val local = saved?.scheme == "http" && saved.host == "127.0.0.1"
                                // A reconnect can choose another port. Do not restore a history
                                // whose origin now belongs to a stale listener.
                                val history = state.history.takeIf { local && saved.port == port }
                                if (history == null || restoreState(history) == null) {
                                    loadUrl(
                                        Uri.Builder()
                                            .scheme("http")
                                            .encodedAuthority("127.0.0.1:$port")
                                            .encodedPath(
                                                if (local) saved.encodedPath ?: "/" else "/"
                                            )
                                            .encodedQuery(if (local) saved.encodedQuery else null)
                                            .encodedFragment(
                                                if (local) saved.encodedFragment else null
                                            )
                                            .build()
                                            .toString()
                                    )
                                }
                            }
                        },
                        onRelease = { web ->
                            if (error == null) state.history = Bundle().also(web::saveState)
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
            if (loading && error == null) MaterialLinearProgressIndicator()
            if (error != null || port == 0)
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            error ?: stringResource(R.string.web_not_running),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = ::refresh) { Text(stringResource(R.string.retry)) }
                    }
                }
        }
    }
}

package io.github.asutorufa.yuhaiin.compose

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation.NavController

@SuppressLint("SetJavaScriptEnabled")
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
@Preview
fun SharedTransitionScope.WebViewComponent(
    animatedContentScope: AnimatedContentScope? = null,
    navController: NavController? = null,
    getPort: () -> Int = { 0 },
) {
    var isLoading by remember { mutableStateOf(true) }
    val webView = remember { mutableStateOf<WebView?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun onRefresh() {
        val currentUrl = webView.value?.url ?: return
        val uri = currentUrl.toUri()
        val newUri = uri.buildUpon().encodedAuthority("127.0.0.1:${getPort()}").build()
        webView.value?.loadUrl(newUri.toString())
    }

    Scaffold(
        modifier = Modifier.thenIfNotNull(animatedContentScope) {
            sharedBounds(
                sharedContentState = rememberSharedContentState("OPEN_WEBVIEW"),
                animatedVisibilityScope = it,
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            HorizontalFloatingToolbar(
                expanded = expanded,
                leadingContent = {
                    IconButton(
                        onClick = { navController?.popBackStack() }) {
                        Icon(
                            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ExitToApp),
                            contentDescription = "Return To Home"
                        )
                    }
                    IconButton(onClick = { onRefresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
                trailingContent = {
                    IconButton(
                        onClick = { if (webView.value?.canGoBack() == true) webView.value?.goBack() }) {
                        Icon(
                            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = "Webview Back"
                        )
                    }
                    IconButton(
                        onClick = { if (webView.value?.canGoForward() == true) webView.value?.goForward() }) {
                        Icon(
                            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward),
                            contentDescription = "Webview Forward"
                        )
                    }
                },
            ) {
                FilledIconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        painter = rememberVectorPainter(if (expanded) Icons.Filled.Close else Icons.Filled.Add),
                        contentDescription = "Expand"
                    )
                }
            }
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .animateContentSize()
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun setRefreshEnabled(enabled: Boolean) {
                                }
                            }, "Android")

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

                            setLayerType(View.LAYER_TYPE_HARDWARE, null)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }
                            }

                            loadUrl("http://127.0.0.1:${getPort()}")

                            webView.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(55.dp)
                    )
                }
                BackHandler(enabled = true) {
                    if (webView.value?.canGoBack() == true) webView.value?.goBack()
                    else navController?.popBackStack()
                }

            }
        }
    )
}
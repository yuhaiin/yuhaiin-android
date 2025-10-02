package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun SharedTransitionScope.WebViewComponent(
    animatedContentScope: AnimatedContentScope,
    navController: NavController,
    getPort: () -> Int,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val webView = remember { mutableStateOf<WebView?>(null) }

    fun onRefresh() {
        val currentUrl = webView.value?.url ?: return
        val uri = currentUrl.toUri()
        val newUri = uri.buildUpon().encodedAuthority("127.0.0.1:${getPort()}").build()
        webView.value?.loadUrl(newUri.toString())
    }

    Scaffold(
        modifier = Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState("OPEN_WEBVIEW"),
            animatedVisibilityScope = animatedContentScope,
        ),
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                        tooltip = { PlainTooltip { Text("Refresh") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { onRefresh() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                        tooltip = { PlainTooltip { Text("Close") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close"
                            )
                        }
                    }
                },
            )
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .animateContentSize()
            ) {

                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.Center)
                            .size(55.dp)
                    )
                }

                LaunchedEffect(Unit) {
                    val startTime = System.currentTimeMillis()
                    WebView(context).apply {
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun setRefreshEnabled(enabled: Boolean) {
                            }
                        }, "Android")

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

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        loadUrl("http://127.0.0.1:${getPort()}")

                        (System.currentTimeMillis() - startTime).apply {
                            if (this < 500) delay(500 - this)
                        }

                        webView.value = this
                    }


                }

                if (webView.value != null) AndroidView(factory = { webView.value!! })


                BackHandler(enabled = true) {
                    if (webView.value?.canGoBack() == true) webView.value?.goBack()
                    else navController.popBackStack()
                }

            }
        }
    )
}
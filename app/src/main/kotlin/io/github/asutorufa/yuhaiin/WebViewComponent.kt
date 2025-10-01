package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebViewComponent(navController: NavController) {
    var isLoading by remember { mutableStateOf(true) }

    fun getHost() = "127.0.0.1:${MainApplication.store.getInt("yuhaiin_port")}"

    fun onRefresh(webView: WebView) {
        val currentUrl = webView.url ?: return
        val uri = currentUrl.toUri()
        val newUri = uri.buildUpon().encodedAuthority(getHost()).build()
        webView.loadUrl(newUri.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yuhaiin") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .animateContentSize()
            ) {
                val context = LocalContext.current

                val webView = remember {
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
                        loadUrl("http://${getHost()}")
                    }
                }


                BackHandler(enabled = true) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        navController.popBackStack()
                    }
                }

                AndroidView(factory = { webView })
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.Center)
                            .size(55.dp)
                    )
                }
            }
        }
    )
}
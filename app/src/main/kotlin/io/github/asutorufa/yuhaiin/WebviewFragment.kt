package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import io.github.asutorufa.yuhaiin.databinding.FragmentWebviewBinding

class WebviewFragment : Fragment() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {


        val view = inflater.inflate(R.layout.fragment_webview, container, false)
        FragmentWebviewBinding.bind(view).apply {
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun setRefreshEnabled(enabled: Boolean) {
                }
            }, "Android")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    webViewLoadingIndicator.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    webViewLoadingIndicator.visibility = View.GONE
                }
            }
            
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.loadUrl("http://127.0.0.1:${MainApplication.store.getInt("yuhaiin_port")}")

            requireActivity().addMenuProvider(object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater
                ) {
                    menuInflater.inflate(R.menu.webview_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_refresh -> {
                            val currentUrl = webView.url ?: return true
                            val uri = currentUrl.toUri()
                            val newUri = uri.buildUpon()
                                .encodedAuthority("127.0.0.1:${MainApplication.store.getInt("yuhaiin_port")}")
                                .build()
                            webView.loadUrl(newUri.toString())
                            true
                        }

                        else -> false
                    }
                }

            }, viewLifecycleOwner)

            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
            ) {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }


        }

        return view
    }
}
package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import io.github.asutorufa.yuhaiin.database.Manager

class PageFragment : Fragment() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.webview, container, false).apply {
            (findViewById<View>(R.id.web_view) as WebView).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                loadUrl("http://127.0.0.1:${Manager.profile.yuhaiinPort}")
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.action == KeyEvent.ACTION_UP
                        && canGoBack()
                    ) {
                        goBack()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
        }
    }
}
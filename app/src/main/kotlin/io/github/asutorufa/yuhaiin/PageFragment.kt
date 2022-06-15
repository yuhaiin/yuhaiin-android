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
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.webview, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<WebView>(R.id.web_view).apply {
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
        super.onViewCreated(view, savedInstanceState)
    }
}
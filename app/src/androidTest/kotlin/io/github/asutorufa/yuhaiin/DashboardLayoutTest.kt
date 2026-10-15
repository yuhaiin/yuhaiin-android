package io.github.asutorufa.yuhaiin

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.asutorufa.yuhaiin.compose.WebViewComponent
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Measures the real Android WebView, including system insets, without a running VPN. */
class DashboardLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = ServerSocket(0)
    private val requests = AtomicInteger()

    init {
        thread(isDaemon = true, name = "dashboard-fixture") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    reader.readLine()
                    while (!reader.readLine().isNullOrEmpty()) {}
                    requests.incrementAndGet()
                    val body =
                        """
                        <!doctype html><meta name="viewport" content="width=device-width, initial-scale=1">
                        <main style="height:100dvh">Dashboard fixture <a href="/next">Next</a></main>
                        """
                            .trimIndent()
                            .toByteArray()
                    it.getOutputStream().apply {
                        write(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                .toByteArray()
                        )
                        write(body)
                    }
                }
            }
        }
    }

    @After fun closeServer() = server.close()

    private fun webView(view: View): WebView? =
        if (view is WebView) view
        else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { webView(view.getChildAt(it)) }
        else null

    private fun label(id: Int) = compose.activity.getString(id)

    private fun waitForPath(path: String) {
        compose.waitUntil(10_000) {
            var ready = false
            compose.runOnUiThread {
                val web = webView(compose.activity.window.decorView)
                ready =
                    web?.copyBackForwardList()?.currentItem?.url ==
                        "http://127.0.0.1:${server.localPort}$path" && web.progress == 100
            }
            ready
        }
        compose.waitForIdle()
    }

    private fun openControls() {
        compose.onNodeWithContentDescription(label(R.string.web_controls)).performClick()
    }

    private fun navigateHistory(id: Int) {
        // WebView's visited-history and page-finished callbacks follow the URL change.
        compose.waitUntil(5000) {
            compose
                .onAllNodes(hasText(label(id)) and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText(label(id)).performClick()
    }

    @Test
    fun dashboardUsesAllSpaceBetweenSystemBars() {
        compose.runOnUiThread {
            compose.activity.setTheme(
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
            compose.activity.enableEdgeToEdge()
        }
        compose.setContent {
            MaterialTheme { WebViewComponent(onBack = {}, getPort = { server.localPort }) }
        }
        val pageHeight = AtomicReference(0.0)
        compose.waitUntil(10_000) {
            compose.runOnUiThread {
                webView(compose.activity.window.decorView)?.evaluateJavascript(
                    "document.querySelector('main')?.getBoundingClientRect().height || 0"
                ) {
                    pageHeight.set(it.toDoubleOrNull() ?: 0.0)
                }
            }
            pageHeight.get() > 100
        }
        compose.runOnUiThread {
            val root = compose.activity.window.decorView
            val web = requireNotNull(webView(root))
            val insets =
                requireNotNull(ViewCompat.getRootWindowInsets(root))
                    .getInsets(
                        WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout()
                    )
            val available = root.height - insets.top - insets.bottom
            assertTrue(
                "WebView height ${web.height} must use the available $available px",
                web.height >= available - 1,
            )
            val position = IntArray(2)
            web.getLocationOnScreen(position)
            assertEquals("No native toolbar above the web page", insets.top, position[1])
        }
    }

    @Test
    fun floatingControlsNavigateRefreshAndDismissWithoutResizingPage() {
        val closed = AtomicBoolean()
        compose.runOnUiThread {
            compose.activity.setTheme(
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
            compose.activity.enableEdgeToEdge()
        }
        compose.setContent {
            MaterialTheme {
                WebViewComponent(onBack = { closed.set(true) }, getPort = { server.localPort })
            }
        }
        waitForPath("/")
        var originalHeight = 0
        compose.runOnUiThread {
            originalHeight = requireNotNull(webView(compose.activity.window.decorView)).height
        }
        openControls()
        compose.onNodeWithText(label(R.string.web_back)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.web_forward)).assertIsNotEnabled()
        compose.runOnUiThread {
            assertEquals(
                originalHeight,
                requireNotNull(webView(compose.activity.window.decorView)).height,
            )
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressBack()
        compose.onNodeWithText(label(R.string.web_close)).assertDoesNotExist()
        // A real touch keeps the initial entry eligible for Android WebView's Back action.
        requireNotNull(device.wait(Until.findObject(By.text("Next")), 5000)).click()
        waitForPath("/next")
        openControls()
        navigateHistory(R.string.web_back)
        waitForPath("/")
        openControls()
        navigateHistory(R.string.web_forward)
        waitForPath("/next")
        // Android Back still navigates web history while the menu is collapsed.
        device.pressBack()
        waitForPath("/")
        openControls()
        navigateHistory(R.string.web_forward)
        waitForPath("/next")
        openControls()
        val beforeRefresh = requests.get()
        compose.onNodeWithText(label(R.string.refresh)).performClick()
        compose.waitUntil(10_000) { requests.get() > beforeRefresh }
        waitForPath("/next")
        openControls()
        compose.onNodeWithText(label(R.string.web_close)).performClick()
        assertTrue(closed.get())
    }
}

package io.github.asutorufa.yuhaiin

import android.Manifest
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.asutorufa.yuhaiin.data.migrateHttpProxyKey
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the real JNI store and the :bg service, rather than replacing the native contract. */
class NativeContractsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitSettings() = runBlocking(Dispatchers.IO) { MainApplication.settings.flush() }

    @Test
    fun brokenProxyKeyMigratesAnExplicitFalseInRealStore() {
        awaitSettings()
        val store = MainApplication.store
        val marker = "android_http_proxy_key_migration_v1"
        val previous = store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY)
        try {
            store.putBoolean(Constants.APPEND_HTTP_PROXY_KEY, true)
            store.putBoolean(Constants.LEGACY_UI_HTTP_PROXY_KEY, false)
            store.putBoolean(marker, false)
            migrateHttpProxyKey(store)
            assertFalse(store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
            store.putBoolean(Constants.APPEND_HTTP_PROXY_KEY, true)
            migrateHttpProxyKey(store)
            assertTrue(store.getBoolean(Constants.APPEND_HTTP_PROXY_KEY))
        } finally {
            store.putBoolean(Constants.APPEND_HTTP_PROXY_KEY, previous)
            store.putBoolean(marker, true)
            MainApplication.settings.refresh()
        }
    }

    @Test
    fun invalidStartupIsVisibleAndCanReconnectAfterRepair() {
        awaitSettings()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (Build.VERSION.SDK_INT >= 33)
            device.executeShellCommand(
                "pm grant ${BuildConfig.APPLICATION_ID} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
        val routeKey = Constants.ROUTE_CONTENT_PREFIX + Constants.ALL_ROUTE
        val original = MainApplication.store.getString(routeKey)
        try {
            runBlocking(Dispatchers.IO) {
                MainApplication.settings.commit { store ->
                    store.putString(Constants.ROUTE_KEY, Constants.ALL_ROUTE)
                    store.putString(routeKey, "INVALID_CIDR")
                }
            }
            compose.runOnUiThread { compose.activity.startService() }
            // The system VPN consent is a separate Activity. A test never taps app coordinates.
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000
            while (
                android.os.SystemClock.uptimeMillis() < deadline &&
                    compose.activity.state.value != State.ERROR
            ) {
                try {
                    device.findObject(By.res("android", "button1"))?.click()
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    /* Consent just closed. */
                }
                android.os.SystemClock.sleep(100)
            }
            assertEquals(State.ERROR, compose.activity.state.value)
            assertFalse(compose.activity.error.value.isNullOrBlank())
            runBlocking(Dispatchers.IO) {
                MainApplication.settings.commit { it.putString(routeKey, original) }
            }
            compose.runOnUiThread { compose.activity.startService() }
            // The previous ERROR remains until asynchronous service startup begins.
            compose.waitUntil(20_000) {
                compose.activity.state.value == State.CONNECTED
            }
            assertEquals(
                compose.activity.error.value,
                State.CONNECTED,
                compose.activity.state.value,
            )
            assertDashboardHasViewport()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.vpnBinder?.stop()
            }
            compose.waitUntil(15_000) { compose.activity.state.value == State.DISCONNECTED }
        } finally {
            MainApplication.store.putString(routeKey, original)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                compose.activity.vpnBinder?.stop()
            }
        }
    }

    private fun assertDashboardHasViewport() {
        // Flush the connection recomposition and expose the card before using the system UI.
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val dashboard =
            device.wait(
                Until.findObject(By.text(compose.activity.getString(R.string.open_dashboard))),
                5000,
            )
        assertNotNull("Connected home must offer the dashboard", dashboard)
        dashboard.click()
        compose.mainClock.advanceTimeBy(1000)
        val height = AtomicReference(0.0)
        fun webView(view: View): WebView? =
            if (view is WebView) view
            else if (view is ViewGroup)
                (0 until view.childCount).firstNotNullOfOrNull { webView(view.getChildAt(it)) }
            else null
        // A wrap-content WebView can have a nonzero innerHeight while CSS vh/dvh is zero,
        // leaving the dashboard's loaded body completely clipped. Check the rendered page.
        val deadline = android.os.SystemClock.uptimeMillis() + 20_000
        while (height.get() <= 100 && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                webView(compose.activity.window.decorView)?.evaluateJavascript(
                    "document.querySelector('main')?.getBoundingClientRect().height || 0"
                ) {
                    height.set(it.toDoubleOrNull() ?: 0.0)
                }
            }
            android.os.SystemClock.sleep(100)
        }
        assertTrue(
            "Dashboard body must have a visible viewport: ${height.get()}",
            height.get() > 100,
        )
    }
}

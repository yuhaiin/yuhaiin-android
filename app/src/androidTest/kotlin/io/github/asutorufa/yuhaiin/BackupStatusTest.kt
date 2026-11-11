package io.github.asutorufa.yuhaiin

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.asutorufa.yuhaiin.service.*
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import io.github.asutorufa.yuhaiin.core.NativeCore
import io.github.asutorufa.yuhaiin.core.NativeRuntime

class BackupStatusTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val activity
        get() = compose.activity

    private fun waitFor(timeout: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue("Condition did not become true within $timeout ms", condition())
    }

    private fun snapshot() = VpnStatus.fromBundle(activity.vpnBinder!!.snapshot())

    @Test
    fun webBackupExportPreviewAndRestoreRestartTheCore() {
        compose.waitUntil(20_000) { activity.session.value.ready }
        val (code, link) = platformApi(activity, "POST", "backup/export", "{}")
        assertEquals(200, code)
        val url = org.json.JSONObject(link).getString("url").removePrefix("/android/v1/")
        val (downloaded, backup) = platformApi(activity, "GET", url)
        assertEquals(200, downloaded)
        val (previewed, preview) = platformApi(activity, "POST", "backup/preview", backup)
        assertEquals(200, previewed)
        assertTrue(preview.contains("createdAt"))
        val oldToken = activity.session.value.token
        val (restored, _) = platformApi(activity, "POST", "backup/restore", backup)
        assertEquals(202, restored)
        // The core restarts with a new session for the WebView.
        waitFor(30_000) { activity.session.value.ready && activity.session.value.token != oldToken }
    }

    @Test
    fun nativeSnapshotHealthAndFinalSummaryCrossServiceBinder() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        runBlocking(Dispatchers.IO) { MainApplication.settings.flush() }
        waitFor { activity.vpnBinder != null }
        if (Build.VERSION.SDK_INT >= 33)
            device.executeShellCommand(
                "pm grant ${BuildConfig.APPLICATION_ID} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
        try {
            compose.runOnUiThread {
                grantVpnConsentForLifecycleTest(activity)
                activity.startService()
            }
            waitFor {
                runCatching { device.findObject(By.res("android", "button1"))?.click() }
                activity.state.value == State.CONNECTED
            }
            waitFor { decodeNativeStatus(snapshot().nativeStatus) != null }
            val connected = decodeNativeStatus(snapshot().nativeStatus)!!
            assertTrue(connected.startedAt > 0)
            assertTrue(connected.session.download.toULong() >= 0uL)
            assertTrue(connected.session.upload.toULong() >= 0uL)
            waitFor(30_000) {
                !snapshot().healthChecking && decodeNativeHealth(snapshot().nativeHealth) != null
            }
            assertTrue(decodeNativeHealth(snapshot().nativeHealth)!!.checkedAt > 0)
            // The test process is not ':bg'; it loads the core itself to contend for the lock.
            NativeRuntime.ensureInit(InstrumentationRegistry.getInstrumentation().targetContext)
            val backup = NativeCore.exportConfig()
            val rejected = runCatching { NativeCore.importConfig(backup) }.exceptionOrNull()
            assertNotNull("A running core must reject import", rejected)
            activity.vpnBinder!!.stop()
            waitFor { activity.state.value == State.DISCONNECTED }
            val ended = decodeNativeStatus(snapshot().nativeStatus)!!
            assertEquals(connected.startedAt, ended.startedAt)
            assertTrue(ended.durationSeconds >= connected.durationSeconds)
            assertFalse(snapshot().healthChecking)
        } finally {
            activity.vpnBinder?.stop()
            waitFor { activity.state.value == State.DISCONNECTED }
        }
    }
}

package io.github.asutorufa.yuhaiin

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.asutorufa.yuhaiin.backup.ConfigBackup
import io.github.asutorufa.yuhaiin.service.*
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yuhaiin.Yuhaiin

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
    fun fileExportPreviewAndRestoreUseRealCoreAndOpenPreferenceHandle() {
        waitFor { activity.vpnBinder != null }
        activity.vpnBinder!!.stop()
        waitFor { activity.state.value == State.DISCONNECTED }
        runBlocking(Dispatchers.IO) { MainApplication.settings.flush() }
        val original = Yuhaiin.exportConfig()
        val file =
            File(activity.cacheDir, "backups/test-backup.json").apply { parentFile!!.mkdirs() }
        val uri =
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.backup_fileprovider",
                file,
            )
        try {
            runBlocking(Dispatchers.IO) {
                MainApplication.settings.commit {
                    it.putString(
                        Constants.ROUTE_CONTENT_PREFIX + Constants.ALL_ROUTE,
                        "192.0.2.0/24",
                    )
                    it.putBoolean(Constants.ALLOW_LAN_KEY, true)
                }
                ConfigBackup.export(activity, uri)
                MainApplication.settings.commit {
                    it.putString(
                        Constants.ROUTE_CONTENT_PREFIX + Constants.ALL_ROUTE,
                        "198.51.100.0/24",
                    )
                    it.putBoolean(Constants.ALLOW_LAN_KEY, false)
                }
                val (data, preview) = ConfigBackup.read(activity, uri)
                assertTrue(preview.preferences > 0)
                ConfigBackup.restore(data)
            }
            assertEquals(
                "192.0.2.0/24",
                MainApplication.store.getString(
                    Constants.ROUTE_CONTENT_PREFIX + Constants.ALL_ROUTE
                ),
            )
            assertTrue(MainApplication.store.getBoolean(Constants.ALLOW_LAN_KEY))
        } finally {
            runBlocking(Dispatchers.IO) { ConfigBackup.restore(original) }
            file.delete()
        }
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
            compose.runOnUiThread { activity.startService() }
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
            val backup = Yuhaiin.exportConfig()
            val rejected = runCatching { Yuhaiin.importConfig(backup) }.exceptionOrNull()
            assertNotNull("Running VPN must reject import", rejected)
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

package io.github.asutorufa.yuhaiin

import android.content.Context
import android.content.pm.PackageInstaller
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.asutorufa.yuhaiin.update.AndroidUpdateRelease
import io.github.asutorufa.yuhaiin.update.UpdateManager
import io.github.asutorufa.yuhaiin.update.UpdateStage
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class UpdateRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("software_update", Context.MODE_PRIVATE)

    @Test
    fun recreatedManagerRejectsChangedApkWithoutRequestingTheProxy() = runBlocking {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand(
            "appops set ${BuildConfig.APPLICATION_ID} REQUEST_INSTALL_PACKAGES allow"
        )
        val apk =
            File(context.cacheDir, "updates/recovery-test.apk").apply {
                parentFile!!.mkdirs()
                writeText("tampered download")
            }
        try {
            val release =
                AndroidUpdateRelease("0.0.0", "test", "", "", apk.name, "", null, apk.length())
            preferences
                .edit()
                .clear()
                .putString("release", Json.encodeToString(release))
                .putString("phase", "permission")
                .putString("verified_checksum", "0".repeat(64))
                .commit()
            val manager = UpdateManager(context)
            assertEquals(UpdateStage.WAITING_PERMISSION, manager.state.value.stage)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                manager.resumePendingInstall()
            }
            withTimeout(5000) { while (manager.state.value.stage != UpdateStage.ERROR) delay(20) }
            assertEquals("APK checksum mismatch", manager.state.value.error)
        } finally {
            preferences.edit().clear().commit()
            apk.delete()
            // Revoking this app-op kills the instrumented process. Reset on the host after tests.
        }
    }

    @Test
    fun recreatedManagerRecognizesExistingSessionAndClearsFailedInstall() {
        val installer = context.packageManager.packageInstaller
        val session =
            installer.createSession(
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            )
        try {
            preferences
                .edit()
                .clear()
                .putString("phase", "install")
                .putInt("session_id", session)
                .commit()
            val manager = UpdateManager(context)
            assertEquals(UpdateStage.INSTALLING, manager.state.value.stage)
            manager.onInstallResult(PackageInstaller.STATUS_FAILURE_ABORTED, "cancelled")
            assertEquals(UpdateStage.ERROR, manager.state.value.stage)
            assertFalse(preferences.contains("phase"))
            assertFalse(preferences.contains("session_id"))
        } finally {
            installer.abandonSession(session)
            preferences.edit().clear().commit()
        }
    }
}

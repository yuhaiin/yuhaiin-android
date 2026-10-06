package io.github.asutorufa.yuhaiin

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackupUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun backupPageLaunchesSystemFilePickersAndShareSheet() {
        val activity = compose.activity
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val destination = hasText(activity.getString(R.string.backup_title)) and hasClickAction()
        compose.onNode(hasScrollAction()).performScrollToNode(destination)
        compose.onNode(destination).performClick()
        compose
            .onNodeWithText(activity.getString(R.string.backup_export))
            .assertIsDisplayed()
            .performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000))
        device.pressBack()
        compose
            .onNodeWithText(activity.getString(R.string.backup_import))
            .assertIsDisplayed()
            .performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000))
        device.pressBack()
        compose
            .onNodeWithText(activity.getString(R.string.backup_share))
            .assertIsDisplayed()
            .performClick()
        assertTrue(
            device.wait(
                Until.hasObject(
                    By.res("com.android.intentresolver", "content_preview_filename")
                        .textStartsWith("yuhaiin-backup-")
                ),
                10_000,
            )
        )
        device.pressBack()
    }
}

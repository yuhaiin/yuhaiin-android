package io.github.asutorufa.yuhaiin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import io.github.asutorufa.yuhaiin.service.VpnActions
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PrivacyFlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val activity
        get() = compose.activity

    private val consent
        get() = PrivacyConsent.forContext(activity)

    private val device
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun label(id: Int) = activity.getString(id)

    @Before
    fun resetConsent() {
        compose.waitUntil(15_000) { activity.vpnBinder != null }
        activity.vpnBinder!!.stop()
        compose.waitUntil(15_000) { activity.state.value == State.DISCONNECTED }
        consent.revoke()
        runBlocking { MainApplication.installedApps.clear() }
    }

    @After
    fun stop() {
        consent.revoke()
        activity.vpnBinder?.stop()
    }

    private fun open(id: Int, fromHome: Boolean = true) {
        // v2 starts on the Web UI; these native screens live under "Android settings".
        if (fromHome) {
            openNativeSettings(activity)
            compose.waitForIdle()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label(id)))
        compose.onNodeWithText(label(id)).performClick()
    }

    private fun connectWithDisclosure() {
        if (Build.VERSION.SDK_INT >= 33)
            device.executeShellCommand(
                "pm grant ${BuildConfig.APPLICATION_ID} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
        compose.runOnUiThread { activity.startService() }
        compose.onNodeWithText(label(R.string.vpn_disclosure_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.privacy_agree)).performClick()
        compose.waitUntil(20_000) {
            runCatching { device.findObject(By.res("android", "button1"))?.click() }
            activity.state.value == State.CONNECTED
        }
    }

    @Test
    fun cancelBackAndRotationNeverGrantVpnConsent() {
        compose.runOnUiThread { activity.startService() }
        compose.onNodeWithText(label(R.string.vpn_disclosure_title)).assertIsDisplayed()
        assertFalse(consent.has(Purpose.VPN))
        assertEquals(State.DISCONNECTED, activity.state.value)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(label(R.string.vpn_disclosure_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.privacy_not_now)).performClick()
        assertFalse(consent.has(Purpose.VPN))
        compose.runOnUiThread {
            activity.startActivity(VpnActions.activityIntent(activity, VpnActions.CONNECT))
        }
        compose.onNodeWithText(label(R.string.vpn_disclosure_title)).assertIsDisplayed()
        device.pressBack()
        compose.waitUntil(5000) { !activity.vpnDisclosure.value }
        assertFalse(consent.has(Purpose.VPN))
        assertFalse(consent.has(Purpose.INSTALLED_APPS))
        assertEquals(State.DISCONNECTED, activity.state.value)
    }

    @Test
    fun appListRequiresItsOwnConsentBeforeAnyRepositoryAccess() {
        consent.grant(Purpose.VPN)
        assertTrue(
            runCatching { runBlocking { MainApplication.installedApps.load() } }.exceptionOrNull()
                is IllegalStateException
        )
        open(R.string.adv_app_list_title)
        compose.onNodeWithText(label(R.string.apps_disclosure_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.privacy_not_now)).performClick()
        assertFalse(consent.has(Purpose.INSTALLED_APPS))
        open(R.string.adv_app_list_title)
        compose.onNodeWithText(label(R.string.privacy_agree)).performClick()
        assertTrue(consent.has(Purpose.INSTALLED_APPS))
        compose.onNode(hasSetTextAction()).performTextReplacement(BuildConfig.APPLICATION_ID)
        compose.waitUntil(10_000) {
            compose
                .onAllNodes(hasText(BuildConfig.APPLICATION_ID) and isToggleable())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(label(R.string.apps_disclosure_title)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertExists()
    }

    @Test
    fun directBackgroundStartReconnectAndResumeCannotBypassConsent() {
        for (action in listOf(VpnActions.CONNECT, VpnActions.RECONNECT, VpnActions.RESUME)) {
            compose.runOnUiThread {
                activity.error.value = null
                ContextCompat.startForegroundService(activity,
                    Intent(activity, YuhaiinVpnService::class.java).setAction(action)
                )
            }
            compose.waitUntil(5000) {
                activity.error.value == label(R.string.vpn_disclosure_required)
            }
            assertFalse(consent.has(Purpose.VPN))
            assertEquals(State.DISCONNECTED, activity.state.value)
            assertEquals(0L, activity.vpnBinder!!.snapshot().getLong("connectedAt"))
        }
    }

    @Test
    fun withdrawalDisconnectsRealVpnAndNextConnectionAsksAgain() {
        connectWithDisclosure()
        assertTrue(consent.has(Purpose.VPN))
        assertFalse(consent.has(Purpose.INSTALLED_APPS))
        consent.grant(Purpose.INSTALLED_APPS)
        runBlocking { MainApplication.installedApps.load() }
        open(R.string.about)
        open(R.string.privacy_policy_title, fromHome = false)
        open(R.string.privacy_withdraw, fromHome = false)
        compose.onNodeWithText(label(R.string.privacy_withdraw_body)).assertIsDisplayed()
        compose.onAllNodesWithText(label(R.string.privacy_withdraw)).onLast().performClick()
        compose.waitUntil(15_000) {
            !consent.has(Purpose.VPN) &&
                !consent.has(Purpose.INSTALLED_APPS) &&
                activity.state.value == State.DISCONNECTED
        }
        assertTrue(runCatching { runBlocking { MainApplication.installedApps.load() } }.isFailure)
        compose.runOnUiThread { activity.startService() }
        compose.onNodeWithText(label(R.string.vpn_disclosure_title)).assertIsDisplayed()
    }

    @Test
    fun privacyPolicyIsAvailableWithoutVpnOrEitherConsent() {
        open(R.string.about)
        open(R.string.privacy_policy_title, fromHome = false)
        compose.onNodeWithText(label(R.string.privacy_developer_title)).assertIsDisplayed()
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText(label(R.string.privacy_retention_title)))
        compose.onNodeWithText(label(R.string.privacy_retention_title)).assertIsDisplayed()
        assertFalse(consent.has(Purpose.VPN))
        assertFalse(consent.has(Purpose.INSTALLED_APPS))
        assertEquals(State.DISCONNECTED, activity.state.value)
    }

    @Test
    fun cancellingLogSharingDoesNotExportAnything() {
        val file = File(activity.externalCacheDir, "yuhaiin.log")
        file.delete()
        open(R.string.logcat)
        compose.onNodeWithContentDescription(label(R.string.export_logs)).performClick()
        compose.onNodeWithText(label(R.string.log_share_disclosure)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.privacy_not_now)).performClick()
        assertFalse(file.exists())
    }

    @Test
    fun finalManifestKeepsOnlyRequiredPermissions() {
        @Suppress("DEPRECATION")
        val permissions =
            activity.packageManager
                .getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .toSet()
        assertFalse(Manifest.permission.WRITE_SETTINGS in permissions)
        assertFalse(Manifest.permission.CHANGE_NETWORK_STATE in permissions)
        assertTrue(Manifest.permission.QUERY_ALL_PACKAGES in permissions)
        assertTrue(Manifest.permission.REQUEST_INSTALL_PACKAGES in permissions)
        assertTrue(Manifest.permission.ACCESS_NETWORK_STATE in permissions)
    }
}

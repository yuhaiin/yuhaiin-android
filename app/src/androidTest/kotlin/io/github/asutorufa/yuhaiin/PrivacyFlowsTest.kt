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
        compose.waitUntil(20_000) { activity.session.value.ready }
        assertTrue(
            runCatching { runBlocking { MainApplication.installedApps.load() } }.exceptionOrNull()
                is IllegalStateException
        )
        // The Web UI's per-app list is refused until the user consents in the app.
        val (refused, body) = platformApi(activity, "GET", "apps")
        assertEquals(409, refused)
        assertTrue(body.contains("consent_required"))
        compose.runOnUiThread { activity.requestAppsConsent() }
        compose.onNodeWithText(label(R.string.apps_disclosure_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.privacy_not_now)).performClick()
        assertFalse(consent.has(Purpose.INSTALLED_APPS))
        compose.runOnUiThread { activity.requestAppsConsent() }
        compose.onNodeWithText(label(R.string.privacy_agree)).performClick()
        assertTrue(consent.has(Purpose.INSTALLED_APPS))
        val (ok, apps) = platformApi(activity, "GET", "apps")
        assertEquals(200, ok)
        assertTrue(apps.contains(BuildConfig.APPLICATION_ID))
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
        // The Web UI's privacy page withdraws through the platform API.
        val (code, _) = platformApi(activity, "POST", "privacy/withdraw", "{}")
        assertEquals(200, code)
        compose.waitUntil(15_000) {
            !consent.has(Purpose.VPN) &&
                !consent.has(Purpose.INSTALLED_APPS) &&
                activity.state.value == State.DISCONNECTED
        }
        compose.runOnUiThread { activity.startService() }
        compose.onNodeWithText(label(R.string.vpn_disclosure_title)).assertIsDisplayed()
    }

    @Test
    fun privacyPolicyIsAvailableWithoutVpnOrEitherConsent() {
        compose.waitUntil(20_000) { activity.session.value.ready }
        val (code, body) = platformApi(activity, "GET", "privacy")
        assertEquals(200, code)
        assertTrue(body.contains(label(R.string.privacy_developer_title)))
        assertTrue(body.contains(label(R.string.privacy_retention_title)))
        assertTrue(body.contains("\"vpn\":false"))
        assertFalse(consent.has(Purpose.VPN))
        assertFalse(consent.has(Purpose.INSTALLED_APPS))
        assertEquals(State.DISCONNECTED, activity.state.value)
    }

    @Test
    fun platformApiRefusesRequestsWithoutTheWebViewSession() {
        compose.waitUntil(20_000) { activity.session.value.ready }
        val port = activity.session.value.port
        val conn =
            java.net.URL("http://127.0.0.1:$port/android/v1/privacy/withdraw").openConnection()
                as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Yuhaiin-Platform-Request", "1")
        conn.outputStream.use { it.write("{}".toByteArray()) }
        assertEquals(401, conn.responseCode)
        conn.disconnect()
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

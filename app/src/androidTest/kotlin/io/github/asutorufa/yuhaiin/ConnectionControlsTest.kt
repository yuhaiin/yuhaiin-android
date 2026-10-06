package io.github.asutorufa.yuhaiin

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.asutorufa.yuhaiin.data.Settings
import io.github.asutorufa.yuhaiin.service.VpnActions
import io.github.asutorufa.yuhaiin.service.VpnStatus
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the real remote service, native core and system notification PendingIntents. */
class ConnectionControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val activity
        get() = compose.activity

    private fun waitFor(timeout: Long = 20_000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
        assertTrue("Condition did not become true within $timeout ms", condition())
    }

    private fun snapshot() = VpnStatus.fromBundle(activity.vpnBinder!!.snapshot())

    private fun connect() {
        runBlocking(Dispatchers.IO) { MainApplication.settings.flush() }
        waitFor { activity.vpnBinder != null }
        if (Build.VERSION.SDK_INT >= 33)
            device.executeShellCommand(
                "pm grant ${BuildConfig.APPLICATION_ID} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
        compose.runOnUiThread { activity.startService() }
        waitFor {
            runCatching { device.findObject(By.res("android", "button1"))?.click() }
            activity.state.value == State.CONNECTED
        }
    }

    private fun notification() =
        activity
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .single { it.id == 1 }
            .notification

    @After
    fun stop() {
        runCatching { activity.vpnBinder?.stop() }
    }

    @Test
    fun notificationReconnectAndSnoozeCancellationUseOneService() {
        connect()
        val initial = snapshot()
        assertTrue(initial.connectedAt > 0)
        assertTrue(initial.mtu > 0)
        assertTrue(initial.ipv4.isNotBlank())
        assertTrue(initial.ipv6.isNotBlank())
        assertTrue(initial.route.isNotBlank())
        assertTrue(initial.network.isNotBlank())
        waitFor { snapshot().speed.contains("↓") && snapshot().speed.contains("↑") }
        assertEquals(3, notification().actions.size)
        val reconnect = notification().actions[1].actionIntent
        reconnect.send()
        waitFor {
            snapshot().connectedAt > initial.connectedAt && activity.state.value == State.CONNECTED
        }
        for (minutes in listOf(5, 15, 30)) {
            activity.vpnBinder!!.snooze(minutes)
            waitFor { snapshot().resumeAt > 0 && activity.state.value == State.DISCONNECTED }
            val remaining = snapshot().resumeAt - System.currentTimeMillis()
            assertTrue(remaining in (minutes * 60_000L - 10_000)..(minutes * 60_000L))
            notification().actions[1].actionIntent.send()
            waitFor { activity.state.value == State.CONNECTED && snapshot().resumeAt == 0L }
        }
        activity.vpnBinder!!.snooze(5)
        waitFor { snapshot().resumeAt > 0 && activity.state.value == State.DISCONNECTED }
        notification().actions[0].actionIntent.send()
        waitFor { snapshot().resumeAt == 0L && activity.state.value == State.DISCONNECTED }
        waitFor {
            activity.getSystemService(NotificationManager::class.java).activeNotifications.none {
                it.id == 1
            }
        }
        assertEquals(0L, snapshot().connectedAt)
        activity.startService(
            Intent(activity, YuhaiinVpnService::class.java).setAction(VpnActions.RESUME)
        )
        SystemClock.sleep(500)
        assertEquals(State.DISCONNECTED, activity.state.value)
        assertEquals(0L, snapshot().resumeAt)
    }

    @Test
    fun fiveMinuteSnoozeResumesAutomatically() {
        connect()
        val previous = snapshot().connectedAt
        activity.vpnBinder!!.snooze(5)
        waitFor { snapshot().resumeAt > 0 && activity.state.value == State.DISCONNECTED }
        // Actual production interval, no test-only timer override or clock manipulation.
        waitFor(320_000) { activity.state.value == State.CONNECTED && snapshot().resumeAt == 0L }
        assertTrue(snapshot().connectedAt > previous)
    }

    @Test
    fun launcherActionsAndMeteredPreferenceReachTheVpn() {
        connect()
        if (Build.VERSION.SDK_INT >= 25) {
            val shortcuts = activity.getSystemService(ShortcutManager::class.java).manifestShortcuts
            assertEquals(setOf("connect", "disconnect", "routes"), shortcuts.map { it.id }.toSet())
        }
        val previous = MainApplication.store.getString(Constants.METERED_MODE_KEY)
        try {
            MainApplication.settings.set(Settings.metered, "metered")
            runBlocking(Dispatchers.IO) { MainApplication.settings.flush() }
            val before = snapshot().connectedAt
            compose.runOnUiThread { activity.reconnect() }
            waitFor { snapshot().connectedAt > before && activity.state.value == State.CONNECTED }
            val connectivity = activity.getSystemService(ConnectivityManager::class.java)
            waitFor {
                connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)?.let {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                } == true
            }
            compose.runOnUiThread {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .setAction(VpnActions.DISCONNECT)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            waitFor { activity.state.value == State.DISCONNECTED }
            compose.runOnUiThread {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .setAction(VpnActions.CONNECT)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            waitFor { activity.state.value == State.CONNECTED }
        } finally {
            runBlocking(Dispatchers.IO) {
                MainApplication.settings.commit {
                    it.putString(Constants.METERED_MODE_KEY, previous)
                }
            }
        }
    }

    @Test
    fun quickSettingsTileAndWidgetControlTheRemoteService() {
        connect()
        val component = "${BuildConfig.APPLICATION_ID}/.service.VpnTileService"
        val oldTiles =
            device.executeShellCommand("settings get secure sysui_qs_tiles").trim().trim('\'')
        val route = MainApplication.store.getString(Constants.ROUTE_KEY)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val host = android.appwidget.AppWidgetHost(activity, 20261006)
        var widgetId = 0
        try {
            // Use SystemUI's add flow so onTileAdded runs before restricting the test panel.
            device.executeShellCommand("cmd statusbar add-tile $component")
            device.executeShellCommand("settings put secure sysui_qs_tiles custom($component)")
            device.executeShellCommand("cmd statusbar expand-settings")
            waitFor { device.hasObject(By.text(activity.getString(R.string.status_connected))) }
            device.executeShellCommand("cmd statusbar click-tile $component")
            waitFor { activity.state.value == State.DISCONNECTED }
            waitFor { device.hasObject(By.text(activity.getString(R.string.status_disconnected))) }
            device.executeShellCommand("cmd statusbar click-tile $component")
            waitFor { activity.state.value == State.CONNECTED }
            device.executeShellCommand("cmd statusbar collapse")

            val manager = android.appwidget.AppWidgetManager.getInstance(activity)
            instrumentation.uiAutomation.adoptShellPermissionIdentity(
                "android.permission.BIND_APPWIDGET"
            )
            widgetId = host.allocateAppWidgetId()
            assertTrue(
                manager.bindAppWidgetIdIfAllowed(
                    widgetId,
                    android.content.ComponentName(
                        activity,
                        io.github.asutorufa.yuhaiin.service.VpnWidget::class.java,
                    ),
                )
            )
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            var view: android.appwidget.AppWidgetHostView? = null
            instrumentation.runOnMainSync {
                host.startListening()
                view = host.createView(activity, widgetId, manager.getAppWidgetInfo(widgetId))
                activity.addContentView(
                    view,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        400,
                    ),
                )
            }
            fun widgetText(): String? {
                var text: String? = null
                instrumentation.runOnMainSync {
                    text =
                        view
                            ?.findViewById<android.widget.TextView>(R.id.widget_status)
                            ?.text
                            ?.toString()
                }
                return text
            }
            waitFor { widgetText() == activity.getString(R.string.status_connected) }
            instrumentation.runOnMainSync {
                view!!.findViewById<android.view.View>(R.id.widget_toggle).performClick()
            }
            waitFor { activity.state.value == State.DISCONNECTED }
            waitFor { widgetText() == activity.getString(R.string.status_disconnected) }
            instrumentation.runOnMainSync {
                view!!.findViewById<android.view.View>(R.id.widget_toggle).performClick()
            }
            waitFor { activity.state.value == State.CONNECTED }
            val before = snapshot().connectedAt
            instrumentation.runOnMainSync {
                view!!.findViewById<android.view.View>(R.id.widget_routes).performClick()
            }
            waitFor { activity.navigationAction.value == VpnActions.ROUTES }
            compose.waitForIdle()
            compose
                .onNodeWithText(activity.getString(R.string.route_non_local_label))
                .performClick()
            waitFor {
                snapshot().route == Constants.NON_LOCAL_ROUTE && snapshot().connectedAt > before
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            host.stopListening()
            if (widgetId != 0) host.deleteAppWidgetId(widgetId)
            device.executeShellCommand("cmd statusbar collapse")
            device.executeShellCommand("settings put secure sysui_qs_tiles $oldTiles")
            runBlocking(Dispatchers.IO) {
                MainApplication.settings.commit { it.putString(Constants.ROUTE_KEY, route) }
            }
        }
    }
}

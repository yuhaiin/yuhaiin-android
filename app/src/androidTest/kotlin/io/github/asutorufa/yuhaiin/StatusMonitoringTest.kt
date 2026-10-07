package io.github.asutorufa.yuhaiin

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.asutorufa.yuhaiin.data.Settings
import io.github.asutorufa.yuhaiin.service.*
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Verifies collection demand through the real :bg service rather than service internals. */
class StatusMonitoringTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun waitFor(timeout: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue("Condition did not become true within $timeout ms", condition())
    }

    private fun connect(): IYuhaiinVpnBinder {
        val activity = compose.activity
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
        val binder = activity.vpnBinder!!
        waitFor { status(binder).speed.contains("↓") }
        waitFor(30_000) {
            !status(binder).healthChecking &&
                decodeNativeHealth(status(binder).nativeHealth) != null
        }
        return binder
    }

    private fun status(binder: IYuhaiinVpnBinder) = VpnStatus.fromBundle(binder.snapshot())

    private fun notification() =
        instrumentation.targetContext
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .single { it.id == 1 }

    private fun speedPreference(value: Boolean) {
        MainApplication.settings.set(Settings.speed, value)
        runBlocking(Dispatchers.IO) { MainApplication.settings.flush() }
    }

    private fun transferWhileHidden(binder: IYuhaiinVpnBinder): Int {
        val payload = ByteArray(64 * 1024) { 42 }
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5000
                val response = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val request = socket.getInputStream().bufferedReader()
                        while (!request.readLine().isNullOrEmpty()) {}
                        socket.getOutputStream().apply {
                            write(
                                "HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray()
                            )
                            write(payload)
                            flush()
                        }
                    }
                }
                assertArrayEquals(
                    payload,
                    binder.proxyGet("http://127.0.0.1:${server.localPort}/traffic"),
                )
                response.get(5, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
        }
        return payload.size
    }

    @Test
    fun backgroundWithoutConsumersStopsSnapshotsAndPeriodicHealthThenResumes() {
        val previous = MainApplication.store.getBoolean(Constants.NOTIFICATION_SPEED_KEY)
        var binder: IYuhaiinVpnBinder? = null
        try {
            speedPreference(false)
            assertFalse(VpnWidget.isInstalled(instrumentation.targetContext))
            val service = connect().also { binder = it }
            // UI summary ticks must not repeatedly repost a notification with hidden speed.
            val postedAt = notification().postTime
            SystemClock.sleep(6000)
            assertEquals(postedAt, notification().postTime)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            waitFor { status(service).speed.isEmpty() }
            val hidden = status(service)
            val transferred = transferWhileHidden(service)
            // Longer than the previous 60-second background health interval.
            SystemClock.sleep(65_000)
            assertEquals(State.CONNECTED, status(service).state)
            assertEquals(hidden.nativeStatus, status(service).nativeStatus)
            assertEquals(hidden.nativeHealth, status(service).nativeHealth)
            assertEquals("", status(service).speed)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor { status(service).nativeStatus != hidden.nativeStatus }
            waitFor { status(service).speed.contains("↓") }
            waitFor(30_000) {
                !status(service).healthChecking &&
                    status(service).nativeHealth != hidden.nativeHealth
            }
            assertEquals(hidden.connectedAt, status(service).connectedAt)
            assertTrue(
                decodeNativeStatus(status(service).nativeStatus)!!.session.download.toULong() >=
                    decodeNativeStatus(hidden.nativeStatus)!!.session.download.toULong() +
                        transferred.toULong()
            )
            service.stop()
            waitFor { status(service).state == State.DISCONNECTED }
            assertNotNull(decodeNativeStatus(status(service).nativeStatus))
        } finally {
            runCatching { binder?.stop() }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            speedPreference(previous)
        }
    }

    @Test
    fun notificationAndWidgetDemandSampleOnlyWhileScreenIsOn() {
        val previous = MainApplication.store.getBoolean(Constants.NOTIFICATION_SPEED_KEY)
        val context = instrumentation.targetContext
        val host = AppWidgetHost(context, 20261007)
        var widgetId = 0
        var binder: IYuhaiinVpnBinder? = null
        try {
            speedPreference(true)
            val service = connect().also { binder = it }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            waitFor { status(service).speed.isEmpty() }
            val hidden = status(service)
            waitFor(15_000) { status(service).speed.contains("↓") }
            assertEquals(hidden.nativeStatus, status(service).nativeStatus)
            assertEquals(hidden.nativeHealth, status(service).nativeHealth)
            waitFor {
                notification()
                    .notification
                    .extras
                    .getString(Notification.EXTRA_TEXT)
                    ?.contains("↓") == true
            }
            device.sleep()
            waitFor { status(service).speed.isEmpty() }
            val sleeping = status(service)
            SystemClock.sleep(15_000)
            assertEquals(sleeping.nativeStatus, status(service).nativeStatus)
            assertEquals(sleeping.speed, status(service).speed)
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            waitFor(15_000) { status(service).speed.contains("↓") }
            speedPreference(false) // persisted settings broadcast must stop the remote sampler
            waitFor { status(service).speed.isEmpty() }
            // Service state and NotificationManager publication cross separate Binder calls.
            waitFor {
                notification()
                    .notification
                    .extras
                    .getString(Notification.EXTRA_TEXT)
                    ?.contains("↓") != true
            }
            instrumentation.uiAutomation.adoptShellPermissionIdentity(
                "android.permission.BIND_APPWIDGET"
            )
            widgetId = host.allocateAppWidgetId()
            assertTrue(
                AppWidgetManager.getInstance(context)
                    .bindAppWidgetIdIfAllowed(
                        widgetId,
                        ComponentName(context, VpnWidget::class.java),
                    )
            )
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            waitFor(20_000) { status(service).speed.contains("↓") }
            assertEquals(hidden.nativeStatus, status(service).nativeStatus)
            host.deleteAppWidgetId(widgetId)
            widgetId = 0
            waitFor { status(service).speed.isEmpty() }
            SystemClock.sleep(12_000)
            assertEquals("", status(service).speed)
            assertEquals(hidden.nativeStatus, status(service).nativeStatus)
            assertEquals(hidden.nativeHealth, status(service).nativeHealth)
            val transferred = transferWhileHidden(service)
            service.stop()
            waitFor { status(service).state == State.DISCONNECTED }
            assertTrue(
                decodeNativeStatus(status(service).nativeStatus)!!.session.download.toULong() >=
                    decodeNativeStatus(hidden.nativeStatus)!!.session.download.toULong() +
                        transferred.toULong()
            )
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            if (widgetId != 0) host.deleteAppWidgetId(widgetId)
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            runCatching { binder?.stop() }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            speedPreference(previous)
        }
    }
}

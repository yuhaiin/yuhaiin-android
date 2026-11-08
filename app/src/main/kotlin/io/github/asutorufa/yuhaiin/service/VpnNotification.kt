package io.github.asutorufa.yuhaiin.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R

class VpnNotification(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private var published: Triple<Int, String, Long>? = null

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26)
            context
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                            context.packageName,
                            context.getString(R.string.channel_name),
                            NotificationManager.IMPORTANCE_LOW,
                        )
                        .apply {
                            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                        }
                )
    }

    private fun detail(status: VpnStatus) =
        status.detail(
            context,
            showSpeed = MainApplication.store.getBoolean(Constants.NOTIFICATION_SPEED_KEY),
        )

    /** Elapsed-realtime start of the running connection, or 0 when no duration is shown. */
    private fun chronometerBase(status: VpnStatus) =
        if (status.state == YuhaiinVpnService.Companion.State.CONNECTED && status.resumeAt == 0L)
            status.connectedAt
        else 0L

    fun builder(status: VpnStatus): NotificationCompat.Builder {
        val paused = status.resumeAt > 0
        val vpnUp =
            status.state == YuhaiinVpnService.Companion.State.CONNECTED ||
                status.state == YuhaiinVpnService.Companion.State.CONNECTING
        val builder =
            NotificationCompat.Builder(context, context.packageName)
                .setContentTitle(context.getString(status.titleResource()))
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(VpnActions.activity(context, VpnActions.HOME))
        when {
            paused ->
                builder
                    .addAction(
                        0,
                        context.getString(R.string.cancel_resume),
                        VpnActions.service(context, VpnActions.DISCONNECT),
                    )
                    .addAction(
                        0,
                        context.getString(R.string.resume_now),
                        VpnActions.service(context, VpnActions.RECONNECT),
                    )
            vpnUp ->
                // Stopping the VPN keeps the core and its management UI running.
                builder
                    .addAction(
                        0,
                        context.getString(R.string.Stop),
                        VpnActions.service(context, VpnActions.DISCONNECT),
                    )
                    .addAction(
                        0,
                        context.getString(R.string.reconnect),
                        VpnActions.service(context, VpnActions.RECONNECT),
                    )
            else ->
                builder
                    .addAction(
                        0,
                        context.getString(R.string.connect_vpn),
                        VpnActions.activity(context, VpnActions.CONNECT),
                    )
                    .addAction(
                        0,
                        context.getString(R.string.exit_core),
                        VpnActions.service(context, VpnActions.EXIT),
                    )
        }
        val base = chronometerBase(status)
        if (base > 0) {
            builder
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - base))
        }
        detail(status).takeIf { it.isNotEmpty() }?.let(builder::setContentText)
        return builder
    }

    private fun canShow(): Boolean {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return false
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 ||
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(context.packageName)
                ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun needsSpeedUpdates(): Boolean =
        MainApplication.store.getBoolean(Constants.NOTIFICATION_SPEED_KEY) && canShow()

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastNotifyAt = 0L
    private var pending: VpnStatus? = null

    /**
     * Android drops notification updates above a few per second. Updates closer than
     * [MIN_INTERVAL_MS] are coalesced and the latest status is posted afterwards, so the final
     * state (for example "VPN off" after a burst of reconnects) is never lost.
     */
    fun update(status: VpnStatus) {
        val wait = lastNotifyAt + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait > 0) {
            val scheduled = pending != null
            pending = status
            if (!scheduled)
                handler.postDelayed(
                    {
                        val latest = pending
                        pending = null
                        if (latest != null) update(latest)
                    },
                    wait,
                )
            return
        }
        pending = null
        if (!canShow()) {
            published = null
            return
        }
        val display =
            Triple(
                status.titleResource() * 31 + status.state.ordinal,
                detail(status),
                chronometerBase(status),
            )
        if (display == published) return
        try {
            manager.notify(1, builder(status).build())
            published = display
            lastNotifyAt = SystemClock.elapsedRealtime()
        } catch (error: SecurityException) {
            published = null
            Log.w("VpnNotification", "Notification permission changed", error)
        }
    }

    /** startForeground() posts directly; count it so the next update is not dropped. */
    fun posted(status: VpnStatus) {
        published = null
        lastNotifyAt = SystemClock.elapsedRealtime()
        update(status)
    }

    private companion object {
        const val MIN_INTERVAL_MS = 300L
    }
}

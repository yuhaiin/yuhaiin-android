package io.github.asutorufa.yuhaiin.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R

class VpnNotification(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private var published: Triple<YuhaiinVpnService.Companion.State, Long, String?>? = null

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

    fun builder(status: VpnStatus): NotificationCompat.Builder {
        val paused = status.resumeAt > 0
        val builder =
            NotificationCompat.Builder(context, context.packageName)
                .setContentTitle(
                    context.getString(
                        if (paused) R.string.status_paused else status.state.labelResource()
                    )
                )
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(VpnActions.activity(context, VpnActions.DASHBOARD))
                .addAction(
                    0,
                    context.getString(if (paused) R.string.cancel_resume else R.string.Stop),
                    VpnActions.service(context, VpnActions.DISCONNECT),
                )
                .addAction(
                    0,
                    context.getString(if (paused) R.string.resume_now else R.string.reconnect),
                    VpnActions.service(context, VpnActions.RECONNECT),
                )
                .addAction(
                    0,
                    context.getString(R.string.open_dashboard),
                    VpnActions.activity(context, VpnActions.DASHBOARD),
                )
        if (paused)
            builder.setContentText(
                context.getString(
                    R.string.resume_time,
                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        .format(java.util.Date(status.resumeAt)),
                )
            )
        else if (MainApplication.store.getBoolean(Constants.NOTIFICATION_SPEED_KEY))
            builder.setContentText(status.speed)
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

    fun update(status: VpnStatus) {
        if (!canShow()) {
            published = null
            return
        }
        val display =
            Triple(
                status.state,
                status.resumeAt,
                if (MainApplication.store.getBoolean(Constants.NOTIFICATION_SPEED_KEY)) status.speed
                else null,
            )
        if (display == published) return
        try {
            manager.notify(1, builder(status).build())
            published = display
        } catch (error: SecurityException) {
            published = null
            Log.w("VpnNotification", "Notification permission changed", error)
        }
    }
}

package io.github.asutorufa.yuhaiin.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.R
import yuhaiin.NotifySpped

class VpnNotification(private val context: Context) : NotifySpped {
    private val manager = NotificationManagerCompat.from(context)

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

    fun builder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, context.packageName)
            .setContentTitle(context.getString(R.string.yuhaiin_running))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )

    override fun notifyEnable(): Boolean = manager.areNotificationsEnabled()

    override fun notify(speed: String) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return
        if (notifyEnable())
            manager.notify(
                1,
                builder()
                    .setContentTitle("${context.getString(R.string.yuhaiin_running)} $speed")
                    .build(),
            )
    }
}

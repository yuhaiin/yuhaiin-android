package io.github.asutorufa.yuhaiin.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.asutorufa.yuhaiin.MainActivity

/** Explicit actions, with distinct PendingIntent identities for every control surface. */
object VpnActions {
    const val CONNECT = "io.github.asutorufa.yuhaiin.CONNECT"
    const val DISCONNECT = "io.github.asutorufa.yuhaiin.DISCONNECT"
    const val RECONNECT = "io.github.asutorufa.yuhaiin.RECONNECT"
    const val DASHBOARD = "io.github.asutorufa.yuhaiin.DASHBOARD"
    const val ROUTES = "io.github.asutorufa.yuhaiin.ROUTES"
    const val REFRESH_MONITORING = "io.github.asutorufa.yuhaiin.REFRESH_MONITORING"
    const val RESUME = "io.github.asutorufa.yuhaiin.RESUME"

    fun activityIntent(context: Context, action: String) =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun activity(context: Context, action: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            activityIntent(context, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun service(context: Context, action: String): PendingIntent =
        PendingIntent.getService(
            context,
            0,
            Intent(context, YuhaiinVpnService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

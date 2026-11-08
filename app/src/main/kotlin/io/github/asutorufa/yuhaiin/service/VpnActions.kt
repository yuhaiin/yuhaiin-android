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
    const val HOME = "io.github.asutorufa.yuhaiin.HOME"
    const val DASHBOARD = "io.github.asutorufa.yuhaiin.DASHBOARD"
    const val ROUTES = "io.github.asutorufa.yuhaiin.ROUTES"
    /** Opens the native Android settings kept next to the Web UI. */
    const val SETTINGS = "io.github.asutorufa.yuhaiin.SETTINGS"
    const val REFRESH_MONITORING = "io.github.asutorufa.yuhaiin.REFRESH_MONITORING"
    const val RESUME = "io.github.asutorufa.yuhaiin.RESUME"
    /** Always-on VPN start forwarded by [YuhaiinVpnService]. */
    const val CONNECT_ALWAYS_ON = "io.github.asutorufa.yuhaiin.CONNECT_ALWAYS_ON"
    const val EXIT = YuhaiinCoreService.STOP_CORE

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

    /** Commands for the core service; it promotes itself to the foreground on every start. */
    fun service(context: Context, action: String): PendingIntent {
        val intent = YuhaiinCoreService.intent(context, action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (android.os.Build.VERSION.SDK_INT >= 26)
            PendingIntent.getForegroundService(context, 0, intent, flags)
        else PendingIntent.getService(context, 0, intent, flags)
    }
}

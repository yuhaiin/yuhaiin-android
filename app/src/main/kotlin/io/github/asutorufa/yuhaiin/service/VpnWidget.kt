package io.github.asutorufa.yuhaiin.service

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State

class VpnWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Binding observes the service without connecting a VPN or starting a foreground service.
        val appContext = context.applicationContext
        val pending = goAsync()
        val handler = android.os.Handler(appContext.mainLooper)
        var finished = false
        var bound = false
        lateinit var connection: ServiceConnection
        fun finish() {
            if (finished) return
            finished = true
            if (bound) appContext.unbindService(connection)
            pending.finish()
        }
        connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    runCatching {
                        val service = IYuhaiinVpnBinder.Stub.asInterface(binder)
                        service.refreshMonitoring()
                        VpnStatus.fromBundle(service.snapshot())
                    }
                        .onSuccess { update(appContext, it) }
                    finish()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    finish()
                }

                override fun onNullBinding(name: ComponentName) {
                    finish()
                }

                override fun onBindingDied(name: ComponentName) {
                    finish()
                }
            }
        bound =
            appContext.bindService(
                Intent(appContext, YuhaiinVpnService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        if (!bound) finish() else handler.postDelayed({ finish() }, 8000)
    }

    override fun onDisabled(context: Context) {
        context.sendBroadcast(Intent(VpnActions.REFRESH_MONITORING).setPackage(context.packageName))
    }

    companion object {
        fun isInstalled(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, VpnWidget::class.java))
                .isNotEmpty()

        /** Everything [update] renders from a status; the service republishes when it changes. */
        data class Display(
            val title: Int,
            val detail: String,
            val toggle: VpnToggle,
            val active: Boolean,
        )

        fun display(context: Context, status: VpnStatus) =
            Display(
                status.titleResource(),
                status.detail(context, showSpeed = true),
                status.toggle(),
                status.state == State.CONNECTED && status.resumeAt == 0L,
            )

        fun update(context: Context, status: VpnStatus) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VpnWidget::class.java))
            if (ids.isEmpty()) return
            val display = display(context, status)
            // Hosts reapply an update onto the existing views, so every state has to set every
            // property it changes rather than rely on the layout's defaults.
            val views =
                RemoteViews(context.packageName, R.layout.vpn_widget).apply {
                    setTextViewText(R.id.widget_status, context.getString(display.title))
                    setTextViewText(R.id.widget_speed, display.detail)
                    setViewVisibility(
                        R.id.widget_speed,
                        if (display.detail.isEmpty()) View.GONE else View.VISIBLE,
                    )
                    setInt(R.id.widget_icon, "setImageAlpha", if (display.active) 255 else 97)
                    setTextViewText(
                        R.id.widget_toggle,
                        context.getString(
                            when (display.toggle) {
                                VpnToggle.CONNECT -> R.string.Connect
                                VpnToggle.RESUME -> R.string.resume_now
                                VpnToggle.STOP,
                                VpnToggle.STOPPING -> R.string.Stop
                            }
                        ),
                    )
                    setBoolean(
                        R.id.widget_toggle,
                        "setEnabled",
                        display.toggle != VpnToggle.STOPPING,
                    )
                    setOnClickPendingIntent(
                        R.id.widget_toggle,
                        when (display.toggle) {
                            // Only connecting can need the VPN consent dialog.
                            VpnToggle.CONNECT -> VpnActions.activity(context, VpnActions.CONNECT)
                            VpnToggle.RESUME -> VpnActions.service(context, VpnActions.RECONNECT)
                            VpnToggle.STOP -> VpnActions.service(context, VpnActions.DISCONNECT)
                            VpnToggle.STOPPING -> null
                        },
                    )
                    setOnClickPendingIntent(
                        R.id.widget_summary,
                        VpnActions.activity(context, VpnActions.HOME),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_routes,
                        VpnActions.activity(context, VpnActions.ROUTES),
                    )
                }
            manager.updateAppWidget(ids, views)
        }
    }
}

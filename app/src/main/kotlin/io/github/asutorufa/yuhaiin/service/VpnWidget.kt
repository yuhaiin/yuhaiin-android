package io.github.asutorufa.yuhaiin.service

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
                        VpnStatus.fromBundle(IYuhaiinVpnBinder.Stub.asInterface(binder).snapshot())
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

    companion object {
        fun update(context: Context, status: VpnStatus) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VpnWidget::class.java))
            if (ids.isEmpty()) return
            val connected = status.state == State.CONNECTED
            val views =
                RemoteViews(context.packageName, R.layout.vpn_widget).apply {
                    setTextViewText(
                        R.id.widget_status,
                        context.getString(
                            if (status.resumeAt > 0) R.string.status_paused
                            else status.state.labelResource()
                        ),
                    )
                    setTextViewText(R.id.widget_speed, status.speed)
                    setTextViewText(
                        R.id.widget_toggle,
                        context.getString(if (connected) R.string.Stop else R.string.Connect),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_toggle,
                        VpnActions.activity(
                            context,
                            if (connected) VpnActions.DISCONNECT else VpnActions.CONNECT,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_status,
                        VpnActions.activity(context, VpnActions.DASHBOARD),
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

package io.github.asutorufa.yuhaiin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService

class BootReceiver : BroadcastReceiver() {
    private val tag = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        val autoConnect = MainApplication.store.getBoolean("auto_connect")
        if (
            Intent.ACTION_BOOT_COMPLETED == intent.action
            && autoConnect
            && VpnService.prepare(context) == null
            && (context.applicationContext as MainApplication).vpnBinder?.isRunning() != true
        ) {
            Log.d(tag, "starting VPN service on boot")
            ContextCompat.startForegroundService(
                context,
                Intent(context, YuhaiinVpnService::class.java)
            )
        }
    }
}

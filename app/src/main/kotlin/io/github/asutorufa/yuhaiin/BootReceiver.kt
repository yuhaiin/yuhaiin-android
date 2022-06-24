package io.github.asutorufa.yuhaiin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.database.Manager
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService

class BootReceiver : BroadcastReceiver() {
    private val tag = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            if (Manager.profile.autoConnect && VpnService.prepare(context) == null) {
                Log.d(tag, "starting VPN service on boot")
                ContextCompat.startForegroundService(
                    context, Intent(context, YuhaiinVpnService::class.java)
                )
            }
        }
    }
}
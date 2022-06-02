package io.github.asutorufa.yuhaiin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG

class BootReceiver : BroadcastReceiver() {
    private val tag = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val db = MainApplication.db.ProfileDao()
            val p = db.getProfileByName(db.getLastProfile() ?: "Default")!!

            if (p.autoConnect && VpnService.prepare(context) == null) {
                if (DEBUG) {
                    Log.d(tag, "starting VPN service on boot")
                }
                ContextCompat.startForegroundService(
                    context, Intent(context, YuhaiinVpnService::class.java)
                )
            }
        }
    }
}
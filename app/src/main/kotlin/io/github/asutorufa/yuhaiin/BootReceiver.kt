package io.github.asutorufa.yuhaiin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import io.github.asutorufa.yuhaiin.BuildConfig.DEBUG
import io.github.asutorufa.yuhaiin.util.Utility

class BootReceiver : BroadcastReceiver() {
    private val TAG = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val db = MainApplication.db.ProfileDao()
            val p = db.getProfileByName(db.getLastProfile() ?: "Default")!!

            if (p.autoConnect && VpnService.prepare(context) == null) {
                if (DEBUG) {
                    Log.d(TAG, "starting VPN service on boot")
                }
                Utility.startVpn(context)
            }
        }
    }
}
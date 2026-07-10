package io.github.asutorufa.yuhaiin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService

class BootReceiver : BroadcastReceiver() {
    private val tag = this.javaClass.simpleName

    private fun isCharging(context: Context): Boolean {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun shouldAutoConnect(context: Context): Boolean {
        val policy = MainApplication.store
            .getString(Constants.BOOT_CONNECT_POLICY_KEY)
            .ifBlank { "always" }

        return when (policy) {
            "charging_only" -> isCharging(context)
            "skip_in_power_save" -> {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isPowerSaveMode != true
            }
            else -> true
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val autoConnect = MainApplication.store.getBoolean(Constants.AUTO_CONNECT_KEY)
        if (
            Intent.ACTION_BOOT_COMPLETED == intent.action
            && autoConnect
            && shouldAutoConnect(context)
            && VpnService.prepare(context) == null
        ) {
            Log.d(tag, "starting VPN service on boot")
            ContextCompat.startForegroundService(
                context,
                Intent(context, YuhaiinVpnService::class.java)
            )
        } else if (Intent.ACTION_BOOT_COMPLETED == intent.action && autoConnect) {
            Log.d(tag, "skipping VPN service start on boot due to boot connect policy")
        }
    }
}

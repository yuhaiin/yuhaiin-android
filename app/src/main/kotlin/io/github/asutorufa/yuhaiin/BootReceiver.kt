package io.github.asutorufa.yuhaiin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService

class BootReceiver : BroadcastReceiver() {
    private val tag = this.javaClass.simpleName

    private fun isCharging(context: Context): Boolean {
        val batteryStatus =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun shouldAutoConnect(context: Context): Boolean {
        val policy =
            MainApplication.store.getString(Constants.BOOT_CONNECT_POLICY_KEY).ifBlank { "always" }

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
        if (!MainApplication.initialized) {
            Log.d(tag, "ignoring auto-connect broadcast in the restricted backup process")
            return
        }
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        if (!MainApplication.store.getBoolean(Constants.AUTO_CONNECT_KEY)) {
            return
        }

        if (!shouldAutoConnect(context)) {
            Log.d(tag, "skipping VPN auto-connect for $action due to connect policy")
            return
        }

        if (VpnService.prepare(context) != null) {
            Log.d(tag, "skipping VPN auto-connect for $action because permission is unavailable")
            return
        }

        Log.d(tag, "starting VPN service for $action")
        ContextCompat.startForegroundService(
            context,
            Intent(context, YuhaiinVpnService::class.java),
        )
    }
}

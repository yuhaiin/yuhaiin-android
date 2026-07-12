package io.github.asutorufa.yuhaiin.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.github.asutorufa.yuhaiin.MainApplication

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateManager.INSTALL_RESULT_ACTION) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmationIntent = getConfirmationIntent(intent)
            if (confirmationIntent != null) {
                try {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                } catch (error: Exception) {
                    MainApplication.updateManager.onInstallResult(
                        PackageInstaller.STATUS_FAILURE,
                        error.message,
                    )
                    return
                }
            }
        }
        MainApplication.updateManager.onInstallResult(status, message)
    }

    @Suppress("DEPRECATION")
    private fun getConfirmationIntent(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }
}

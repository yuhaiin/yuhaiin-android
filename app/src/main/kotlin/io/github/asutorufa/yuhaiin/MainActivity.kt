package io.github.asutorufa.yuhaiin

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.compose.Main
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    var vpnBinder: IYuhaiinVpnBinder? = null
    val state = MutableStateFlow(State.DISCONNECTED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Main(this) }
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, YuhaiinVpnService::class.java), mConnection, BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            vpnBinder?.unregisterCallback(vpnCallback)
            unbindService(mConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val vpnCallback = object : IYuhaiinVpnCallback.Stub() {
        override fun onStateChanged(state: Int) {
            this@MainActivity.state.value = State.entries[state]
        }

        override fun onMsg(msg: String?) {
            Log.i("yuhaiin vpn service", "onMsg: $msg")
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            vpnBinder = IYuhaiinVpnBinder.Stub.asInterface(binder).also {
                this@MainActivity.state.value = State.entries[it.state()]
                it.registerCallback(vpnCallback)
            }
        }

        override fun onServiceDisconnected(p1: ComponentName) {
            vpnBinder?.unregisterCallback(vpnCallback)
            vpnBinder = null
        }
    }

    private val vpnPermissionDialogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startService(
                    Intent(this, YuhaiinVpnService::class.java)
                )
            }
        }

    fun startService() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0
            )
        }

        // prepare to get vpn permission
        VpnService.prepare(this)?.apply {
            vpnPermissionDialogLauncher.launch(this)
        } ?: startService(Intent(this, YuhaiinVpnService::class.java))
    }
}

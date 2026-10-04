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
import androidx.activity.enableEdgeToEdge
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
    private var serviceBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Main(this)
        }
    }

    override fun onStart() {
        super.onStart()
        bindVpnService()
    }

    private fun bindVpnService() {
        if (serviceBound) return
        serviceBound = bindService(
            Intent(this, YuhaiinVpnService::class.java),
            mConnection,
            BIND_AUTO_CREATE,
        )
    }

    override fun onStop() {
        if (serviceBound) {
            runCatching { vpnBinder?.unregisterCallback(vpnCallback) }
                .onFailure { Log.w("MainActivity", "failed to unregister VPN callback", it) }
            // Keep UpdateManager's Binder reference alive so an in-flight update can
            // finish after the Activity moves to the background.
            vpnBinder = null

            runCatching { unbindService(mConnection) }
                .onFailure { Log.w("MainActivity", "failed to unbind VPN service", it) }
            serviceBound = false
        }
        super.onStop()
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
                MainApplication.updateManager.setProxyBinder(it)
                this@MainActivity.state.value = State.entries[it.state()]
                it.registerCallback(vpnCallback)
            }
        }

        override fun onServiceDisconnected(p1: ComponentName) {
            // The remote process is already gone; do not make another Binder call here.
            vpnBinder = null
            MainApplication.updateManager.setProxyBinder(null)
            state.value = State.DISCONNECTED
        }

        override fun onBindingDied(name: ComponentName) {
            vpnBinder = null
            MainApplication.updateManager.setProxyBinder(null)
            state.value = State.DISCONNECTED

            if (serviceBound) {
                runCatching { unbindService(this) }
                    .onFailure { Log.w("MainActivity", "failed to drop dead VPN binding", it) }
                serviceBound = false
            }
            bindVpnService()
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

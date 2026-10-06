package io.github.asutorufa.yuhaiin.service

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import io.github.asutorufa.yuhaiin.IYuhaiinVpnBinder
import io.github.asutorufa.yuhaiin.IYuhaiinVpnCallback
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State

class VpnTileService : TileService() {
    private var bound = false
    private var binder: IYuhaiinVpnBinder? = null
    private var state = State.DISCONNECTED
    private val callback =
        object : IYuhaiinVpnCallback.Stub() {
            override fun onStateChanged(state: Int) {
                mainExecutorCompat { render(State.entries.getOrNull(state) ?: State.ERROR) }
            }

            override fun onMsg(msg: String?) {}
        }

    private fun mainExecutorCompat(block: () -> Unit) {
        android.os.Handler(mainLooper).post { block() }
    }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = IYuhaiinVpnBinder.Stub.asInterface(service)
                runCatching { binder?.registerCallback(callback) }.onFailure { render(State.ERROR) }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                binder = null
                render(State.DISCONNECTED)
            }

            override fun onBindingDied(name: ComponentName) {
                onStopListening()
                onStartListening()
            }
        }

    override fun onStartListening() {
        super.onStartListening()
        if (!bound)
            bound =
                bindService(
                    Intent(this, YuhaiinVpnService::class.java),
                    connection,
                    BIND_AUTO_CREATE,
                )
    }

    override fun onStopListening() {
        runCatching { binder?.unregisterCallback(callback) }
        binder = null
        if (bound) unbindService(connection)
        bound = false
        super.onStopListening()
    }

    private fun render(next: State) {
        state = next
        qsTile?.apply {
            label =
                if (Build.VERSION.SDK_INT >= 29) getString(R.string.app_name)
                else getString(next.labelResource())
            state =
                when (next) {
                    State.CONNECTED -> Tile.STATE_ACTIVE
                    State.CONNECTING,
                    State.DISCONNECTING -> Tile.STATE_UNAVAILABLE
                    else -> Tile.STATE_INACTIVE
                }
            if (Build.VERSION.SDK_INT >= 29) subtitle = getString(next.labelResource())
            contentDescription = getString(next.labelResource())
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            if (state == State.CONNECTING || state == State.DISCONNECTING) return@unlockAndRun
            if (state == State.CONNECTED) {
                runCatching { binder?.stop() }.onFailure { openConsent() }
            } else if (VpnService.prepare(this) != null) openConsent()
            else
                runCatching {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, YuhaiinVpnService::class.java).setAction(VpnActions.CONNECT),
                    )
                }
                    .onFailure { openConsent() }
        }
    }

    // The PendingIntent overload exists only on API 34+. The Intent path is used below 34.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openConsent() {
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(VpnActions.activity(this, VpnActions.CONNECT))
        else startActivityAndCollapse(VpnActions.activityIntent(this, VpnActions.CONNECT))
    }
}

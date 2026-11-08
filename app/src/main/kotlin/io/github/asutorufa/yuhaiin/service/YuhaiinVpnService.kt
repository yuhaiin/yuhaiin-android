package io.github.asutorufa.yuhaiin.service

import android.content.Intent
import android.net.Network
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Android VPN adapter. It only builds, replaces and revokes VPN interfaces; the Go core, its
 * listeners and the foreground notification belong to [YuhaiinCoreService] in the same process.
 *
 * Stopping the VPN never stops the core. [VpnCoordinator] binds this service while the core holds
 * at least one interface generation and unbinds when the last one is released.
 */
class YuhaiinVpnService : VpnService() {
    companion object {
        enum class State {
            CONNECTED,
            CONNECTING,
            DISCONNECTING,
            DISCONNECTED,
            ERROR,
        }

        /** Binding action of [VpnCoordinator]; the system binds with [SERVICE_INTERFACE]. */
        const val ACTION_PLATFORM = "io.github.asutorufa.yuhaiin.VPN_PLATFORM"
    }

    inner class LocalBinder : Binder() {
        val service: YuhaiinVpnService
            get() = this@YuhaiinVpnService
    }

    private val tag = "YuhaiinVpnService"
    private val localBinder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else localBinder

    /**
     * Started by the system for Always-on VPN (and by v1 PendingIntents). Forward the request to
     * the core service, which owns the policy, then let this service live only while bound.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action =
            when (intent?.action) {
                VpnActions.DISCONNECT -> VpnActions.DISCONNECT
                VpnActions.RECONNECT -> VpnActions.RECONNECT
                VpnActions.RESUME -> VpnActions.RESUME
                // null / SERVICE_INTERFACE: Always-on VPN or a legacy connect request.
                else -> VpnActions.CONNECT_ALWAYS_ON
            }
        Log.d(tag, "start command ${intent?.action} -> $action")
        YuhaiinCoreService.start(this, action)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    /** Builds a new interface. Android replaces the previous one of this app. */
    fun establish(config: PlatformVpnConfig): ParcelFileDescriptor? {
        val descriptor = Builder().configure(this, config).establish() ?: return null
        updateUnderlyingNetwork(VpnCoordinator.underlyingNetwork)
        return descriptor
    }

    fun updateUnderlyingNetwork(network: Network?) {
        if (!VpnCoordinator.hasInterface() && network == null) return
        if (!setUnderlyingNetworks(network?.let { arrayOf(it) }))
            Log.w(tag, "failed to update VPN underlying network")
    }

    fun isAlwaysOnCompat(): Boolean = Build.VERSION.SDK_INT >= 29 && isAlwaysOn

    fun isLockdownCompat(): Boolean = Build.VERSION.SDK_INT >= 29 && isLockdownEnabled

    override fun onRevoke() {
        Log.i(tag, "VPN permission revoked")
        VpnCoordinator.onRevoked()
        super.onRevoke()
    }
}

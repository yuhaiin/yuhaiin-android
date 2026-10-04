package io.github.asutorufa.yuhaiin.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Observe available physical networks without requestNetwork's active network retention. On older
 * devices null delegates selection to Android rather than picking an arbitrary callback.
 */
class UnderlyingNetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val changed: (Network?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var best: Network? = null
    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post {
                    if (!registered) return@post
                    best = if (Build.VERSION.SDK_INT >= 31) network else null
                    changed(best)
                }
            }

            override fun onLost(network: Network) {
                handler.post {
                    if (registered && (best == network || Build.VERSION.SDK_INT < 31)) {
                        best = null
                        changed(null)
                    }
                }
            }
        }

    fun start() {
        if (registered) return
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
        registered = true
        try {
            if (Build.VERSION.SDK_INT >= 31)
                connectivity.registerBestMatchingNetworkCallback(request, callback, handler)
            else connectivity.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            registered = false
            throw e
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        connectivity.unregisterNetworkCallback(callback)
        best = null
    }
}

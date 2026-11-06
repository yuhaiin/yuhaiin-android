package io.github.asutorufa.yuhaiin.service

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.NetworkInterface
import yuhaiin.UnderlyingNetwork
import yuhaiin.UnderlyingNetworkIter
import yuhaiin.Yuhaiin

/** Observe actual non-VPN Network identities without retaining networks with requestNetwork. */
class UnderlyingNetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val changed: (Network?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var epoch = 0L
    private var best: Network? = null
    private var availableCallback: ConnectivityManager.NetworkCallback? = null
    private var bestCallback: ConnectivityManager.NetworkCallback? = null
    private var networks = linkedMapOf<Network, UnderlyingNetwork>()
    private var haveSnapshot = false

    private fun dispatch(session: Long, action: () -> Unit) {
        handler.post { if (registered && epoch == session) action() }
    }

    private fun publish() {
        if (!haveSnapshot) return
        val items = networks.values.iterator()
        val snapshot =
            object : UnderlyingNetworkIter {
                override fun hasNext() = items.hasNext()
                override fun next() = items.next()
            }
        // Before API 31, leave selection with Android when its default is our
        // own VPN. Source/interface affinity still distinguishes physical paths.
        val selected = best ?: connectivity.activeNetwork?.takeIf { networks.containsKey(it) }
        runCatching { Yuhaiin.reportUnderlyingNetworks(snapshot, selected?.networkHandle ?: 0L) }
            .onFailure { Log.w("UnderlyingNetworkMonitor", "Cannot publish underlying networks", it) }
    }

    private fun refresh(
        updated: Network? = null,
        properties: LinkProperties? = null,
        capabilities: NetworkCapabilities? = null,
    ) {
        // Failed/partial reads are unknown state. Retain the last complete view.
        val next = linkedMapOf<Network, UnderlyingNetwork>()
        runCatching {
                for (network in connectivity.allNetworks) {
                    val caps =
                        capabilities.takeIf { network == updated }
                            ?: connectivity.getNetworkCapabilities(network)
                            ?: return
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    ) continue
                    val link =
                        properties.takeIf { network == updated }
                            ?: connectivity.getLinkProperties(network)
                            ?: return
                    val name = link.interfaceName ?: return
                    next[network] =
                        UnderlyingNetwork().apply {
                            handle = network.networkHandle
                            interfaceName = name
                            interfaceIndex = NetworkInterface.getByName(name)?.index ?: 0
                            addresses = link.linkAddresses.joinToString(" ") { it.address.hostAddress ?: "" }
                            available = true
                            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            vpn = false
                        }
                }
                networks = next
                haveSnapshot = true
                publish()
            }
            .onFailure { Log.w("UnderlyingNetworkMonitor", "Cannot read underlying networks", it) }
    }

    fun start() {
        if (registered) return
        val session = ++epoch
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
        val available =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = dispatch(session) {
                    refresh()
                    if (Build.VERSION.SDK_INT < 31) changed(null)
                }

                override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) =
                    dispatch(session) { refresh(network, properties = link) }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    dispatch(session) { refresh(network, capabilities = caps) }

                override fun onLost(network: Network) = dispatch(session) {
                    // A best-matching callback losing a match does not prove
                    // network loss. Only the all-network callback retires it.
                    networks.remove(network)
                    publish()
                    if (Build.VERSION.SDK_INT < 31) changed(null)
                }
            }
        registered = true
        try {
            if (Build.VERSION.SDK_INT >= 26)
                connectivity.registerNetworkCallback(request, available, handler)
            else connectivity.registerNetworkCallback(request, available)
            availableCallback = available
            if (Build.VERSION.SDK_INT >= 31) {
                val selected =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) = dispatch(session) {
                            best = network
                            changed(network)
                            refresh()
                        }

                        override fun onLost(network: Network) = dispatch(session) {
                            if (best == network) {
                                best = null
                                changed(null)
                                publish()
                            }
                        }
                    }
                connectivity.registerBestMatchingNetworkCallback(request, selected, handler)
                bestCallback = selected
            }
            refresh()
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        ++epoch
        availableCallback?.let(connectivity::unregisterNetworkCallback)
        bestCallback?.let(connectivity::unregisterNetworkCallback)
        availableCallback = null
        bestCallback = null
        best = null
        // Stopping observation is unknown state, not an offline transition.
    }
}

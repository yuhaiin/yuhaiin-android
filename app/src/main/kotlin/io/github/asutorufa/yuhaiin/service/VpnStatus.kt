package io.github.asutorufa.yuhaiin.service

import android.os.Bundle
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State

/** Immutable service-owned snapshot transported across the :bg Binder boundary. */
data class VpnStatus(
    val state: State = State.DISCONNECTED,
    val connectedAt: Long = 0,
    val resumeAt: Long = 0,
    val speed: String = "",
    val network: String = "",
    val mtu: Int = 0,
    val ipv4: String = "",
    val ipv6: String = "",
    val route: String = "",
) {
    fun toBundle() =
        Bundle().apply {
            putInt("state", state.ordinal)
            putLong("connectedAt", connectedAt)
            putLong("resumeAt", resumeAt)
            putString("speed", speed)
            putString("network", network)
            putInt("mtu", mtu)
            putString("ipv4", ipv4)
            putString("ipv6", ipv6)
            putString("route", route)
        }

    companion object {
        fun fromBundle(bundle: Bundle) =
            VpnStatus(
                State.entries.getOrNull(bundle.getInt("state", State.DISCONNECTED.ordinal))
                    ?: State.ERROR,
                bundle.getLong("connectedAt"),
                bundle.getLong("resumeAt"),
                bundle.getString("speed").orEmpty(),
                bundle.getString("network").orEmpty(),
                bundle.getInt("mtu"),
                bundle.getString("ipv4").orEmpty(),
                bundle.getString("ipv6").orEmpty(),
                bundle.getString("route").orEmpty(),
            )
    }
}

fun State.labelResource(): Int =
    when (this) {
        State.CONNECTED -> R.string.status_connected
        State.CONNECTING -> R.string.status_connecting
        State.DISCONNECTING -> R.string.status_disconnecting
        State.DISCONNECTED -> R.string.status_disconnected
        State.ERROR -> R.string.status_error
    }

package io.github.asutorufa.yuhaiin.service

import android.content.Context
import android.os.Bundle
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import java.text.DateFormat
import java.util.Date

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
    val nativeStatus: String = "",
    val nativeHealth: String = "",
    val healthChecking: Boolean = false,
    val healthError: String = "",
    val coreState: CoreState = CoreState.STOPPED,
) {
    val coreRunning: Boolean
        get() = coreState == CoreState.RUNNING

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
            putString("nativeStatus", nativeStatus)
            putString("nativeHealth", nativeHealth)
            putBoolean("healthChecking", healthChecking)
            putString("healthError", healthError)
            putInt("coreState", coreState.ordinal)
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
                bundle.getString("nativeStatus").orEmpty(),
                bundle.getString("nativeHealth").orEmpty(),
                bundle.getBoolean("healthChecking"),
                bundle.getString("healthError").orEmpty(),
                CoreState.entries.getOrNull(bundle.getInt("coreState")) ?: CoreState.STOPPED,
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

fun routeLabelResource(route: String): Int? =
    when (route) {
        Constants.ALL_ROUTE -> R.string.route_all_label
        Constants.NON_LOCAL_ROUTE -> R.string.route_non_local_label
        Constants.NON_CHINESE_ROUTE -> R.string.route_non_chinese_label
        else -> null
    }

/** The primary control a status offers on a surface that has room for only one. */
enum class VpnToggle {
    CONNECT,
    STOP,
    RESUME,
    STOPPING,
}

fun VpnStatus.toggle(): VpnToggle =
    when {
        resumeAt > 0 -> VpnToggle.RESUME
        state == State.CONNECTED || state == State.CONNECTING -> VpnToggle.STOP
        state == State.DISCONNECTING -> VpnToggle.STOPPING
        else -> VpnToggle.CONNECT
    }

fun VpnStatus.titleResource(): Int =
    when {
        resumeAt > 0 -> R.string.status_paused
        state == State.DISCONNECTED && coreRunning -> R.string.status_core_only
        state == State.DISCONNECTED && coreState == CoreState.FAILED -> R.string.core_start_failed
        else -> state.labelResource()
    }

fun VpnStatus.connectedSummary(routeLabel: String): String =
    listOf(network, routeLabel).filter { it.isNotBlank() }.joinToString(" · ")

/** Second line under the title; empty when the title already says everything. */
fun VpnStatus.detail(context: Context, showSpeed: Boolean): String =
    when {
        resumeAt > 0 ->
            context.getString(
                R.string.resume_time,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(resumeAt)),
            )
        state != State.CONNECTED -> ""
        showSpeed && speed.isNotBlank() -> speed
        else -> connectedSummary(routeLabelResource(route)?.let(context::getString) ?: route)
    }

/** Lifecycle of the Go core, independent of the VPN. */
enum class CoreState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

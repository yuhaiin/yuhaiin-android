package io.github.asutorufa.yuhaiin.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) Local Network Protections.
 *
 * Apps targeting API 37 need the runtime permission [PERMISSION] to open TCP connections to, and
 * to accept TCP connections from, the local network (and to use LAN UDP/multicast). Without it the
 * OS drops that traffic: a remote client completes the TCP handshake with nothing answering or
 * acknowledging its data, which looks like a timeout. Loopback and Internet traffic are
 * unaffected, so the in-app WebView keeps working while a LAN client cannot reach the Allow LAN
 * management server.
 *
 * This is independent of the core's `allow_lan` setting, which only chooses the bind address.
 * Only an Activity may ask the user; background components must merely report the state.
 */
object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    const val MIN_SDK = 37

    enum class State {
        /** Android 16 and older: the INTERNET permission implies local network access. */
        NOT_REQUIRED,
        GRANTED,
        DENIED,
    }

    /** What the foreground UI should do about a missing permission. */
    enum class Prompt {
        NONE,
        /** Show the standard system dialog now (first need only). */
        REQUEST,
        /** Show a non-modal notice whose button asks for the permission again. */
        NOTICE_REQUEST,
        /** The system will not ask again: the notice leads to the app's system settings. */
        NOTICE_SETTINGS,
    }

    fun isEnforced(sdkInt: Int = Build.VERSION.SDK_INT) = sdkInt >= MIN_SDK

    fun state(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): State =
        when {
            !isEnforced(sdkInt) -> State.NOT_REQUIRED
            ContextCompat.checkSelfPermission(context, PERMISSION) ==
                PackageManager.PERMISSION_GRANTED -> State.GRANTED
            else -> State.DENIED
        }

    /**
     * Decides what to show. The system dialog is requested automatically at most once
     * ([askedBefore]); afterwards the user is only shown a dismissible notice, so a refusal is
     * never answered by another dialog. [canAskAgain] is `shouldShowRequestPermissionRationale`:
     * false after a request means the system will not show the dialog again.
     *
     * @param needed a feature that depends on LAN traffic is in use (Allow LAN, or the VPN).
     */
    fun prompt(
        state: State,
        needed: Boolean,
        askedBefore: Boolean,
        canAskAgain: Boolean,
    ): Prompt =
        when {
            state != State.DENIED || !needed -> Prompt.NONE
            !askedBefore -> Prompt.REQUEST
            canAskAgain -> Prompt.NOTICE_REQUEST
            else -> Prompt.NOTICE_SETTINGS
        }

    /** True when LAN features work, or the OS does not restrict them. */
    fun isUsable(state: State) = state != State.DENIED
}

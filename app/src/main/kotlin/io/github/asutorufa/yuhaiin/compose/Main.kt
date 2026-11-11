package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.network.LocalNetworkAccess
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose

/**
 * v2: the yuhaiin Web UI is the only screen. Native UI is limited to what Android requires the
 * app itself to show: the data disclosures before consent, the OS VPN dialog (launched by the
 * Activity) and the route chooser used by the widget and launcher shortcut.
 */
@Composable
fun Main(activity: MainActivity) {
    val error by activity.error.collectAsStateWithLifecycle()
    val navigationAction by activity.navigationAction.collectAsStateWithLifecycle()
    val vpnDisclosure by activity.vpnDisclosure.collectAsStateWithLifecycle()
    val appsDisclosure by activity.appsDisclosure.collectAsStateWithLifecycle()
    val session by activity.session.collectAsStateWithLifecycle()
    val localNetwork by activity.localNetwork.collectAsStateWithLifecycle()

    androidx.activity.compose.ReportDrawnAfter {
        runCatching { MainApplication.settings.ready.await() }
    }
    AppTheme {
        if (vpnDisclosure)
            DataDisclosureDialog(
                purpose = Purpose.VPN,
                error = error,
                onAccept = activity::acceptVpnDisclosure,
                onDismiss = activity::dismissVpnDisclosure,
            )
        if (appsDisclosure)
            DataDisclosureDialog(
                purpose = Purpose.INSTALLED_APPS,
                error = error,
                onAccept = activity::acceptAppsDisclosure,
                onDismiss = activity::dismissAppsDisclosure,
            )
        RouteShortcutPicker(activity, navigationAction)
        Box {
            WebViewComponent(
                session = session,
                onRetry = activity::startCore,
                onNativeAction = { action ->
                    when (action) {
                        NativeAction.VPN_AUTHORIZE -> activity.startService()
                        NativeAction.APPS_CONSENT -> activity.requestAppsConsent()
                        NativeAction.LOCAL_NETWORK -> activity.requestLocalNetworkAccess()
                    }
                },
            )
            if (localNetwork != LocalNetworkAccess.Prompt.NONE)
                LocalNetworkNotice(
                    openSettings = localNetwork == LocalNetworkAccess.Prompt.NOTICE_SETTINGS,
                    onAction = {
                        if (localNetwork == LocalNetworkAccess.Prompt.NOTICE_SETTINGS)
                            activity.openLocalNetworkSettings()
                        else activity.requestLocalNetworkAccess()
                    },
                    onDismiss = activity::dismissLocalNetworkNotice,
                )
        }
    }
}

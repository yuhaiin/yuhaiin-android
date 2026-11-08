package io.github.asutorufa.yuhaiin.compose

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.compose.AppMotion.navigateBack
import io.github.asutorufa.yuhaiin.compose.AppMotion.navigateForward
import io.github.asutorufa.yuhaiin.compose.route.RouteConfigScreen
import io.github.asutorufa.yuhaiin.compose.route.RouteEditScreen
import io.github.asutorufa.yuhaiin.privacy.PrivacyConsent.Purpose
import io.github.asutorufa.yuhaiin.service.VpnActions
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Main(activity: MainActivity) {
    val vpnState by activity.state.collectAsStateWithLifecycle()
    val error by activity.error.collectAsStateWithLifecycle()
    val status by activity.status.collectAsStateWithLifecycle()
    val navigationAction by activity.navigationAction.collectAsStateWithLifecycle()
    val vpnDisclosure by activity.vpnDisclosure.collectAsStateWithLifecycle()

    androidx.activity.compose.ReportDrawnAfter {
        runCatching { MainApplication.settings.ready.await() }
    }
    AppTheme {
        if (vpnDisclosure) {
            DataDisclosureDialog(
                purpose = Purpose.VPN,
                error = error,
                onAccept = activity::acceptVpnDisclosure,
                onDismiss = activity::dismissVpnDisclosure,
            )
        }
        SharedTransitionLayout(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            val backStack = rememberNavBackStack(HomeRoute)
            val navigator =
                remember(backStack) {
                    AppNavigator(backStack, activity::finish)
                }

            LaunchedEffect(navigationAction) {
                if (navigationAction == io.github.asutorufa.yuhaiin.service.VpnActions.DASHBOARD) {
                    // The Web UI is the home screen in v2.
                    navigator.popToRoot()
                    activity.navigationAction.value = null
                }
                if (navigationAction == io.github.asutorufa.yuhaiin.service.VpnActions.SETTINGS) {
                    navigator.push(NativeSettingsRoute)
                    activity.navigationAction.value = null
                }
                if (navigationAction == io.github.asutorufa.yuhaiin.service.VpnActions.HOME) {
                    navigator.popToRoot()
                    activity.navigationAction.value = null
                }
            }
            RouteShortcutPicker(activity, navigationAction)

            NavDisplay(
                backStack = backStack,
                onBack = navigator::pop,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                transitionSpec = { navigateForward() },
                popTransitionSpec = { navigateBack() },
                predictivePopTransitionSpec = { swipeEdge -> AppMotion.predictiveBack(swipeEdge) },
                entryProvider =
                    entryProvider<NavKey> {
                        entry<HomeRoute> {
                            val session by activity.session.collectAsStateWithLifecycle()
                            WebViewComponent(
                                session = session,
                                onRetry = activity::startCore,
                                onOpenLogs = { navigator.push(LogcatRoute) },
                                onNativeAction = { action ->
                                    when (action) {
                                        NativeAction.VPN_AUTHORIZE -> activity.startService()
                                        NativeAction.NATIVE_SETTINGS ->
                                            navigator.push(NativeSettingsRoute)
                                        NativeAction.APPS -> navigator.push(AppListRoute)
                                        NativeAction.ROUTES -> navigator.push(RouteConfigRoute)
                                        NativeAction.LOGS -> navigator.push(LogcatRoute)
                                        NativeAction.BACKUP -> navigator.push(BackupRoute)
                                        NativeAction.UPDATE -> navigator.push(AboutRoute)
                                        NativeAction.PRIVACY -> navigator.push(PrivacyRoute)
                                    }
                                },
                            )
                        }

                        entry<NativeSettingsRoute> {
                            val context = LocalContext.current
                            val addresses by
                                produceState<List<String>>(
                                    initialValue = emptyList(),
                                    key1 = vpnState,
                                    key2 = context,
                                ) {
                                    val connectivity =
                                        context.getSystemService(ConnectivityManager::class.java)
                                    val refreshRequests = Channel<Unit>(Channel.CONFLATED)
                                    val refreshJob = launch {
                                        for (ignored in refreshRequests) {
                                            value =
                                                withContext(Dispatchers.IO) {
                                                    MainApplication.getAddresses()
                                                }
                                        }
                                    }
                                    val networkCallback =
                                        object : ConnectivityManager.NetworkCallback() {
                                            override fun onAvailable(network: Network) {
                                                refreshRequests.trySend(Unit)
                                            }

                                            override fun onLost(network: Network) {
                                                refreshRequests.trySend(Unit)
                                            }

                                            override fun onLinkPropertiesChanged(
                                                network: Network,
                                                linkProperties: LinkProperties,
                                            ) {
                                                refreshRequests.trySend(Unit)
                                            }
                                        }

                                    refreshRequests.trySend(Unit)
                                    connectivity.registerDefaultNetworkCallback(networkCallback)

                                    try {
                                        awaitCancellation()
                                    } finally {
                                        runCatching {
                                            connectivity.unregisterNetworkCallback(networkCallback)
                                        }
                                        refreshRequests.close()
                                        refreshJob.cancel()
                                    }
                                }
                            with(this@SharedTransitionLayout) {
                                SettingCompose(
                                    vpnState = vpnState,
                                    status = status,
                                    snooze = { activity.vpnBinder?.snooze(it) },
                                    error = error,
                                    stopService = { activity.vpnBinder?.stop() },
                                    startService = { activity.startService() },
                                    addresses = addresses,
                                    onOpenAbout = { navigator.push(AboutRoute) },
                                    onOpenAppList = { navigator.push(AppListRoute) },
                                    onOpenRouteConfig = { navigator.push(RouteConfigRoute) },
                                    onOpenWebView = { navigator.popToRoot() },
                                    onOpenLogcat = { navigator.push(LogcatRoute) },
                                    onOpenBackup = { navigator.push(BackupRoute) },
                                    checkHealth = { activity.vpnBinder?.checkHealth() },
                                    onBack = { navigator.popFrom(NativeSettingsRoute) },
                                )
                            }
                        }

                        entry<AboutRoute> {
                            with(this@SharedTransitionLayout) {
                                AboutScreen(
                                    onBack = { navigator.popFrom(AboutRoute) },
                                    updateManager = MainApplication.updateManager,
                                    proxyReady = status.coreRunning,
                                    startProxy = { activity.startCore() },
                                    onOpenPrivacy = { navigator.push(PrivacyRoute) },
                                )
                            }
                        }

                        entry<PrivacyRoute> {
                            PrivacyScreen(
                                onBack = { navigator.popFrom(PrivacyRoute) },
                                disconnect = {
                                    activity.startService(
                                        io.github.asutorufa.yuhaiin.service.YuhaiinCoreService
                                            .intent(activity, VpnActions.DISCONNECT)
                                    )
                                },
                            )
                        }

                        entry<AppListRoute> {
                            with(this@SharedTransitionLayout) {
                                AppListComponent(
                                    connected = vpnState == State.CONNECTED,
                                    onBack = { navigator.popFrom(AppListRoute) },
                                )
                            }
                        }

                        entry<RouteConfigRoute> {
                            val animatedContentScope = LocalNavAnimatedContentScope.current
                            with(this@SharedTransitionLayout) {
                                RouteConfigScreen(
                                    animatedContentScope = animatedContentScope,
                                    onBack = { navigator.popFrom(RouteConfigRoute) },
                                    onOpenRouteEdit = { routeName ->
                                        navigator.push(RouteEditRoute(routeName))
                                    },
                                )
                            }
                        }

                        entry<RouteEditRoute> { route ->
                            val animatedContentScope = LocalNavAnimatedContentScope.current
                            with(this@SharedTransitionLayout) {
                                RouteEditScreen(
                                    animatedContentScope = animatedContentScope,
                                    onBack = { navigator.popFrom(route) },
                                    routeName = route.routeName,
                                )
                            }
                        }

                        entry<WebViewRoute> {
                            // Legacy back stacks restored from v1 state: the Web UI is home.
                            LaunchedEffect(Unit) { navigator.popToRoot() }
                        }

                        entry<BackupRoute> {
                            BackupScreen(
                                onBack = { navigator.popFrom(BackupRoute) },
                                // The core owns the configuration lock whether or not the VPN
                                // runs, so a restore stops the core and starts it afterwards.
                                disconnect = { activity.stopCoreAndWait() },
                                resume = { activity.startCore() },
                            )
                        }

                        entry<LogcatRoute> {
                            val logcatExcludeRules =
                                arrayListOf(
                                    "]: processMotionEvent MotionEvent { action=ACTION_",
                                    "]: dispatchPointerEvent handled=true, event=MotionEvent { action=ACTION_",
                                    "Davey! duration=",
                                    // android popup window select text debug log
                                    "Attempted to finish an input event but the input event receiver has already been disposed",
                                    "endAllActiveAnimators on ",
                                    "Initializing SystemTextClassifier,",
                                    "TextClassifier called on main thread",
                                    "android added item ",
                                    "No package ID ",
                                    "eglMakeCurrent:",
                                    "NotificationManager: io.github.asutorufa.yuhaiin: notify",
                                    "InputEventReceiver_DOT: IER.scheduleInputVsync",
                                    "ViewRootImpl@",
                                    "androidx.compose",
                                    "ViewPostIme",
                                )

                            with(this@SharedTransitionLayout) {
                                LogcatCompose(
                                    excludeList = logcatExcludeRules,
                                    onBack = { navigator.popFrom(LogcatRoute) },
                                )
                            }
                        }
                    },
            )
        }
    }
}

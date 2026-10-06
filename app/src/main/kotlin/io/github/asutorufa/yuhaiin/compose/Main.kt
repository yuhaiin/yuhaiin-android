package io.github.asutorufa.yuhaiin.compose

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import io.github.asutorufa.yuhaiin.compose.route.RouteConfigScreen
import io.github.asutorufa.yuhaiin.compose.route.RouteEditScreen
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun ChangeSystemBarsTheme(activity: MainActivity, lightTheme: Boolean) {
    val barColor = MaterialTheme.colorScheme.background.toArgb()
    LaunchedEffect(lightTheme) {
        if (lightTheme) {
            activity.enableEdgeToEdge(
                statusBarStyle =
                    SystemBarStyle.light(
                        barColor,
                        barColor,
                    ),
                navigationBarStyle =
                    SystemBarStyle.light(
                        barColor,
                        barColor,
                    ),
            )
        } else {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(barColor),
                navigationBarStyle = SystemBarStyle.dark(barColor),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Main(activity: MainActivity) {
    val vpnState by activity.state.collectAsStateWithLifecycle()
    val error by activity.error.collectAsStateWithLifecycle()
    val status by activity.status.collectAsStateWithLifecycle()
    val navigationAction by activity.navigationAction.collectAsStateWithLifecycle()
    val colorScheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            }

            isSystemInDarkTheme() -> darkColorScheme()
            else -> lightColorScheme()
        }

    androidx.activity.compose.ReportDrawnAfter {
        runCatching { MainApplication.settings.ready.await() }
    }
    MaterialTheme(colorScheme = colorScheme) {
        ChangeSystemBarsTheme(activity, !isSystemInDarkTheme())
        SharedTransitionLayout(modifier = Modifier.background(colorScheme.surface)) {
            val backStack = rememberNavBackStack(HomeRoute)
            val navigator =
                remember(backStack) {
                    AppNavigator(backStack, activity::finish)
                }

            LaunchedEffect(navigationAction) {
                if (navigationAction == io.github.asutorufa.yuhaiin.service.VpnActions.DASHBOARD) {
                    navigator.push(WebViewRoute)
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
                transitionSpec = {
                    fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 24 } togetherWith
                        fadeOut(tween(120))
                },
                popTransitionSpec = {
                    fadeIn(tween(180)) togetherWith
                        fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 24 }
                },
                predictivePopTransitionSpec = { _ ->
                    fadeIn(tween(180)) togetherWith
                        fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 24 }
                },
                entryProvider =
                    entryProvider<NavKey> {
                        entry<HomeRoute> {
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
                                    onOpenWebView = { navigator.push(WebViewRoute) },
                                    onOpenLogcat = { navigator.push(LogcatRoute) },
                                    onOpenBackup = { navigator.push(BackupRoute) },
                                    checkHealth = { activity.vpnBinder?.checkHealth() },
                                )
                            }
                        }

                        entry<AboutRoute> {
                            with(this@SharedTransitionLayout) {
                                AboutScreen(
                                    onBack = { navigator.popFrom(AboutRoute) },
                                    updateManager = MainApplication.updateManager,
                                    proxyReady = vpnState == State.CONNECTED,
                                    startProxy = { activity.startService() },
                                )
                            }
                        }

                        entry<AppListRoute> {
                            with(this@SharedTransitionLayout) {
                                AppListComponent(onBack = { navigator.popFrom(AppListRoute) })
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
                            with(this@SharedTransitionLayout) {
                                WebViewComponent(onBack = { navigator.popFrom(WebViewRoute) }) {
                                    MainApplication.store.getInt(
                                        io.github.asutorufa.yuhaiin.Constants.WEB_PORT_KEY
                                    )
                                }
                            }
                        }

                        entry<BackupRoute> {
                            BackupScreen(
                                onBack = { navigator.popFrom(BackupRoute) },
                                disconnect = {
                                    activity.vpnBinder?.stop()
                                    kotlinx.coroutines.withTimeout(15_000) {
                                        activity.state.first {
                                            it == State.DISCONNECTED || it == State.ERROR
                                        }
                                    }
                                },
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

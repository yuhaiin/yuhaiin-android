package io.github.asutorufa.yuhaiin.compose

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.compose.route.RouteConfigScreen
import io.github.asutorufa.yuhaiin.compose.route.RouteEditScreen
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State


@Composable
private fun ChangeSystemBarsTheme(activity: MainActivity, lightTheme: Boolean) {
    val barColor = MaterialTheme.colorScheme.background.toArgb()
    LaunchedEffect(lightTheme) {
        if (lightTheme) {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(
                    barColor, barColor,
                ),
                navigationBarStyle = SystemBarStyle.light(
                    barColor, barColor,
                ),
            )
        } else {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(
                    barColor,
                ),
                navigationBarStyle = SystemBarStyle.dark(
                    barColor,
                ),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Main(activity: MainActivity) {
    val vpnState by activity.state.collectAsState()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        isSystemInDarkTheme() -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        SharedTransitionLayout {
            val backStack = rememberNavBackStack(HomeRoute)
            val navigator = remember(backStack) {
                AppNavigator(backStack, activity::finish)
            }

            NavDisplay(
                backStack = backStack,
                onBack = navigator::pop,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = entryProvider<NavKey> {
                    entry<HomeRoute> {
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        with(this@SharedTransitionLayout) {
                            SettingCompose(
                                vpnState = vpnState,
                                stopService = { activity.vpnBinder?.stop() },
                                startService = { activity.startService() },
                                animatedContentScope = animatedContentScope,
                                store = MainApplication.store,
                                addresses = MainApplication.getAddresses(),
                                onOpenAbout = { navigator.push(AboutRoute) },
                                onOpenAppList = { navigator.push(AppListRoute) },
                                onOpenRouteConfig = { navigator.push(RouteConfigRoute) },
                                onOpenWebView = { navigator.push(WebViewRoute) },
                                onOpenLogcat = { navigator.push(LogcatRoute) },
                            )
                        }
                    }

                    entry<AboutRoute> {
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        with(this@SharedTransitionLayout) {
                            AboutScreen(
                                onBack = navigator::pop,
                                updateManager = MainApplication.updateManager,
                                proxyReady = vpnState == State.CONNECTED,
                                startProxy = { activity.startService() },
                                animatedContentScope = animatedContentScope,
                            )
                        }
                    }

                    entry<AppListRoute> {
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        with(this@SharedTransitionLayout) {
                            AppListComponent(
                                onBack = navigator::pop,
                                packageManager = activity.applicationContext.packageManager,
                                animatedVisibilityScope = animatedContentScope,
                            )
                        }
                    }

                    entry<RouteConfigRoute> {
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        with(this@SharedTransitionLayout) {
                            RouteConfigScreen(
                                onBack = navigator::pop,
                                onOpenRouteEdit = { routeName ->
                                    navigator.push(RouteEditRoute(routeName))
                                },
                                animatedContentScope = animatedContentScope,
                            )
                        }
                    }

                    entry<RouteEditRoute> { route ->
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        with(this@SharedTransitionLayout) {
                            RouteEditScreen(
                                onBack = navigator::pop,
                                routeName = route.routeName,
                                animatedContentScope = animatedContentScope,
                            )
                        }
                    }

                    entry<WebViewRoute> {
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        with(this@SharedTransitionLayout) {
                            WebViewComponent(
                                animatedContentScope = animatedContentScope,
                                onBack = navigator::pop,
                            ) {
                                MainApplication.store.getInt("yuhaiin_port")
                            }
                        }
                    }

                    entry<LogcatRoute>(
                        metadata = metadata {
                            put(NavDisplay.TransitionKey) {
                                slideInVertically { it } + fadeIn() togetherWith
                                    ExitTransition.KeepUntilTransitionsFinished
                            }
                            put(NavDisplay.PopTransitionKey) {
                                EnterTransition.None togetherWith
                                    slideOutVertically { it } + fadeOut()
                            }
                            put(NavDisplay.PredictivePopTransitionKey) { _: Int ->
                                EnterTransition.None togetherWith
                                    slideOutVertically { it } + fadeOut()
                            }
                        }
                    ) {
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        val logcatExcludeRules = arrayListOf(
                            "]: processMotionEvent MotionEvent \\{ action=ACTION_",
                            "]: dispatchPointerEvent handled=true, event=MotionEvent \\{ action=ACTION_",
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
                            "ViewPostIme"
                        )

                        with(this@SharedTransitionLayout) {
                            LogcatCompose(
                                excludeList = logcatExcludeRules,
                                onBack = navigator::pop,
                                animatedVisibilityScope = animatedContentScope,
                            )
                        }
                    }
                },
            )
        }
    }
}

inline fun <T> Modifier.thenIfNotNull(
    value: T?,
    block: Modifier.(T) -> Modifier
): Modifier = if (value != null) block(value) else this

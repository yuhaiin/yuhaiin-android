package io.github.asutorufa.yuhaiin.compose

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.asutorufa.yuhaiin.MainActivity
import io.github.asutorufa.yuhaiin.MainApplication


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
            val navController = rememberNavController()

            NavHost(navController, "Home") {
                composable("Home") {
                    SettingCompose(
                        navController = navController,
                        vpnState = vpnState,
                        stopService = { activity.vpnBinder?.stop() },
                        startService = { activity.startService() },
                        animatedContentScope = this@composable,
                        store = MainApplication.store,
                    )
                }

                composable("APPLIST") {
                    AppListComponent(
                        navController = navController,
                        packageManager = activity.applicationContext.packageManager,
                        animatedVisibilityScope = this@composable,
                    )
                }

                composable("WebView") {
                    WebViewComponent(this@composable, navController) {
                        MainApplication.store.getInt("yuhaiin_port")
                    }
                }

                composable(
                    "LOGCAT",
                    enterTransition = { slideInVertically { it } + fadeIn() },
                    exitTransition = { slideOutVertically { it } + fadeOut() },
                ) {
                    val logcatExcludeRules = arrayListOf(
                        ".*]: processMotionEvent MotionEvent \\{ action=ACTION_.*",
                        ".*]: dispatchPointerEvent handled=true, event=MotionEvent \\{ action=ACTION_.*",
                        ".*Davey! duration=.*",
                        // android popup window select text debug log
                        ".*Attempted to finish an input event but the input event receiver has already been disposed.*",
                        ".*endAllActiveAnimators on .* with handle.*",
                        ".*Initializing SystemTextClassifier,.*",
                        ".*TextClassifier called on main thread.*",
                        ".*android added item .*",
                        ".*No package ID .* found for ID.*",
                        ".*eglMakeCurrent:.*",
                        ".*NotificationManager: io.github.asutorufa.yuhaiin: notify.*",
                        ".*InputEventReceiver_DOT: IER.scheduleInputVsync.*",
                        ".*ViewRootImpl@.*[.*]: .*",
                        ".*androidx.compose.*",
                        ".*ViewPostIme.*"
                    )

                    LogcatCompose(
                        excludeList = logcatExcludeRules,
                        navController = navController,
                        animatedVisibilityScope = this@composable,
                    )
                }
            }
        }
    }
}

inline fun <T> Modifier.thenIfNotNull(
    value: T?,
    block: Modifier.(T) -> Modifier
): Modifier = if (value != null) block(value) else this


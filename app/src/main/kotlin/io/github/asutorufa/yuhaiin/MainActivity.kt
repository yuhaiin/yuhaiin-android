package io.github.asutorufa.yuhaiin

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.asutorufa.logviewer.LogcatCompose
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : AppCompatActivity() {
    private var vpnBinder: IYuhaiinVpnBinder? = null

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state
    private var currentState: State
        get() = _state.value
        set(value) {
            _state.value = value
            Log.d("VpnService", "state changed $value")
        }


    @Composable
    private fun ChangeSystemBarsTheme(lightTheme: Boolean) {
        val barColor = MaterialTheme.colorScheme.background.toArgb()
        LaunchedEffect(lightTheme) {
            if (lightTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(
                        barColor, barColor,
                    ),
                    navigationBarStyle = SystemBarStyle.light(
                        barColor, barColor,
                    ),
                )
            } else {
                enableEdgeToEdge(
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

    //                CustomTabsIntent.Builder().apply {
//                    setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
//                }.build().apply {
//                    intent.data =
//                        "http://127.0.0.1:${MainApplication.store.getInt("yuhaiin_port")}".toUri()
//                    this@MainActivity.startActivity(intent)
//                }
//            }

    @OptIn(
        ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class,
        ExperimentalSharedTransitionApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vpnState by state.collectAsState()

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
                ChangeSystemBarsTheme(!isSystemInDarkTheme())

                SharedTransitionLayout {
                    val navController = rememberNavController()

                    NavHost(navController, "Home") {
                        composable("Home") {
                            Home(
                                modifier = Modifier
                                    .fillMaxSize(),
                                navController, vpnState,
                                { vpnBinder?.stop() },
                                { startService() },
                                animatedContentScope = this@composable,
                            )
                        }

                        composable("APPLIST") {
                            AppListComponent(
                                navController,
                                applicationContext.packageManager,
                                animatedVisibilityScope = this@composable,
                            )
                        }

                        composable("WebView") {
                            WebViewComponent(this@composable, navController) {
                                MainApplication.store.getInt("yuhaiin_port")
                            }
                        }

                        composable("LOGCAT") {
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
                                ".*ViewRootImpl@.*[.*]: .*"
                            )

                            LogcatCompose(
                                topBarModifier = Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState("OPEN_LOGCAT"),
                                    animatedVisibilityScope = this@composable,
                                ),
                                excludeList = logcatExcludeRules,
                                navController = navController,
                            )
                        }
                    }
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, YuhaiinVpnService::class.java), mConnection, BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            vpnBinder?.unregisterCallback(vpnCallback)
            unbindService(mConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val vpnCallback = object : IYuhaiinVpnCallback.Stub() {
        override fun onStateChanged(state: Int) {
            currentState = State.entries[state]
        }

        override fun onMsg(msg: String?) {
            Log.i("yuhaiin vpn service", "onMsg: $msg")
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            vpnBinder = IYuhaiinVpnBinder.Stub.asInterface(binder).also {
                currentState = State.entries[it.state()]
                it.registerCallback(vpnCallback)
            }
        }

        override fun onServiceDisconnected(p1: ComponentName) {
            vpnBinder?.unregisterCallback(vpnCallback)
            vpnBinder = null
        }
    }

    private val vpnPermissionDialogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startService(
                    Intent(this, YuhaiinVpnService::class.java)
                )
            }
        }

    private fun startService() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0
            )
        }

        // prepare to get vpn permission
        VpnService.prepare(this)?.apply {
            vpnPermissionDialogLauncher.launch(this)
        } ?: startService(Intent(this, YuhaiinVpnService::class.java))
    }
}


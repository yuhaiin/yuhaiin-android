package io.github.asutorufa.yuhaiin

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.Preference
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


@SuppressLint("RememberInComposition", "RestrictedApi")
class MainActivity : AppCompatActivity() {
    var mainApplication: MainApplication? = null

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


    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return super.onCreateView(name, context, attrs)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainApplication = applicationContext as MainApplication

        setContent {

            val blurEffect by lazy {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderEffect.createBlurEffect(
                    20f, 20f, Shader.TileMode.CLAMP
                )
                else null
            }

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
                val navController = rememberNavController()

                NavHost(navController, "FAB") {
                    composable("APPLIST") { AppListFragmentCompose() }
                    composable("WebView") { WebViewComponent(navController) }

                    composable("FAB") {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val focusRequester = FocusRequester()
                            var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
                            BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

                            ProfileFragmentCompose(
                                navController = navController,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                                    .then(
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                            Modifier.background(Color.Black.copy(alpha = 0.5f))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        fabMenuExpanded = false
                                    }
                                    .graphicsLayer {
                                        renderEffect =
                                            if (fabMenuExpanded) blurEffect?.asComposeRenderEffect() else null
                                    }
                            )

                            if (fabMenuExpanded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                                Modifier.background(
                                                    Color.Black.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            fabMenuExpanded = false
                                        }
                                )
                            }

                            FloatingActionButtonMenu(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                expanded = fabMenuExpanded,
                                button = {
                                    ToggleFloatingActionButton(
                                        modifier = Modifier
                                            .semantics { traversalIndex = -1f }
                                            .animateFloatingActionButton(
                                                visible = true,
                                                alignment = Alignment.BottomEnd,
                                            )
                                            .focusRequester(focusRequester),
                                        checked = fabMenuExpanded,
                                        onCheckedChange = {
                                            fabMenuExpanded = !fabMenuExpanded
                                        }
                                    ) {
                                        val imageVector by remember {
                                            derivedStateOf {
                                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                                            }
                                        }
                                        Icon(
                                            painter = rememberVectorPainter(imageVector),
                                            contentDescription = null,
                                            modifier = Modifier.animateIcon({ checkedProgress }),
                                        )
                                    }
                                }
                            ) {

                                val vpnState by state.collectAsState()

                                val rotation by animateFloatAsState(targetValue = if (vpnState == State.CONNECTED) 90f else 0f)

                                if (vpnState == State.CONNECTED) {
                                    FloatingActionButtonMenuItem(
                                        modifier = Modifier.semantics {
                                            isTraversalGroup = true
                                        },
                                        onClick = {
                                            navController.navigate("WebView")
                                        },
                                        text = { Text(text = "Open") },
                                        icon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.open_in_browser),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                    )
                                }

                                if (vpnState == State.CONNECTED || vpnState == State.DISCONNECTED)
                                    FloatingActionButtonMenuItem(
                                        modifier = Modifier.semantics {
                                            isTraversalGroup = true
                                        },
                                        onClick = {
                                            if (vpnState == State.DISCONNECTED) startService()
                                            else mainApplication?.vpnBinder?.stop()
                                        },
                                        text = {
                                            Icon(
                                                painter = painterResource(
                                                    if (vpnState == State.CONNECTED)
                                                        R.drawable.stop else R.drawable.play_arrow
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .rotate(rotation)
                                            )
                                            Text(
                                                text = if (vpnState == State.CONNECTED) "Stop" else "Start"
                                            )

                                        },
                                        icon = {}
                                    )
                                else ContainedLoadingIndicator(
                                    modifier = Modifier.size(50.dp)
                                )
                            }
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
            mainApplication?.vpnBinder?.unregisterCallback(vpnCallback)
            unbindService(mConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val vpnCallback = VpnCallback()

    inner class VpnCallback : IYuhaiinVpnCallback.Stub() {
        override fun onStateChanged(state: Int) {
            currentState = State.entries[state]
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            mainApplication?.vpnBinder = IYuhaiinVpnBinder.Stub.asInterface(binder).also {
                if (it.isRunning)
                    currentState = State.CONNECTED
                it.registerCallback(vpnCallback)
            }
        }

        override fun onServiceDisconnected(p1: ComponentName) {
            mainApplication?.vpnBinder?.unregisterCallback(vpnCallback)
            mainApplication?.vpnBinder = null
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

@Composable
fun ProfileFragmentCompose(
    navController: NavController,
    modifier: Modifier
) {
    val state = rememberFragmentState()

    AndroidFragment<ProfileFragment>(
        modifier = modifier,
        fragmentState = state
    ) { fragment ->

        fragment.findPreference<Preference>(fragment.resources.getString(R.string.adv_new_app_list_key))
            ?.let {
                it.setOnPreferenceClickListener {
                    navController.navigate("APPLIST")
                    true
                }
            }
    }
}

@Composable
fun AppListFragmentCompose() {
    val state = rememberFragmentState()

    AndroidFragment<AppListDialogFragment>(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        fragmentState = state
    ) { fragment ->

    }
}

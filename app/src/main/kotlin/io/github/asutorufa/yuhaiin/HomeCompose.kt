package io.github.asutorufa.yuhaiin

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.service.YuhaiinVpnService.Companion.State

@SuppressLint("RememberReturnType")
@Composable
fun blurEffect(): RenderEffect? {
    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderEffect.createBlurEffect(
            20f, 20f, Shader.TileMode.CLAMP
        )
        else null
    }
}

@SuppressLint("RememberInComposition")
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.Home(
    modifier: Modifier,
    navController: NavController,
    vpnState: State,
    stopService: () -> Unit,
    startService: () -> Unit,
    animatedContentScope: AnimatedContentScope,
) {
    Box(
        modifier = modifier
    ) {
        val focusRequester = FocusRequester()
        var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
        BackHandler(fabMenuExpanded) { fabMenuExpanded = false }
        val blur = blurEffect()

        SettingCompose(
            animatedContentScope = animatedContentScope,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                        Modifier.background(
                            Color.Black.copy(
                                alpha = if (fabMenuExpanded) 0.5f else 0f
                            )
                        )
                    else Modifier
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    fabMenuExpanded = false
                }
                .graphicsLayer {
                    renderEffect =
                        if (fabMenuExpanded) blur?.asComposeRenderEffect() else null
                },
            navController = navController,
            store = MainApplication.store
        )

        if (fabMenuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        fabMenuExpanded = false
                    }
            )
        }

        FloatingActionButtonMenu(
            modifier = Modifier
                .align(Alignment.BottomEnd),
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
            val rotation by animateFloatAsState(targetValue = if (vpnState == State.CONNECTED) 90f else 0f)

            if (vpnState == State.CONNECTED) {
                FloatingActionButtonMenuItem(
                    modifier = Modifier
                        .semantics {
                            isTraversalGroup = true
                        }
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState("OPEN_WEBVIEW"),
                            animatedVisibilityScope = animatedContentScope,
                        ),
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
                        else stopService()
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
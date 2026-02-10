package io.github.asutorufa.yuhaiin.compose.route

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.compose.SettingsItem
import io.github.asutorufa.yuhaiin.compose.thenIfNotNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.RouteConfig(
    navController: NavController,
    animatedContentScope: AnimatedContentScope?
) {
    SettingsItem(
        textColumnModifier = Modifier.thenIfNotNull(animatedContentScope) {
            sharedBounds(
                sharedContentState = rememberSharedContentState("OPEN_ROUTE_CONFIG_TITLE"),
                animatedVisibilityScope = it,
            )
        },
        iconModifier = Modifier.thenIfNotNull(animatedContentScope) {
            sharedBounds(
                sharedContentState = rememberSharedContentState("OPEN_ROUTE_CONFIG_ICON"),
                animatedVisibilityScope = it,
            )
        },
        title = stringResource(R.string.route_config_title),
        icon = painterResource(R.drawable.router),
        onClick = { navController.navigate("RouteConfig") }
    )
}

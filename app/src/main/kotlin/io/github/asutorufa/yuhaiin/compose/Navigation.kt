package io.github.asutorufa.yuhaiin.compose

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey

@Serializable
data object HomeRoute : AppRoute

@Serializable
data object AboutRoute : AppRoute

@Serializable
data object AppListRoute : AppRoute

@Serializable
data object RouteConfigRoute : AppRoute

@Serializable
data class RouteEditRoute(val routeName: String) : AppRoute

@Serializable
data object WebViewRoute : AppRoute

@Serializable
data object LogcatRoute : AppRoute

class AppNavigator(
    private val backStack: MutableList<NavKey>,
    private val finish: () -> Unit,
) {
    fun push(route: AppRoute) {
        backStack.add(route)
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLast()
        } else {
            finish()
        }
    }
}

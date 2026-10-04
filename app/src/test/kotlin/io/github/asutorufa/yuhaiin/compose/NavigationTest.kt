package io.github.asutorufa.yuhaiin.compose

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    @Test
    fun outgoingToolbarCannotPopTheNextPageOnASecondTap() {
        val backStack = mutableListOf<NavKey>(HomeRoute, RouteConfigRoute, RouteEditRoute("test"))
        var finished = false
        val navigator = AppNavigator(backStack) { finished = true }
        navigator.popFrom(RouteEditRoute("test"))
        navigator.popFrom(RouteEditRoute("test"))
        assertEquals(listOf<NavKey>(HomeRoute, RouteConfigRoute), backStack)
        assertFalse(finished)
    }

    @Test
    fun pushAndPopReturnsFromChildToHome() {
        val backStack = mutableListOf<NavKey>(HomeRoute)
        var finished = false
        val navigator = AppNavigator(backStack) { finished = true }

        navigator.push(AboutRoute)
        assertEquals(listOf<NavKey>(HomeRoute, AboutRoute), backStack)

        navigator.pop()

        assertEquals(listOf<NavKey>(HomeRoute), backStack)
        assertFalse(finished)
    }

    @Test
    fun routeEditPreservesRouteName() {
        val backStack = mutableListOf<NavKey>(HomeRoute)
        val navigator = AppNavigator(backStack) {}

        navigator.push(RouteConfigRoute)
        navigator.push(RouteEditRoute("my-route"))

        assertEquals("my-route", (backStack.last() as RouteEditRoute).routeName)
    }

    @Test
    fun nestedRoutesArePoppedOneAtATime() {
        val backStack = mutableListOf<NavKey>(HomeRoute)
        val navigator = AppNavigator(backStack) {}

        navigator.push(RouteConfigRoute)
        navigator.push(RouteEditRoute("my-route"))
        navigator.pop()

        assertEquals(listOf<NavKey>(HomeRoute, RouteConfigRoute), backStack)

        navigator.pop()

        assertEquals(listOf<NavKey>(HomeRoute), backStack)
    }

    @Test
    fun poppingHomeFinishesWithoutRemovingRoot() {
        val backStack = mutableListOf<NavKey>(HomeRoute)
        var finished = false
        val navigator = AppNavigator(backStack) { finished = true }

        navigator.pop()

        assertEquals(listOf<NavKey>(HomeRoute), backStack)
        assertTrue(finished)
    }

    @Test
    fun rapidRepeatedNavigationOpensOneDestination() {
        val backStack = mutableListOf<NavKey>(HomeRoute)
        val navigator = AppNavigator(backStack) {}
        navigator.push(AboutRoute)
        navigator.push(AboutRoute)
        assertEquals(listOf<NavKey>(HomeRoute, AboutRoute), backStack)
        navigator.pop()
        assertEquals(listOf<NavKey>(HomeRoute), backStack)
    }
}

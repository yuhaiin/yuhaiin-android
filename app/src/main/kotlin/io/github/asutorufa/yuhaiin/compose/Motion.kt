package io.github.asutorufa.yuhaiin.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEvent

/** Shared motion values, because Material 3 1.4.0 keeps `MaterialTheme.motionScheme` internal. */
internal object AppMotion {
    private const val NAVIGATION_MILLIS = 300
    private const val FADE_OUT_MILLIS = 90
    private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Moves layout: bounds, offsets and sizes. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** Changes appearance: colors and opacity. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    val reveal: EnterTransition
        get() = fadeIn(effects()) + expandVertically(spatial())

    val conceal: ExitTransition
        get() = fadeOut(effects()) + shrinkVertically(spatial())

    fun <S> AnimatedContentTransitionScope<S>.navigateForward(): ContentTransform =
        navigate(SlideDirection.Start)

    fun <S> AnimatedContentTransitionScope<S>.navigateBack(): ContentTransform =
        navigate(SlideDirection.End)

    private fun <S> AnimatedContentTransitionScope<S>.navigate(
        direction: SlideDirection
    ): ContentTransform {
        val slide = tween<IntOffset>(NAVIGATION_MILLIS, easing = emphasized)
        return slideIntoContainer(direction, slide) { it / 10 } +
            fadeIn(
                tween(NAVIGATION_MILLIS - FADE_OUT_MILLIS, delayMillis = FADE_OUT_MILLIS)
            ) togetherWith
            slideOutOfContainer(direction, slide) { it / 10 } + fadeOut(tween(FADE_OUT_MILLIS))
    }

    /** Durations here map to gesture progress, so the top page stays opaque until late. */
    fun predictiveBack(@NavigationEvent.SwipeEdge swipeEdge: Int): ContentTransform {
        val pivotX = if (swipeEdge == NavigationEvent.EDGE_RIGHT) 0f else 1f
        return fadeIn(tween(NAVIGATION_MILLIS), initialAlpha = 0.5f) +
            scaleIn(tween(NAVIGATION_MILLIS), initialScale = 0.95f) togetherWith
            scaleOut(
                tween(NAVIGATION_MILLIS),
                targetScale = 0.85f,
                transformOrigin = TransformOrigin(pivotX, 0.5f),
            ) + fadeOut(tween(120, delayMillis = NAVIGATION_MILLIS - 120))
    }
}

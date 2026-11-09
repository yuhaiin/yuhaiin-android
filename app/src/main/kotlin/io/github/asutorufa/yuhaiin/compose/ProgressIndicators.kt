package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Wavy bar for loading with an optional known [progress] in 0..1; indeterminate when null. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialLinearProgressIndicator(modifier: Modifier = Modifier, progress: Float? = null) {
    val colors = MaterialTheme.colorScheme
    val bar = modifier.fillMaxWidth()
    if (progress == null)
        LinearWavyProgressIndicator(
            modifier = bar,
            color = colors.primary,
            trackColor = colors.primaryContainer,
        )
    else
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = bar,
            color = colors.primary,
            trackColor = colors.primaryContainer,
        )
}

/** Large wavy ring for work with no known progress, e.g. starting the core. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialCircularProgressIndicator(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val stroke = remember(density) { Stroke(with(density) { 14.dp.toPx() }, cap = StrokeCap.Round) }
    CircularWavyProgressIndicator(
        modifier = modifier.size(240.dp).progressSemantics(),
        color = colors.primary,
        trackColor = colors.primaryContainer,
        stroke = stroke,
        trackStroke = stroke,
        wavelength = 44.dp,
    )
}

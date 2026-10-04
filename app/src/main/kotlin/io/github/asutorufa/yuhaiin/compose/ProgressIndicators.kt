package io.github.asutorufa.yuhaiin.compose

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.asutorufa.yuhaiin.R
import kotlin.math.roundToInt

// Use the official Expressive widgets until these APIs reach stable Compose Material 3.
@Composable
fun MaterialLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    AndroidView(
        factory = { context ->
            LoadingIndicator(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        modifier = modifier.size(48.dp).progressSemantics(),
        update = { it.setIndicatorColor(color.toArgb()) },
        onRelease = {
            it.drawable.setVisible(false, false)
            it.drawable.callback = null
        },
    )
}

@Composable
fun MaterialLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val fraction = progress?.coerceIn(0f, 1f)
    val colors = MaterialTheme.colorScheme
    val semantics =
        if (fraction == null) Modifier.progressSemantics() else Modifier.progressSemantics(fraction)
    AndroidView(
        factory = { context ->
            (LayoutInflater.from(context)
                    .inflate(R.layout.expressive_linear_progress, FrameLayout(context), false)
                    as LinearProgressIndicator)
                .apply {
                    max = 10_000
                    isIndeterminate = fraction == null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
        },
        modifier = modifier.fillMaxWidth().then(semantics),
        update = { view ->
            view.setIndicatorColor(colors.primary.toArgb())
            view.trackColor = colors.primaryContainer.toArgb()
            // Match the Compose Expressive motion: move one wavelength per second.
            view.waveSpeed =
                if (fraction == null) view.wavelengthIndeterminate else view.wavelengthDeterminate
            if (fraction == null) {
                if (!view.isIndeterminate) view.isIndeterminate = true
            } else {
                val target = (fraction * view.max).roundToInt()
                if (view.isIndeterminate || view.progress != target) {
                    view.setProgressCompat(target, true)
                }
            }
        },
        onRelease = { it.hide() },
    )
}

package io.github.asutorufa.yuhaiin.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// The Web UI's default Olive accent and its warm neutrals (yuhaiin-react src/global.css).
// Used before dynamic color (API 31); later the neutrals still come from here, see AppTheme.
private val brandLight =
    lightColorScheme(
        primary = Color(0xFF5B6D4C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE8CF),
        onPrimaryContainer = Color(0xFF2F3D23),
        secondary = Color(0xFF5C6356),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E5D9),
        onSecondaryContainer = Color(0xFF444B3F),
        tertiary = Color(0xFF79546D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF2E1EC),
        onTertiaryContainer = Color(0xFF5F3D55),
        surface = Color(0xFFF4F5F1),
        onSurface = Color(0xFF20251F),
        surfaceVariant = Color(0xFFECEFE9),
        onSurfaceVariant = Color(0xFF4D5549),
        outline = Color(0xFF7B8475),
        outlineVariant = Color(0xFFDCE0D6),
        surfaceDim = Color(0xFFDADDD5),
        surfaceBright = Color(0xFFFEFEFC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF9FAF6),
        surfaceContainer = Color(0xFFF0F2EC),
        surfaceContainerHigh = Color(0xFFECEFE9),
        surfaceContainerHighest = Color(0xFFE5E8E1),
    )

private val brandDark =
    darkColorScheme(
        primary = Color(0xFFB5C99D),
        onPrimary = Color(0xFF202722),
        primaryContainer = Color(0xFF3E4D33),
        onPrimaryContainer = Color(0xFFD6E6C2),
        secondary = Color(0xFFBAC3B8),
        onSecondary = Color(0xFF262C24),
        secondaryContainer = Color(0xFF3C443A),
        onSecondaryContainer = Color(0xFFD7DFD3),
        tertiary = Color(0xFFD5AAC7),
        onTertiary = Color(0xFF3F2737),
        tertiaryContainer = Color(0xFF573D4F),
        onTertiaryContainer = Color(0xFFF2DAEA),
        surface = Color(0xFF151A17),
        onSurface = Color(0xFFECEFEA),
        surfaceVariant = Color(0xFF28302A),
        onSurfaceVariant = Color(0xFFBAC3B8),
        outline = Color(0xFF88948A),
        outlineVariant = Color(0xFF3A443C),
        surfaceDim = Color(0xFF151A17),
        surfaceBright = Color(0xFF353D37),
        surfaceContainerLowest = Color(0xFF101411),
        surfaceContainerLow = Color(0xFF1A201C),
        surfaceContainer = Color(0xFF202722),
        surfaceContainerHigh = Color(0xFF2B332D),
        surfaceContainerHighest = Color(0xFF353D37),
    )

/**
 * Keeps the dynamic accent but takes every neutral role from the Web UI, so the
 * WebView, the start/error screen and dialogs share one background: no tinted
 * flash before the page paints, and no stripes between Scaffold and ListItem
 * on vendor palettes that separate background from surface.
 */
private fun ColorScheme.withWebNeutrals(base: ColorScheme) =
    copy(
        background = base.surface,
        onBackground = base.onSurface,
        surface = base.surface,
        onSurface = base.onSurface,
        surfaceVariant = base.surfaceVariant,
        onSurfaceVariant = base.onSurfaceVariant,
        outline = base.outline,
        outlineVariant = base.outlineVariant,
        surfaceDim = base.surfaceDim,
        surfaceBright = base.surfaceBright,
        surfaceContainerLowest = base.surfaceContainerLowest,
        surfaceContainerLow = base.surfaceContainerLow,
        surfaceContainer = base.surfaceContainer,
        surfaceContainerHigh = base.surfaceContainerHigh,
        surfaceContainerHighest = base.surfaceContainerHighest,
    )

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val brand = if (dark) brandDark else brandLight
    val scheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                .withWebNeutrals(brand)
        } else brand
    MaterialTheme(colorScheme = scheme, content = content)
}

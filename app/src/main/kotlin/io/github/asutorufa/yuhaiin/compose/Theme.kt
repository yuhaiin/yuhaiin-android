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

// Tonal palette around the app icon's center packet, #526FC5. Used before dynamic color (API 31).
private val brandLight =
    lightColorScheme(
        primary = Color(0xFF485D92),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDAE2FF),
        onPrimaryContainer = Color(0xFF304578),
        secondary = Color(0xFF585E71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCE2F9),
        onSecondaryContainer = Color(0xFF404659),
        tertiary = Color(0xFF725572),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFDD7FA),
        onTertiaryContainer = Color(0xFF593D5A),
        surface = Color(0xFFFAF8FF),
        onSurface = Color(0xFF1A1B21),
        surfaceVariant = Color(0xFFE2E2EC),
        onSurfaceVariant = Color(0xFF45464F),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC6C6D0),
        surfaceDim = Color(0xFFDAD9E0),
        surfaceBright = Color(0xFFFAF8FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F3FA),
        surfaceContainer = Color(0xFFEEEDF4),
        surfaceContainerHigh = Color(0xFFE9E7EF),
        surfaceContainerHighest = Color(0xFFE3E2E9),
    )

private val brandDark =
    darkColorScheme(
        primary = Color(0xFFB1C5FF),
        onPrimary = Color(0xFF172E60),
        primaryContainer = Color(0xFF304578),
        onPrimaryContainer = Color(0xFFDAE2FF),
        secondary = Color(0xFFC0C6DC),
        onSecondary = Color(0xFF2A3042),
        secondaryContainer = Color(0xFF404659),
        onSecondaryContainer = Color(0xFFDCE2F9),
        tertiary = Color(0xFFE0BBDE),
        onTertiary = Color(0xFF412742),
        tertiaryContainer = Color(0xFF593D5A),
        onTertiaryContainer = Color(0xFFFDD7FA),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE3E2E9),
        surfaceVariant = Color(0xFF45464F),
        onSurfaceVariant = Color(0xFFC6C6D0),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF45464F),
        surfaceDim = Color(0xFF121318),
        surfaceBright = Color(0xFF38393F),
        surfaceContainerLowest = Color(0xFF0D0E13),
        surfaceContainerLow = Color(0xFF1A1B21),
        surfaceContainer = Color(0xFF1E1F25),
        surfaceContainerHigh = Color(0xFF292A2F),
        surfaceContainerHighest = Color(0xFF34343A),
    )

/** Some vendors' dynamic palettes separate these, which stripes Scaffold against ListItem. */
private fun ColorScheme.withUniformBackground() =
    copy(background = surface, onBackground = onSurface)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            dark -> brandDark
            else -> brandLight
        }
    MaterialTheme(colorScheme = scheme.withUniformBackground(), content = content)
}

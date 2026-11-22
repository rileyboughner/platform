package dev.rileyboughner.tvlauncher.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Minimal black-and-white theme for the TV launcher: pure black
 * background, pure white text, one dark-gray hairline. No accent color —
 * the home screen's color comes from the synced wallpaper behind the
 * tiles, not from a tinted theme.
 */
private val TvBlack = Color(0xFF000000)
private val TvWhite = Color(0xFFFFFFFF)
private val TvHairline = Color(0xFF2A2A2A)

@Composable
fun TvLauncherTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = TvBlack,
        surface = TvBlack,
        surfaceVariant = TvBlack,
        onBackground = TvWhite,
        onSurface = TvWhite,
        onSurfaceVariant = TvWhite,
        primary = TvWhite,
        onPrimary = TvBlack,
        secondary = TvWhite,
        onSecondary = TvBlack,
        error = TvWhite,
        onError = TvBlack,
        outline = TvHairline,
    )
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

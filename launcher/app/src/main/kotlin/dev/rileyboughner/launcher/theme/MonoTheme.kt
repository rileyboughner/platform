package dev.rileyboughner.launcher.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Riley's requested restyle: a minimal black-and-white text launcher
 * (Olauncher / Before Launcher style). This intentionally overrides the
 * rose-pine wiring in [AppTheme] for the phone/tablet UI — colors are
 * fixed (pure black, pure white, one dark-gray hairline), not sourced from
 * [platform.theme.PlatformTheme]. The Google TV / Leanback UI
 * ([dev.rileyboughner.launcher.ui.TvHomeScreen]) uses this same theme, for
 * the same minimal black-and-white look on both form factors.
 */
private val MonoBlack = Color(0xFF000000)
private val MonoWhite = Color(0xFFFFFFFF)
private val MonoHairline = Color(0xFF2A2A2A)

private val MonoColors = darkColorScheme(
    background = MonoBlack,
    surface = MonoBlack,
    surfaceVariant = MonoBlack,
    onBackground = MonoWhite,
    onSurface = MonoWhite,
    onSurfaceVariant = MonoWhite,
    primary = MonoWhite,
    onPrimary = MonoBlack,
    secondary = MonoWhite,
    onSecondary = MonoBlack,
    error = MonoWhite,
    onError = MonoBlack,
    outline = MonoHairline,
)

@Composable
fun MonoLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonoColors,
        content = content,
    )
}

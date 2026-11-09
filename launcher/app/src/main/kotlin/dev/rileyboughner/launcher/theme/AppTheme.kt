package dev.rileyboughner.launcher.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import platform.theme.PlatformTheme

/**
 * Maps the generated rose-pine [PlatformTheme] (theme/build/android/Theme.kt,
 * Phase 1 Style Dictionary output) onto a Compose Material3 color scheme.
 *
 * No color value is defined here — every field below is a direct reference
 * into [PlatformTheme]. If the palette changes, it changes in `theme/`
 * and flows through automatically.
 */
private val RosePineDarkColors = darkColorScheme(
    background = PlatformTheme.colorSemanticBackground,
    surface = PlatformTheme.colorSemanticSurface,
    onBackground = PlatformTheme.colorSemanticText,
    onSurface = PlatformTheme.colorSemanticText,
    primary = PlatformTheme.colorSemanticAccent,
    onPrimary = PlatformTheme.colorBase,
    secondary = PlatformTheme.colorSemanticAccentSecondary,
    onSecondary = PlatformTheme.colorBase,
    error = PlatformTheme.colorSemanticError,
    onError = PlatformTheme.colorBase,
    outline = PlatformTheme.colorSemanticBorder,
)

private val RosePineLightColors = lightColorScheme(
    background = PlatformTheme.colorSemanticBackground,
    surface = PlatformTheme.colorSemanticSurface,
    onBackground = PlatformTheme.colorSemanticText,
    onSurface = PlatformTheme.colorSemanticText,
    primary = PlatformTheme.colorSemanticAccent,
    onPrimary = PlatformTheme.colorBase,
    secondary = PlatformTheme.colorSemanticAccentSecondary,
    onSecondary = PlatformTheme.colorBase,
    error = PlatformTheme.colorSemanticError,
    onError = PlatformTheme.colorBase,
    outline = PlatformTheme.colorSemanticBorder,
)

/**
 * Shared Compose theme wrapper for both the phone/tablet UI and the TV UI.
 * Rose Pine is a fixed dark-first palette (see platform proposal section 2),
 * but we still respect system dark/light since Material3 expects both.
 */
@Composable
fun LauncherTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) RosePineDarkColors else RosePineLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

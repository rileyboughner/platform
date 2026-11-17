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
 * the same minimal black-and-white look on both form factors (and always
 * the default [accentColor] — TV never calls theme sync, see
 * [dev.rileyboughner.launcher.theme.ThemeSyncRepository]).
 *
 * [accentColor] threads a synced theme's accent (`colors.color4` in
 * `dotfiles/config/themes/<key>.json`, see [accentColorFor]) into
 * `primary`/`secondary` only -- every other role stays pure black/white, so
 * a synced theme shows up as an accent/highlight (e.g. a `Switch`'s thumb,
 * or the active page dot) rather than a redesign of the minimal look.
 */
private val MonoBlack = Color(0xFF000000)
private val MonoWhite = Color(0xFFFFFFFF)
private val MonoHairline = Color(0xFF2A2A2A)

@Composable
fun MonoLauncherTheme(accentColor: Color = MonoWhite, content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = MonoBlack,
        surface = MonoBlack,
        surfaceVariant = MonoBlack,
        onBackground = MonoWhite,
        onSurface = MonoWhite,
        onSurfaceVariant = MonoWhite,
        primary = accentColor,
        onPrimary = MonoBlack,
        secondary = accentColor,
        onSecondary = MonoBlack,
        error = MonoWhite,
        onError = MonoBlack,
        outline = MonoHairline,
    )
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

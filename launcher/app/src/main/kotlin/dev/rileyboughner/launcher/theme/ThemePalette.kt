package dev.rileyboughner.launcher.theme

import androidx.compose.ui.graphics.Color

/**
 * The 11 themes in `dotfiles/config/themes` (one JSON file per theme), read
 * directly from those files (never invented here) -- `key` is the theme's
 * filename stem, which is exactly the string the theme-sync hub stores in a
 * state document's `theme` field (see `dotfiles/scripts/lib/theme-common.sh`'s
 * `theme_resolve` and `boner theme set`). `accent` is each theme's
 * `colors.color4`, the one field that reads as a genuine accent (not
 * grayscale) in every theme but Graphite.
 *
 * Graphite is the launcher's own default black-and-white look
 * ([MonoLauncherTheme]'s un-tinted colors) rather than its literal
 * `color4` (`#c9c9c9`, barely distinguishable from white) -- see
 * [accentColorFor].
 */
data class SyncedTheme(val key: String, val displayName: String, val accent: Color)

val SYNCED_THEMES: List<SyncedTheme> = listOf(
    SyncedTheme("graphite", "Graphite", Color(0xFFFFFFFF)),
    SyncedTheme("catppuccin-mocha", "Catppuccin Mocha", Color(0xFF89B4FA)),
    SyncedTheme("dracula", "Dracula", Color(0xFFBD93F9)),
    SyncedTheme("everforest-dark", "Everforest Dark", Color(0xFF7FBBB3)),
    SyncedTheme("gruvbox-dark", "Gruvbox Dark", Color(0xFF458588)),
    SyncedTheme("kanagawa", "Kanagawa", Color(0xFF7E9CD8)),
    SyncedTheme("nord", "Nord", Color(0xFF81A1C1)),
    SyncedTheme("one-dark", "One Dark", Color(0xFF61AFEF)),
    SyncedTheme("rose-pine", "Rose Pine", Color(0xFF9CCFD8)),
    SyncedTheme("solarized-dark", "Solarized Dark", Color(0xFF2075C7)),
    SyncedTheme("tokyo-night", "Tokyo Night", Color(0xFF7AA2F7)),
)

private val SYNCED_THEMES_BY_KEY = SYNCED_THEMES.associateBy { it.key }

const val DEFAULT_SYNCED_THEME_KEY = "graphite"

/** Falls back to Graphite's (un-tinted) accent for an unpinned/unknown/null theme key -- there is no "auto" concept on the launcher. */
fun accentColorFor(themeKey: String?): Color =
    SYNCED_THEMES_BY_KEY[themeKey]?.accent ?: SYNCED_THEMES_BY_KEY[DEFAULT_SYNCED_THEME_KEY]!!.accent

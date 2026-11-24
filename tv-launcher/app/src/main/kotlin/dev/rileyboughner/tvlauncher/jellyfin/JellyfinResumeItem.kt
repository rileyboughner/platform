package dev.rileyboughner.tvlauncher.jellyfin

/** One "continue watching" item: enough to render a poster + progress bar + remaining time and open Jellyfin. */
data class JellyfinResumeItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val progressFraction: Float,
    val remainingText: String?,
)

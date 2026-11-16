package dev.rileyboughner.launcher.jellyfin

/** One "continue watching" item, with enough to render a poster + progress bar and deep link back into Jellyfin. */
data class JellyfinResumeItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val progressFraction: Float,
)

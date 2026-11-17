package dev.rileyboughner.launcher.theme

import org.json.JSONObject

/**
 * One theme-sync hub state document -- see
 * `dotfiles/scripts/theme-sync/README.md`'s "State document" section. Same
 * five fields the hub requires on every `POST /state` (or `/state/<group>`):
 * a whole-document replace, last-write-wins by `updatedAt`, no merging.
 */
data class ThemeSyncStateDoc(
    val theme: String?,
    val pinned: Boolean,
    val wallpaper: String,
    val wallpaperSha256: String,
    val updatedAt: Long,
    val writer: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme ?: JSONObject.NULL)
        put("pinned", pinned)
        put("wallpaper", wallpaper)
        put("wallpaper_sha256", wallpaperSha256)
        put("updated_at", updatedAt)
        put("writer", writer)
    }

    companion object {
        /** Null for a malformed/incomplete document (e.g. `{}` from an empty hub) rather than throwing. */
        fun fromJsonOrNull(json: JSONObject): ThemeSyncStateDoc? {
            if (!json.has("updated_at") || !json.has("pinned")) return null
            return runCatching {
                ThemeSyncStateDoc(
                    theme = json.optString("theme", "").takeIf { it.isNotEmpty() && !json.isNull("theme") },
                    pinned = json.getBoolean("pinned"),
                    wallpaper = json.optString("wallpaper", ""),
                    wallpaperSha256 = json.optString("wallpaper_sha256", ""),
                    updatedAt = json.getLong("updated_at"),
                    writer = json.optString("writer", ""),
                )
            }.getOrNull()
        }
    }
}

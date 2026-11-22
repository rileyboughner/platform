package dev.rileyboughner.tvlauncher.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.rileyboughner.tvlauncher.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val GROUP_MOBILE = "mobile"
private const val MODE_CACHE_TTL_MS = 60_000L

/** Result of one [ThemeSyncRepository.pollTv] cycle: the theme key to show, plus a freshly downloaded wallpaper bitmap when the hub's wallpaper actually changed or this process hasn't downloaded it yet (null when the caller already holds this run's bitmap — callers keep showing whatever bitmap they already have). */
data class TvSyncResult(val themeKey: String, val wallpaperBitmap: Bitmap?)

/**
 * Read-only client for the theme-sync hub — mirrors
 * `dotfiles/scripts/theme-sync-watch` (the desktop/laptop watcher) closely
 * enough that the two sides of the protocol can't drift, but this app only
 * ever polls: it reads the hub state and downloads the wallpaper blob, and
 * never pushes anything to the hub.
 *
 * [pollTv] is always on (the TV has no settings screen, so no toggle) and,
 * instead of setting the system wallpaper, hands the downloaded bitmap
 * back for the home screen to draw as its background. Returns null when
 * nothing changed (hub unreachable, hub has no state yet, or the hub's
 * state is no newer than what's already applied AND this process already
 * holds the wallpaper bitmap) — callers keep whatever accent/bitmap
 * they're already showing in that case. A fresh process re-downloads the
 * bitmap even with an unchanged hub state, since the bitmap it would
 * "keep showing" doesn't exist yet.
 *
 * Every public function is a no-op (and returns null, never throws) when
 * the hub is unreachable. All network and bitmap I/O runs on
 * [Dispatchers.IO]; nothing here ever touches the main thread.
 */
class ThemeSyncRepository(
    private val settingsStore: SettingsStore,
) {

    /**
     * The wallpaper hash whose bitmap THIS process already downloaded for
     * the TV. In-memory on purpose, never persisted: the TV's bitmap
     * lives only in memory (MainActivity holds it in a remember{}), so
     * a fresh process must re-download even when the hub state is
     * unchanged. A failed download leaves this unset, so the next poll
     * cycle retries.
     */
    private var lastTvWallpaperSha256: String? = null

    suspend fun pollTv(): TvSyncResult? = withContext(Dispatchers.IO) {
        val baseUrl = ThemeSyncApi.findHubBaseUrl() ?: return@withContext null
        val path = statePath(resolveMode(baseUrl))
        val doc = ThemeSyncApi.getState(baseUrl, path)?.let { ThemeSyncStateDoc.fromJsonOrNull(it) }
            ?: return@withContext null
        val bitmapNeeded = doc.wallpaperSha256.isNotBlank() && lastTvWallpaperSha256 != doc.wallpaperSha256
        val stateIsNewer = doc.updatedAt > settingsStore.getSyncLastAppliedUpdatedAt()
        if (!stateIsNewer && !bitmapNeeded) return@withContext null

        val bitmap = if (bitmapNeeded) {
            ThemeSyncApi.getBlob(baseUrl, doc.wallpaperSha256)
                ?.let { decodeDownsampled(it) }
                ?.also { lastTvWallpaperSha256 = doc.wallpaperSha256 }
        } else {
            null
        }
        settingsStore.setSyncLastAppliedUpdatedAt(doc.updatedAt)
        settingsStore.setSyncLastStateJson(doc.toJson().toString())
        val themeKey = if (doc.pinned) doc.theme else null
        val key = (themeKey ?: SettingsStore.DEFAULT_SYNCED_THEME_KEY).also { settingsStore.setSyncTheme(it) }
        TvSyncResult(key, bitmap)
    }

    /** Decodes wallpaper bytes downsampled to at most 1920x1080 (the TV panel's fixed resolution), so a multi-megapixel photo doesn't sit in memory at full size just to be drawn behind the tiles. */
    private fun decodeDownsampled(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1920 || bounds.outHeight / sampleSize > 1080) sampleSize *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }.getOrNull()

    /** Mode, cached for [MODE_CACHE_TTL_MS] — same short-TTL-with-stale-fallback shape as `theme_sync_get_mode` in lib/theme-sync-common.sh, so a flaky hub round-trip doesn't flip this device's state path back and forth. */
    private fun resolveMode(baseUrl: String): String {
        val cachedAt = settingsStore.getSyncModeCacheFetchedAt()
        if (System.currentTimeMillis() - cachedAt < MODE_CACHE_TTL_MS) {
            return settingsStore.getSyncModeCache()
        }
        val fetched = ThemeSyncApi.getMode(baseUrl)
        if (fetched != null) {
            settingsStore.setSyncModeCache(fetched)
            settingsStore.setSyncModeCacheFetchedAt(System.currentTimeMillis())
            return fetched
        }
        return settingsStore.getSyncModeCache()
    }

    private fun statePath(mode: String): String = if (mode == "split") "state/$GROUP_MOBILE" else "state"
}

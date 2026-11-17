package dev.rileyboughner.launcher.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import dev.rileyboughner.launcher.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val GROUP_MOBILE = "mobile"
private const val MODE_CACHE_TTL_MS = 60_000L

/** Result of one [ThemeSyncRepository.pollTv] cycle: the theme key to show as the launcher's accent, plus a freshly downloaded wallpaper bitmap when the hub's wallpaper actually changed or this process hasn't downloaded it yet (null when the caller already holds this run's bitmap — callers keep showing whatever bitmap they already have). */
data class TvSyncResult(val themeKey: String, val wallpaperBitmap: Bitmap?)

/**
 * Phone/tablet/TV client for the theme-sync hub -- mirrors
 * `dotfiles/scripts/theme-sync-watch` (the desktop/laptop watcher) closely
 * enough that the two sides of the protocol can't drift, but is pull-mostly:
 * it polls the hub and applies wallpaper + theme, and only ever pushes when
 * Riley explicitly picks a theme in Settings (never a background push, and
 * never the wallpaper half -- phones don't set their own wallpaper from
 * this app). Phones/tablets use the "mobile" group in split mode, same as
 * the watcher uses "workstation" for desktop/laptop.
 *
 * The TV uses [pollTv] instead of [poll]: same hub read, but always on
 * (the TV has no settings screen, so no toggle) and the wallpaper comes
 * back as a bitmap for the TV home screen to draw itself, since the
 * system wallpaper is invisible behind the TV UI's opaque theme.
 *
 * Every public function is a no-op (and returns null/false, never throws)
 * when sync is disabled or the hub is unreachable -- see [poll] and [push].
 * All network and bitmap I/O runs on [Dispatchers.IO]; nothing here ever
 * touches the main thread.
 */
class ThemeSyncRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * The wallpaper hash whose bitmap THIS process already downloaded for
     * the TV. In-memory on purpose, never persisted: the TV's bitmap
     * lives only in memory (TvMainActivity holds it in a remember{}), so
     * a fresh process must re-download even when the hub state is
     * unchanged — keying the skip off the persisted "last applied"
     * timestamp instead left the TV black after every power-on until the
     * next wallpaper change (2026-11-17). A failed download leaves this
     * unset, so the next 5s poll cycle retries.
     */
    private var lastTvWallpaperSha256: String? = null
    /**
     * One poll cycle: finds the hub, reads this device's group/shared state,
     * and -- only if it's newer than what was last applied -- applies the
     * wallpaper and returns the theme key to show as the launcher's accent.
     * Returns null when nothing changed (including: sync off, hub
     * unreachable, hub has no state yet, or the hub's state is no newer
     * than what's already applied) -- callers should keep whatever accent
     * they're already showing in that case.
     */
    suspend fun poll(): String? = withContext(Dispatchers.IO) {
        if (!settingsRepository.getSyncEnabled()) return@withContext null
        val baseUrl = ThemeSyncApi.findHubBaseUrl() ?: return@withContext null
        val path = statePath(resolveMode(baseUrl))
        val doc = ThemeSyncApi.getState(baseUrl, path)?.let { ThemeSyncStateDoc.fromJsonOrNull(it) }
            ?: return@withContext null
        if (doc.updatedAt <= settingsRepository.getSyncLastAppliedUpdatedAt()) return@withContext null

        applyWallpaperIfChanged(baseUrl, doc)
        settingsRepository.setSyncLastAppliedUpdatedAt(doc.updatedAt)
        settingsRepository.setSyncLastStateJson(doc.toJson().toString())
        val themeKey = if (doc.pinned) doc.theme else null
        (themeKey ?: DEFAULT_SYNCED_THEME_KEY).also { settingsRepository.setSyncTheme(it) }
    }

    /**
     * TV variant of [poll]: same hub read and apply bookkeeping, but it
     * does NOT check the Settings sync toggle (the TV has no settings
     * screen, so TV sync is always on) and instead of setting the system
     * wallpaper -- invisible behind the TV UI's opaque theme -- it hands
     * the downloaded bitmap back for the TV home screen to draw as its
     * background. Returns null when nothing changed (hub unreachable, hub
     * has no state yet, or the hub's state is no newer than what's already
     * applied AND this process already holds the wallpaper bitmap) --
     * callers keep whatever accent/bitmap they're already showing in that
     * case. A fresh process re-downloads the bitmap even with an
     * unchanged hub state, since the bitmap it would "keep showing"
     * doesn't exist yet.
     */
    suspend fun pollTv(): TvSyncResult? = withContext(Dispatchers.IO) {
        val baseUrl = ThemeSyncApi.findHubBaseUrl() ?: return@withContext null
        val path = statePath(resolveMode(baseUrl))
        val doc = ThemeSyncApi.getState(baseUrl, path)?.let { ThemeSyncStateDoc.fromJsonOrNull(it) }
            ?: return@withContext null
        val bitmapNeeded = doc.wallpaperSha256.isNotBlank() && lastTvWallpaperSha256 != doc.wallpaperSha256
        val stateIsNewer = doc.updatedAt > settingsRepository.getSyncLastAppliedUpdatedAt()
        if (!stateIsNewer && !bitmapNeeded) return@withContext null

        // Deliberately NOT downloadWallpaperBytes: that one skips off the
        // PERSISTED last-state hash, which is exactly the check that must
        // not apply to a fresh TV process (see lastTvWallpaperSha256).
        val bitmap = if (bitmapNeeded) {
            ThemeSyncApi.getBlob(baseUrl, doc.wallpaperSha256)
                ?.let { decodeDownsampled(it) }
                ?.also { lastTvWallpaperSha256 = doc.wallpaperSha256 }
        } else {
            null
        }
        settingsRepository.setSyncLastAppliedUpdatedAt(doc.updatedAt)
        settingsRepository.setSyncLastStateJson(doc.toJson().toString())
        val themeKey = if (doc.pinned) doc.theme else null
        val key = (themeKey ?: DEFAULT_SYNCED_THEME_KEY).also { settingsRepository.setSyncTheme(it) }
        TvSyncResult(key, bitmap)
    }

    /**
     * Picks a theme locally (always, even with sync off -- the accent
     * applies immediately either way) and, only when sync is on, pushes it
     * to the hub. The pushed document carries forward the wallpaper fields
     * from the last state this device knew about, since the hub's
     * `POST /state` replaces the whole document -- a theme-only push with
     * blank wallpaper fields would otherwise wipe out whatever wallpaper
     * the group was already synced to.
     */
    suspend fun pushTheme(themeKey: String): Unit = withContext(Dispatchers.IO) {
        settingsRepository.setSyncTheme(themeKey)
        if (!settingsRepository.getSyncEnabled()) return@withContext
        val baseUrl = ThemeSyncApi.findHubBaseUrl() ?: return@withContext
        val path = statePath(resolveMode(baseUrl))
        val lastKnown = settingsRepository.getSyncLastStateJson()
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { ThemeSyncStateDoc.fromJsonOrNull(it) }

        val outgoing = ThemeSyncStateDoc(
            theme = themeKey,
            pinned = true,
            wallpaper = lastKnown?.wallpaper ?: "",
            wallpaperSha256 = lastKnown?.wallpaperSha256 ?: "",
            updatedAt = System.currentTimeMillis() / 1000,
            writer = Build.MODEL ?: "launcher",
        )
        applyAuthoritativeState(ThemeSyncApi.postState(baseUrl, path, outgoing.toJson()) ?: return@withContext)
    }

    /**
     * Sets the hub's sync mode (unified/split) -- a global setting shared
     * with the workstation side, see `boner sync mode`. With sync off this
     * only updates the local cache (consistent with "sync off means no
     * writing"); it takes effect hub-side the next time sync is turned on.
     */
    suspend fun pushMode(mode: String): Boolean = withContext(Dispatchers.IO) {
        if (mode != "unified" && mode != "split") return@withContext false
        if (!settingsRepository.getSyncEnabled()) {
            settingsRepository.setSyncModeCache(mode)
            settingsRepository.setSyncModeCacheFetchedAt(System.currentTimeMillis())
            return@withContext true
        }
        val baseUrl = ThemeSyncApi.findHubBaseUrl() ?: return@withContext false
        val accepted = ThemeSyncApi.postMode(baseUrl, mode) ?: return@withContext false
        settingsRepository.setSyncModeCache(accepted)
        settingsRepository.setSyncModeCacheFetchedAt(System.currentTimeMillis())
        accepted == mode
    }

    /** Applies a state document the hub just handed back as authoritative (own push accepted, or a concurrent newer write won instead). */
    private fun applyAuthoritativeState(doc: JSONObject) {
        val parsed = ThemeSyncStateDoc.fromJsonOrNull(doc) ?: return
        settingsRepository.setSyncLastAppliedUpdatedAt(parsed.updatedAt)
        settingsRepository.setSyncLastStateJson(parsed.toJson().toString())
        settingsRepository.setSyncTheme(if (parsed.pinned) parsed.theme ?: DEFAULT_SYNCED_THEME_KEY else DEFAULT_SYNCED_THEME_KEY)
    }

    /** Downloads and applies the hub's wallpaper blob only when its hash actually differs from what's already applied -- never re-downloads or re-applies a no-op. */
    private fun applyWallpaperIfChanged(baseUrl: String, doc: ThemeSyncStateDoc) {
        val bytes = downloadWallpaperBytes(baseUrl, doc) ?: return
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return
        runCatching { WallpaperManager.getInstance(context).setBitmap(bitmap) }
    }

    /** Downloads the hub's wallpaper blob only when its hash differs from the last state this device applied; null when blank, unchanged, or unreachable. Shared by [poll] (system wallpaper) and [pollTv] (drawn background). */
    private fun downloadWallpaperBytes(baseUrl: String, doc: ThemeSyncStateDoc): ByteArray? {
        if (doc.wallpaperSha256.isBlank()) return null
        val lastSha = settingsRepository.getSyncLastStateJson()
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optString("wallpaper_sha256", "")
        if (lastSha == doc.wallpaperSha256) return null
        return ThemeSyncApi.getBlob(baseUrl, doc.wallpaperSha256)
    }

    /** Decodes wallpaper bytes downsampled to at most 1920x1080 (the TV panel's fixed resolution), so a multi-megapixel photo doesn't sit in memory at full size just to be drawn behind the tiles. */
    private fun decodeDownsampled(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1920 || bounds.outHeight / sampleSize > 1080) sampleSize *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }.getOrNull()

    /** Mode, cached for [MODE_CACHE_TTL_MS] -- same short-TTL-with-stale-fallback shape as `theme_sync_get_mode` in lib/theme-sync-common.sh, so a flaky hub round-trip doesn't flip this device's state path back and forth. */
    private fun resolveMode(baseUrl: String): String {
        val cachedAt = settingsRepository.getSyncModeCacheFetchedAt()
        if (System.currentTimeMillis() - cachedAt < MODE_CACHE_TTL_MS) {
            return settingsRepository.getSyncModeCache()
        }
        val fetched = ThemeSyncApi.getMode(baseUrl)
        if (fetched != null) {
            settingsRepository.setSyncModeCache(fetched)
            settingsRepository.setSyncModeCacheFetchedAt(System.currentTimeMillis())
            return fetched
        }
        return settingsRepository.getSyncModeCache()
    }

    private fun statePath(mode: String): String = if (mode == "split") "state/$GROUP_MOBILE" else "state"
}

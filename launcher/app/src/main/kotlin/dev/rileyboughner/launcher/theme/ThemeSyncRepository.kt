package dev.rileyboughner.launcher.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import dev.rileyboughner.launcher.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val GROUP_MOBILE = "mobile"
private const val MODE_CACHE_TTL_MS = 60_000L

/**
 * Phone/tablet client for the theme-sync hub -- mirrors
 * `dotfiles/scripts/theme-sync-watch` (the desktop/laptop watcher) closely
 * enough that the two sides of the protocol can't drift, but is pull-mostly:
 * it polls the hub and applies wallpaper + theme, and only ever pushes when
 * Riley explicitly picks a theme in Settings (never a background push, and
 * never the wallpaper half -- phones don't set their own wallpaper from
 * this app). Phones/tablets use the "mobile" group in split mode, same as
 * the watcher uses "workstation" for desktop/laptop.
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
        if (doc.wallpaperSha256.isBlank()) return
        val lastSha = settingsRepository.getSyncLastStateJson()
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optString("wallpaper_sha256", "")
        if (lastSha == doc.wallpaperSha256) return

        val bytes = ThemeSyncApi.getBlob(baseUrl, doc.wallpaperSha256) ?: return
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return
        runCatching { WallpaperManager.getInstance(context).setBitmap(bitmap) }
    }

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

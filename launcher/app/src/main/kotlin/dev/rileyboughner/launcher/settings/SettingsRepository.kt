package dev.rileyboughner.launcher.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.rileyboughner.launcher.BuildConfig
import dev.rileyboughner.launcher.FavoritesRepository
import dev.rileyboughner.launcher.LauncherAppInfo
import dev.rileyboughner.launcher.theme.DEFAULT_SYNCED_THEME_KEY
import org.json.JSONArray
import org.json.JSONObject

/** One main-page favorite as picked in [MainPageFavoritesPickerScreen]: an installed app plus its custom display label. */
data class MainPageFavorite(val packageName: String, val label: String)

/**
 * Stores user-entered server URLs and API tokens (Todoist, Jellyfin, Home
 * Assistant) in [EncryptedSharedPreferences]. Tokens/keys are never logged —
 * see [dev.rileyboughner.launcher.todoist.TodoistApi], `JellyfinApi`, and
 * `HomeAssistantApi`.
 *
 * Getters fall back to a [BuildConfig] default (compiled in at build time
 * from an environment variable outside this repo, empty if unset) when
 * nothing has been saved yet. Saving a value from the Settings screen always
 * takes priority over the compiled-in default.
 */
class SettingsRepository(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getTodoistToken(): String? =
        prefs.getString(KEY_TODOIST_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_TODOIST_TOKEN.takeIf { it.isNotBlank() }

    fun setTodoistToken(token: String) {
        prefs.edit().putString(KEY_TODOIST_TOKEN, token.trim()).apply()
    }

    fun getJellyfinServerUrl(): String =
        prefs.getString(KEY_JELLYFIN_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_JELLYFIN_SERVER_URL

    fun setJellyfinServerUrl(url: String) {
        prefs.edit().putString(KEY_JELLYFIN_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    fun getJellyfinApiKey(): String? =
        prefs.getString(KEY_JELLYFIN_API_KEY, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_JELLYFIN_API_KEY.takeIf { it.isNotBlank() }

    fun setJellyfinApiKey(apiKey: String) {
        prefs.edit().putString(KEY_JELLYFIN_API_KEY, apiKey.trim()).apply()
    }

    fun getHomeAssistantServerUrl(): String =
        prefs.getString(KEY_HOME_ASSISTANT_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOME_ASSISTANT_URL

    fun setHomeAssistantServerUrl(url: String) {
        prefs.edit().putString(KEY_HOME_ASSISTANT_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    fun getHomeAssistantToken(): String? =
        prefs.getString(KEY_HOME_ASSISTANT_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_HOME_ASSISTANT_TOKEN.takeIf { it.isNotBlank() }

    fun setHomeAssistantToken(token: String) {
        prefs.edit().putString(KEY_HOME_ASSISTANT_TOKEN, token.trim()).apply()
    }

    // No token/password involved — Duolingo's unofficial public-profile
    // endpoint only needs a username, so there's no BuildConfig default here.
    fun getDuolingoUsername(): String? =
        prefs.getString(KEY_DUOLINGO_USERNAME, null)?.takeIf { it.isNotBlank() }

    fun setDuolingoUsername(username: String) {
        prefs.edit().putString(KEY_DUOLINGO_USERNAME, username.trim()).apply()
    }

    fun getShowGlanceInfo(): Boolean = prefs.getBoolean(KEY_SHOW_GLANCE_INFO, true)

    fun setShowGlanceInfo(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_GLANCE_INFO, show).apply()
    }

    // Theme/wallpaper sync with the server hub (dotfiles/scripts/theme-sync) --
    // see dev.rileyboughner.launcher.theme.ThemeSyncRepository for the client.
    // Default off, matching the workstation watcher's own default-off.
    fun getSyncEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_ENABLED, false)

    fun setSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    // The theme key (dotfiles/config/themes/<key>.json stem) currently applied
    // to this device's accent color -- either picked locally in Settings, or
    // last pulled from the hub. Persisted so the accent survives a process
    // restart with no network round-trip.
    fun getSyncTheme(): String = prefs.getString(KEY_SYNC_THEME, null) ?: DEFAULT_SYNCED_THEME_KEY

    fun setSyncTheme(themeKey: String) {
        prefs.edit().putString(KEY_SYNC_THEME, themeKey).apply()
    }

    // Last known hub sync mode ("unified"/"split"), cached locally the same
    // way the watcher caches it (theme_sync_get_mode in
    // lib/theme-sync-common.sh) so a hub-unreachable moment still has an
    // answer for which state endpoint to use.
    fun getSyncModeCache(): String = prefs.getString(KEY_SYNC_MODE_CACHE, null) ?: "unified"

    fun setSyncModeCache(mode: String) {
        prefs.edit().putString(KEY_SYNC_MODE_CACHE, mode).apply()
    }

    fun getSyncModeCacheFetchedAt(): Long = prefs.getLong(KEY_SYNC_MODE_CACHE_AT, 0L)

    fun setSyncModeCacheFetchedAt(epochMillis: Long) {
        prefs.edit().putLong(KEY_SYNC_MODE_CACHE_AT, epochMillis).apply()
    }

    // The full hub state document last applied to (or accepted from) this
    // device, serialized as JSON. Lets a theme-only push carry forward the
    // wallpaper fields it doesn't itself know about, instead of clobbering
    // them -- the hub's POST /state is a whole-document replace, not a merge.
    fun getSyncLastStateJson(): String? = prefs.getString(KEY_SYNC_LAST_STATE_JSON, null)

    fun setSyncLastStateJson(json: String) {
        prefs.edit().putString(KEY_SYNC_LAST_STATE_JSON, json).apply()
    }

    fun getSyncLastAppliedUpdatedAt(): Long = prefs.getLong(KEY_SYNC_LAST_APPLIED_AT, 0L)

    fun setSyncLastAppliedUpdatedAt(updatedAt: Long) {
        prefs.edit().putLong(KEY_SYNC_LAST_APPLIED_AT, updatedAt).apply()
    }

    // Per-favorite main-page visibility, keyed by the favorite's display
    // label (stable — see FavoritesRepository.FAVORITE_LABELS). Stored in
    // this same per-device EncryptedSharedPreferences file, so each device
    // (e.g. phone vs. tablet) keeps its own on/off choices with no sync.
    fun isFavoriteVisible(label: String): Boolean = prefs.getBoolean(favoriteVisibilityKey(label), true)

    fun setFavoriteVisible(label: String, visible: Boolean) {
        prefs.edit().putBoolean(favoriteVisibilityKey(label), visible).apply()
    }

    private fun favoriteVisibilityKey(label: String) = "$KEY_FAVORITE_VISIBLE_PREFIX$label"

    /**
     * The ordered, per-device main-page favorites list, or null when nothing has been saved from
     * the picker yet. Null is distinct from an empty (but present) list: the former means "fall
     * back to the legacy [FavoritesRepository.resolveFavorites] + [isFavoriteVisible] behavior",
     * the latter means "the user picked a saved favorites list that happens to be empty".
     * Unparseable stored JSON (e.g. a corrupted write) is treated the same as absent — never crashes.
     */
    fun getMainPageFavorites(): List<MainPageFavorite>? {
        val raw = prefs.getString(KEY_MAIN_PAGE_FAVORITES, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                MainPageFavorite(
                    packageName = entry.getString("packageName"),
                    label = entry.getString("label"),
                )
            }
        }.getOrNull()
    }

    fun setMainPageFavorites(favorites: List<MainPageFavorite>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            array.put(
                JSONObject().apply {
                    put("packageName", favorite.packageName)
                    put("label", favorite.label)
                },
            )
        }
        prefs.edit().putString(KEY_MAIN_PAGE_FAVORITES, array.toString()).apply()
    }

    /**
     * The main-page favorites that should actually render, in order: the saved picker list when
     * one exists, otherwise the legacy [FavoritesRepository.resolveFavorites] output filtered by
     * [isFavoriteVisible] — i.e. exactly what the main page rendered before the picker existed.
     * This is the single source of truth both [MainPageFavoritesPickerScreen] (to seed its
     * starting state) and the main page (to render) must use, so the two never drift apart.
     */
    fun getEffectiveMainPageFavorites(allApps: List<LauncherAppInfo>): List<MainPageFavorite> {
        val stored = getMainPageFavorites()
        if (stored != null) {
            val installedPackages = allApps.mapTo(HashSet()) { it.packageName }
            return stored.filter { it.packageName in installedPackages }
        }
        return FavoritesRepository.resolveFavorites(allApps)
            .filter { isFavoriteVisible(it.label) }
            .map { MainPageFavorite(packageName = it.packageName, label = it.label) }
    }

    companion object {
        private const val PREFS_FILE_NAME = "launcher_secure_settings"
        private const val KEY_TODOIST_TOKEN = "todoist_api_token"
        private const val KEY_JELLYFIN_SERVER_URL = "jellyfin_server_url"
        private const val KEY_JELLYFIN_API_KEY = "jellyfin_api_key"
        private const val KEY_HOME_ASSISTANT_SERVER_URL = "home_assistant_server_url"
        private const val KEY_HOME_ASSISTANT_TOKEN = "home_assistant_token"
        private const val KEY_DUOLINGO_USERNAME = "duolingo_username"
        private const val KEY_SHOW_GLANCE_INFO = "show_glance_info"
        private const val KEY_FAVORITE_VISIBLE_PREFIX = "favorite_visible_"
        private const val KEY_MAIN_PAGE_FAVORITES = "main_page_favorites_v1"
        private const val KEY_SYNC_ENABLED = "theme_sync_enabled"
        private const val KEY_SYNC_THEME = "theme_sync_theme"
        private const val KEY_SYNC_MODE_CACHE = "theme_sync_mode_cache"
        private const val KEY_SYNC_MODE_CACHE_AT = "theme_sync_mode_cache_at"
        private const val KEY_SYNC_LAST_STATE_JSON = "theme_sync_last_state_json"
        private const val KEY_SYNC_LAST_APPLIED_AT = "theme_sync_last_applied_at"

        const val DEFAULT_HOME_ASSISTANT_URL = "https://home.rileyboughner.dev"
        const val DEFAULT_JELLYFIN_SERVER_URL = "https://media.rileyboughner.dev"
    }
}

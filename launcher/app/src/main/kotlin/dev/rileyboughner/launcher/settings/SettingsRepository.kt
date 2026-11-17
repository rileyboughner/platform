package dev.rileyboughner.launcher.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.rileyboughner.launcher.BuildConfig
import dev.rileyboughner.launcher.FavoritesRepository
import dev.rileyboughner.launcher.LauncherAppInfo
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

        const val DEFAULT_HOME_ASSISTANT_URL = "https://home.rileyboughner.dev"
        const val DEFAULT_JELLYFIN_SERVER_URL = "https://media.rileyboughner.dev"
    }
}

package dev.rileyboughner.tvlauncher.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The TV launcher's only storage: server URLs and API tokens in
 * [EncryptedSharedPreferences], plus the small theme-sync bookkeeping the
 * wallpaper poll needs. There is no settings screen on the TV — the Home
 * Assistant token arrives from the launcher token server on every launch
 * (see [TokenSyncApi]) and is stored here; a missing/blank fetched token
 * leaves the stored one alone, so an unreachable server never wipes a
 * working token.
 *
 * Tokens are never logged. The Home Assistant server URL has a fixed
 * default ([DEFAULT_HOME_ASSISTANT_URL]) and is not user-editable on the
 * TV; if the server ever moves, this default is the one place to change.
 */
class SettingsStore(context: Context) {

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

    fun getHomeAssistantServerUrl(): String =
        prefs.getString(KEY_HOME_ASSISTANT_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOME_ASSISTANT_URL

    fun getHomeAssistantToken(): String? =
        prefs.getString(KEY_HOME_ASSISTANT_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setHomeAssistantToken(token: String) {
        prefs.edit().putString(KEY_HOME_ASSISTANT_TOKEN, token.trim()).apply()
    }

    fun getJellyfinApiKey(): String? =
        prefs.getString(KEY_JELLYFIN_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setJellyfinApiKey(apiKey: String) {
        prefs.edit().putString(KEY_JELLYFIN_API_KEY, apiKey.trim()).apply()
    }

    // Theme/wallpaper sync bookkeeping — see ThemeSyncRepository.
    fun getSyncTheme(): String = prefs.getString(KEY_SYNC_THEME, null) ?: DEFAULT_SYNCED_THEME_KEY

    fun setSyncTheme(themeKey: String) {
        prefs.edit().putString(KEY_SYNC_THEME, themeKey).apply()
    }

    fun getSyncModeCache(): String = prefs.getString(KEY_SYNC_MODE_CACHE, null) ?: "unified"

    fun setSyncModeCache(mode: String) {
        prefs.edit().putString(KEY_SYNC_MODE_CACHE, mode).apply()
    }

    fun getSyncModeCacheFetchedAt(): Long = prefs.getLong(KEY_SYNC_MODE_CACHE_AT, 0L)

    fun setSyncModeCacheFetchedAt(epochMillis: Long) {
        prefs.edit().putLong(KEY_SYNC_MODE_CACHE_AT, epochMillis).apply()
    }

    fun getSyncLastStateJson(): String? = prefs.getString(KEY_SYNC_LAST_STATE_JSON, null)

    fun setSyncLastStateJson(json: String) {
        prefs.edit().putString(KEY_SYNC_LAST_STATE_JSON, json).apply()
    }

    fun getSyncLastAppliedUpdatedAt(): Long = prefs.getLong(KEY_SYNC_LAST_APPLIED_AT, 0L)

    fun setSyncLastAppliedUpdatedAt(updatedAt: Long) {
        prefs.edit().putLong(KEY_SYNC_LAST_APPLIED_AT, updatedAt).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "tv_launcher_secure_settings"
        private const val KEY_HOME_ASSISTANT_SERVER_URL = "home_assistant_server_url"
        private const val KEY_HOME_ASSISTANT_TOKEN = "home_assistant_token"
        private const val KEY_JELLYFIN_API_KEY = "jellyfin_api_key"
        private const val KEY_SYNC_THEME = "theme_sync_theme"
        private const val KEY_SYNC_MODE_CACHE = "theme_sync_mode_cache"
        private const val KEY_SYNC_MODE_CACHE_AT = "theme_sync_mode_cache_at"
        private const val KEY_SYNC_LAST_STATE_JSON = "theme_sync_last_state_json"
        private const val KEY_SYNC_LAST_APPLIED_AT = "theme_sync_last_applied_at"

        const val DEFAULT_HOME_ASSISTANT_URL = "https://home.rileyboughner.dev"
        const val DEFAULT_JELLYFIN_SERVER_URL = "https://media.rileyboughner.dev"
        const val DEFAULT_SYNCED_THEME_KEY = "rose-pine"
    }
}

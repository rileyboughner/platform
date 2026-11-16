package dev.rileyboughner.launcher.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores user-entered server URLs and API tokens (Todoist, Jellyfin, Home
 * Assistant) in [EncryptedSharedPreferences]. Tokens/keys are never logged —
 * see [dev.rileyboughner.launcher.todoist.TodoistApi], `JellyfinApi`, and
 * `HomeAssistantApi`.
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

    fun getTodoistToken(): String? = prefs.getString(KEY_TODOIST_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setTodoistToken(token: String) {
        prefs.edit().putString(KEY_TODOIST_TOKEN, token.trim()).apply()
    }

    fun getJellyfinServerUrl(): String? = prefs.getString(KEY_JELLYFIN_SERVER_URL, null)?.takeIf { it.isNotBlank() }

    fun setJellyfinServerUrl(url: String) {
        prefs.edit().putString(KEY_JELLYFIN_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    fun getJellyfinApiKey(): String? = prefs.getString(KEY_JELLYFIN_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setJellyfinApiKey(apiKey: String) {
        prefs.edit().putString(KEY_JELLYFIN_API_KEY, apiKey.trim()).apply()
    }

    fun getHomeAssistantServerUrl(): String =
        prefs.getString(KEY_HOME_ASSISTANT_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOME_ASSISTANT_URL

    fun setHomeAssistantServerUrl(url: String) {
        prefs.edit().putString(KEY_HOME_ASSISTANT_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    fun getHomeAssistantToken(): String? = prefs.getString(KEY_HOME_ASSISTANT_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setHomeAssistantToken(token: String) {
        prefs.edit().putString(KEY_HOME_ASSISTANT_TOKEN, token.trim()).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "launcher_secure_settings"
        private const val KEY_TODOIST_TOKEN = "todoist_api_token"
        private const val KEY_JELLYFIN_SERVER_URL = "jellyfin_server_url"
        private const val KEY_JELLYFIN_API_KEY = "jellyfin_api_key"
        private const val KEY_HOME_ASSISTANT_SERVER_URL = "home_assistant_server_url"
        private const val KEY_HOME_ASSISTANT_TOKEN = "home_assistant_token"

        const val DEFAULT_HOME_ASSISTANT_URL = "https://home.rileyboughner.dev"
    }
}

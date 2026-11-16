package dev.rileyboughner.launcher.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user-entered Todoist API token in [EncryptedSharedPreferences].
 * The token is never logged — see [dev.rileyboughner.launcher.todoist.TodoistApi].
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

    companion object {
        private const val PREFS_FILE_NAME = "launcher_secure_settings"
        private const val KEY_TODOIST_TOKEN = "todoist_api_token"
    }
}

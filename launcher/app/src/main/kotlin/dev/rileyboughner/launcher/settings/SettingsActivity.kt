package dev.rileyboughner.launcher.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.rileyboughner.launcher.theme.MonoLauncherTheme

/**
 * Minimal Settings screen, reached from the "Settings" text row on the
 * Apps page. Lets Riley paste in his own Todoist API token, Jellyfin
 * server URL + API key, and Home Assistant server URL + long-lived token —
 * see [SettingsRepository] for storage and [SettingsScreen] for the UI.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        setContent {
            MonoLauncherTheme {
                SettingsScreen(
                    initialFields = SettingsFields(
                        todoistToken = settingsRepository.getTodoistToken() ?: "",
                        jellyfinServerUrl = settingsRepository.getJellyfinServerUrl(),
                        jellyfinApiKey = settingsRepository.getJellyfinApiKey() ?: "",
                        homeAssistantServerUrl = settingsRepository.getHomeAssistantServerUrl(),
                        homeAssistantToken = settingsRepository.getHomeAssistantToken() ?: "",
                    ),
                    onSave = { fields ->
                        settingsRepository.setTodoistToken(fields.todoistToken)
                        settingsRepository.setJellyfinServerUrl(fields.jellyfinServerUrl)
                        settingsRepository.setJellyfinApiKey(fields.jellyfinApiKey)
                        settingsRepository.setHomeAssistantServerUrl(fields.homeAssistantServerUrl)
                        settingsRepository.setHomeAssistantToken(fields.homeAssistantToken)
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

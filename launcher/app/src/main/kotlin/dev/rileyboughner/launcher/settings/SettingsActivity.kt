package dev.rileyboughner.launcher.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.rileyboughner.launcher.AppListRepository
import dev.rileyboughner.launcher.theme.MonoLauncherTheme

/**
 * Settings, reached from the "Settings" text row on the Apps page. Lets
 * Riley paste in his own Todoist API token, Jellyfin server URL + API key,
 * and Home Assistant server URL + long-lived token, and pick which apps
 * show on the main page (under what name, and in what order) — see
 * [SettingsRepository] for storage, [SettingsScreen] for the main UI, and
 * [MainPageFavoritesPickerScreen] for the app picker.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appListRepository: AppListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        appListRepository = AppListRepository(applicationContext)
        val allApps = appListRepository.getLaunchableApps()

        setContent {
            MonoLauncherTheme {
                var showFavoritesPicker by remember { mutableStateOf(false) }

                if (showFavoritesPicker) {
                    MainPageFavoritesPickerScreen(
                        allApps = allApps,
                        initialFavorites = settingsRepository.getEffectiveMainPageFavorites(allApps),
                        onSave = { favorites ->
                            settingsRepository.setMainPageFavorites(favorites)
                            showFavoritesPicker = false
                        },
                        onBack = { showFavoritesPicker = false },
                    )
                } else {
                    SettingsScreen(
                        initialFields = SettingsFields(
                            todoistToken = settingsRepository.getTodoistToken() ?: "",
                            jellyfinServerUrl = settingsRepository.getJellyfinServerUrl(),
                            jellyfinApiKey = settingsRepository.getJellyfinApiKey() ?: "",
                            homeAssistantServerUrl = settingsRepository.getHomeAssistantServerUrl(),
                            homeAssistantToken = settingsRepository.getHomeAssistantToken() ?: "",
                            duolingoUsername = settingsRepository.getDuolingoUsername() ?: "",
                            showGlanceInfo = settingsRepository.getShowGlanceInfo(),
                        ),
                        onSave = { fields ->
                            settingsRepository.setTodoistToken(fields.todoistToken)
                            settingsRepository.setJellyfinServerUrl(fields.jellyfinServerUrl)
                            settingsRepository.setJellyfinApiKey(fields.jellyfinApiKey)
                            settingsRepository.setHomeAssistantServerUrl(fields.homeAssistantServerUrl)
                            settingsRepository.setHomeAssistantToken(fields.homeAssistantToken)
                            settingsRepository.setDuolingoUsername(fields.duolingoUsername)
                            settingsRepository.setShowGlanceInfo(fields.showGlanceInfo)
                            finish()
                        },
                        onBack = { finish() },
                        onOpenMainPageFavorites = { showFavoritesPicker = true },
                    )
                }
            }
        }
    }
}

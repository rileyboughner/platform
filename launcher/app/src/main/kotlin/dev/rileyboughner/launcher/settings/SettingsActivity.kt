package dev.rileyboughner.launcher.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.rileyboughner.launcher.theme.MonoLauncherTheme

/**
 * Minimal Settings screen, reached from the "Settings" text row on the
 * Apps page. Only job: let Riley paste his own Todoist API token — see
 * [SettingsRepository] for storage and [SettingsScreen] for the UI.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        setContent {
            MonoLauncherTheme {
                SettingsScreen(
                    initialToken = settingsRepository.getTodoistToken() ?: "",
                    onSave = { token ->
                        settingsRepository.setTodoistToken(token)
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

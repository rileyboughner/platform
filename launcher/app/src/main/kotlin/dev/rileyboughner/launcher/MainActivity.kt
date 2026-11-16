package dev.rileyboughner.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.settings.SettingsActivity
import dev.rileyboughner.launcher.settings.SettingsRepository
import dev.rileyboughner.launcher.theme.MonoLauncherTheme
import dev.rileyboughner.launcher.todoist.TodoistRepository
import dev.rileyboughner.launcher.ui.LauncherPagerScreen

/**
 * Phone/tablet entry point. Declares the HOME + LAUNCHER intent filters in
 * AndroidManifest.xml, so Android offers this activity when the user picks
 * a default home app in Settings → Apps → Default apps → Home app.
 *
 * Hosts the 5-page pager (Dashboard | Home Assistant | Apps | Calendar |
 * Todo) — see [LauncherPagerScreen] for page order — in the minimal
 * black-and-white [MonoLauncherTheme].
 */
class MainActivity : ComponentActivity() {

    private lateinit var appListRepository: AppListRepository
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var todoistRepository: TodoistRepository
    private lateinit var jellyfinRepository: JellyfinRepository
    private lateinit var homeAssistantRepository: HomeAssistantRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(applicationContext)
        appListRepository = AppListRepository(applicationContext)
        calendarRepository = CalendarRepository(applicationContext)
        todoistRepository = TodoistRepository(settingsRepository)
        jellyfinRepository = JellyfinRepository(settingsRepository)
        homeAssistantRepository = HomeAssistantRepository(settingsRepository)

        setContent {
            MonoLauncherTheme {
                LauncherPagerScreen(
                    appListRepository = appListRepository,
                    calendarRepository = calendarRepository,
                    todoistRepository = todoistRepository,
                    jellyfinRepository = jellyfinRepository,
                    homeAssistantRepository = homeAssistantRepository,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )
            }
        }
    }
}

package dev.rileyboughner.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.dashboard.DashboardRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.settings.SettingsActivity
import dev.rileyboughner.launcher.settings.SettingsRepository
import dev.rileyboughner.launcher.theme.MonoLauncherTheme
import dev.rileyboughner.launcher.todoist.TodoistRepository
import dev.rileyboughner.launcher.ui.TvHomeScreen

/**
 * Google TV / Android TV entry point. Declares the LEANBACK_LAUNCHER
 * intent filter in AndroidManifest.xml, so this activity is offered as a
 * candidate "home" on Leanback (Android TV / Google TV) devices.
 *
 * A separate activity from [MainActivity] so the 10-foot D-pad-navigable
 * [TvHomeScreen] and the phone's touch/gesture UI never load on the wrong
 * form factor. Shares every repository and backing screen with the phone
 * UI (same favorites resolution, same Dashboard/Home Assistant/Calendar/Todo
 * screens, same [SettingsActivity]/[SettingsRepository] key storage) — only
 * the navigation shell around them is TV-specific.
 */
class TvMainActivity : ComponentActivity() {

    private lateinit var appListRepository: AppListRepository
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var todoistRepository: TodoistRepository
    private lateinit var jellyfinRepository: JellyfinRepository
    private lateinit var homeAssistantRepository: HomeAssistantRepository
    private lateinit var dashboardRepository: DashboardRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(applicationContext)
        appListRepository = AppListRepository(applicationContext)
        calendarRepository = CalendarRepository(applicationContext)
        todoistRepository = TodoistRepository(settingsRepository)
        jellyfinRepository = JellyfinRepository(settingsRepository)
        homeAssistantRepository = HomeAssistantRepository(settingsRepository)
        dashboardRepository = DashboardRepository()

        setContent {
            val apps = remember { appListRepository.getLaunchableApps() }

            MonoLauncherTheme {
                TvHomeScreen(
                    apps = apps,
                    onAppClick = { app -> appListRepository.launchApp(app.packageName) },
                    dashboardRepository = dashboardRepository,
                    jellyfinRepository = jellyfinRepository,
                    homeAssistantRepository = homeAssistantRepository,
                    calendarRepository = calendarRepository,
                    todoistRepository = todoistRepository,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )
            }
        }
    }
}

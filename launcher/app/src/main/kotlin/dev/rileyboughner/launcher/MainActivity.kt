package dev.rileyboughner.launcher

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.dashboard.DashboardRepository
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
    private lateinit var dashboardRepository: DashboardRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Belt-and-suspenders alongside Theme.Launcher.Wallpaper's
        // android:windowShowWallpaper — some OEM skins have been known to
        // ignore the theme attribute alone, but respect the window flag.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        hideSystemBars()
        val settingsRepository = SettingsRepository(applicationContext)
        appListRepository = AppListRepository(applicationContext)
        calendarRepository = CalendarRepository(applicationContext)
        todoistRepository = TodoistRepository(settingsRepository)
        jellyfinRepository = JellyfinRepository(settingsRepository)
        homeAssistantRepository = HomeAssistantRepository(settingsRepository)
        dashboardRepository = DashboardRepository()

        setContent {
            MonoLauncherTheme {
                LauncherPagerScreen(
                    appListRepository = appListRepository,
                    calendarRepository = calendarRepository,
                    todoistRepository = todoistRepository,
                    jellyfinRepository = jellyfinRepository,
                    homeAssistantRepository = homeAssistantRepository,
                    dashboardRepository = dashboardRepository,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )
            }
        }
    }

    // Edge-to-edge immersive mode: content draws behind where the status/nav
    // bars would be (decorFitsSystemWindow = false), and the bars themselves
    // stay hidden until a swipe from the edge reveals them transiently
    // without resizing the layout underneath.
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Re-applies the hide on every focus regain — some OEM skins (and
    // returning from a system dialog/share sheet) otherwise leave the bars
    // permanently shown again instead of just transiently revealed.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}

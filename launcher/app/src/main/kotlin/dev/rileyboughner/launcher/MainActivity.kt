package dev.rileyboughner.launcher

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.dashboard.DashboardRepository
import dev.rileyboughner.launcher.duolingo.DuolingoRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.settings.SettingsActivity
import dev.rileyboughner.launcher.settings.SettingsRepository
import dev.rileyboughner.launcher.theme.MonoLauncherTheme
import dev.rileyboughner.launcher.theme.ThemeSyncPoller
import dev.rileyboughner.launcher.theme.ThemeSyncRepository
import dev.rileyboughner.launcher.nextcloud.NextcloudTasksRepository
import dev.rileyboughner.launcher.theme.accentColorFor
import dev.rileyboughner.launcher.ui.LauncherPagerScreen
import dev.rileyboughner.launcher.update.UpdatePrompt
import dev.rileyboughner.launcher.update.UpdateRepository

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
    // The Todo page reads from Nextcloud Tasks now; the Todoist repository
    // (todoist/ package) is retained as a fallback and is no longer wired
    // into the pager.
    private lateinit var nextcloudTasksRepository: NextcloudTasksRepository
    private lateinit var jellyfinRepository: JellyfinRepository
    private lateinit var homeAssistantRepository: HomeAssistantRepository
    private lateinit var dashboardRepository: DashboardRepository
    private lateinit var duolingoRepository: DuolingoRepository
    private lateinit var updateRepository: UpdateRepository
    private lateinit var themeSyncRepository: ThemeSyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Belt-and-suspenders alongside Theme.Launcher.Wallpaper's
        // android:windowShowWallpaper — some OEM skins have been known to
        // ignore the theme attribute alone, but respect the window flag.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        enableEdgeToEdge()
        val settingsRepository = SettingsRepository(applicationContext)
        appListRepository = AppListRepository(applicationContext)
        calendarRepository = CalendarRepository(applicationContext)
        nextcloudTasksRepository = NextcloudTasksRepository(settingsRepository)
        jellyfinRepository = JellyfinRepository(settingsRepository)
        homeAssistantRepository = HomeAssistantRepository(settingsRepository)
        dashboardRepository = DashboardRepository()
        duolingoRepository = DuolingoRepository(applicationContext, settingsRepository)
        updateRepository = UpdateRepository(applicationContext)
        themeSyncRepository = ThemeSyncRepository(applicationContext, settingsRepository)

        // MainActivity is reachable directly (LAUNCHER category, for
        // development) in addition to via HomeRouterActivity, which already
        // never sends a television here -- so this re-derives the same
        // check HomeRouterActivity uses rather than trusting that it's
        // always the only path in. Theme sync on a television is
        // TvMainActivity's job (pollTv + the drawn background), so this
        // activity's poller stays phone/tablet-only.
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isTelevision = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        setContent {
            var accentColor by remember { mutableStateOf(accentColorFor(settingsRepository.getSyncTheme())) }

            if (!isTelevision) {
                ThemeSyncPoller {
                    themeSyncRepository.poll()?.let { themeKey -> accentColor = accentColorFor(themeKey) }
                }
            }

            MonoLauncherTheme(accentColor = accentColor) {
                LauncherPagerScreen(
                    appListRepository = appListRepository,
                    calendarRepository = calendarRepository,
                    nextcloudTasksRepository = nextcloudTasksRepository,
                    jellyfinRepository = jellyfinRepository,
                    homeAssistantRepository = homeAssistantRepository,
                    dashboardRepository = dashboardRepository,
                    duolingoRepository = duolingoRepository,
                    settingsRepository = settingsRepository,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )

                UpdatePrompt(updateRepository)
            }
        }
    }

    // True edge-to-edge: content (and the wallpaper, via the Wallpaper theme
    // variant) draws the full window including behind the status/nav bars
    // (decorFitsSystemWindow = false), and the bars themselves stay shown
    // but transparent rather than hidden. v8 tried hiding the bars outright
    // via WindowInsetsController, but that legacy-adjacent approach (plus
    // the theme's android:windowFullscreen=true) left a solid opaque band
    // where the status bar sits instead of actually hiding or showing
    // through it — see themes.xml for the fuller explanation. Every pager
    // page already applies its own safeDrawingPadding(), so switching to
    // "shown but transparent" doesn't require any page-content changes.
    private fun enableEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    // Re-applies on every focus regain — some OEM skins (and returning from
    // a system dialog/share sheet) otherwise reset the bar colors back to
    // opaque instead of leaving them transparent.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableEdgeToEdge()
    }
}

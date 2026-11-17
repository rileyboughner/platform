package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.AppListRepository
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.dashboard.DashboardRepository
import dev.rileyboughner.launcher.duolingo.DuolingoRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.settings.SettingsRepository
import dev.rileyboughner.launcher.todoist.TodoistRepository

/**
 * Five-page horizontal pager, page order [Dashboard, Home Assistant, Apps,
 * Calendar, Todo] with Apps (index 2) the default/start page. Pages are
 * only composed once actually swiped into view (Pager's default
 * `beyondViewportPageCount` is 0), which is also what makes "request the
 * permission / fetch data when the page is first shown" work for free via
 * a plain `LaunchedEffect(Unit)` in each page.
 */
private const val PAGE_DASHBOARD = 0
private const val PAGE_HOME_ASSISTANT = 1
private const val PAGE_APPS = 2
private const val PAGE_CALENDAR = 3
private const val PAGE_TODO = 4
private const val PAGE_COUNT = 5

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherPagerScreen(
    appListRepository: AppListRepository,
    calendarRepository: CalendarRepository,
    todoistRepository: TodoistRepository,
    jellyfinRepository: JellyfinRepository,
    homeAssistantRepository: HomeAssistantRepository,
    dashboardRepository: DashboardRepository,
    duolingoRepository: DuolingoRepository,
    settingsRepository: SettingsRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = PAGE_APPS) { PAGE_COUNT }

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().weight(1f),
        ) { page ->
            when (page) {
                PAGE_DASHBOARD -> DashboardScreen(
                    dashboardRepository = dashboardRepository,
                    jellyfinRepository = jellyfinRepository,
                    modifier = Modifier.fillMaxSize(),
                )
                PAGE_HOME_ASSISTANT -> HomeAssistantScreen(homeAssistantRepository, modifier = Modifier.fillMaxSize())
                PAGE_APPS -> AppsPageScreen(
                    appListRepository = appListRepository,
                    duolingoRepository = duolingoRepository,
                    settingsRepository = settingsRepository,
                    modifier = Modifier.fillMaxSize(),
                )
                PAGE_CALENDAR -> CalendarScreen(
                    calendarRepository,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                )
                PAGE_TODO -> TodoScreen(
                    todoistRepository,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                )
            }
        }
        PageIndicator(
            pageCount = PAGE_COUNT,
            currentPage = pagerState.currentPage,
            onClick = onOpenSettings,
        )
    }
}

/**
 * Minimal dot indicator — small dots, no labels, no animation. Tapping
 * anywhere on the strip opens Settings (Riley's feedback: the dots were
 * otherwise dead space).
 *
 * The strip paints NO background: the dots float over the system
 * wallpaper showing through the window (the activity is edge-to-edge
 * with FLAG_SHOW_WALLPAPER). An earlier version painted
 * `colorScheme.background` here, which read as a solid black bar under
 * the dots on the wallpaper page — Riley asked for it gone (v14).
 * windowInsetsPadding is applied *before* clickable here so the reserved
 * nav-bar inset space stays out of the tap area — the dots themselves
 * still sit in the padded area clear of the bar.
 */
@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            // Accent/highlight only (synced-theme color, default white) --
            // see MonoLauncherTheme. The inactive dots stay the fixed
            // hairline gray regardless of the synced theme.
            val color = if (index == currentPage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

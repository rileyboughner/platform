package dev.rileyboughner.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rileyboughner.launcher.FavoritesRepository
import dev.rileyboughner.launcher.LauncherAppInfo
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.dashboard.DashboardRepository
import dev.rileyboughner.launcher.duolingo.DuolingoRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.todoist.TodoistRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Google TV / Leanback home screen. Same minimal black-and-white text style
 * as the phone launcher (via [dev.rileyboughner.launcher.theme.MonoLauncherTheme],
 * applied by the caller) — solid black background, large white text rows,
 * no icons, no grids — but a single D-pad-navigable list instead of the
 * phone's swipeable pager, since there's no touchscreen here. Up/down moves
 * focus (see [TvTextRow]), center/OK opens the focused row, back returns to
 * this home list from any sub-screen.
 *
 * The Dashboard, Home Assistant, Calendar, and Todo rows reuse the phone's
 * screens unmodified (see [TvSubScreen]) — only their framing (a back row
 * above, initial D-pad focus) is TV-specific. "All apps" is TV-only code
 * ([TvAllAppsScreen]): the phone's [AllAppsScreen] is shared with the phone's
 * swipe-down gesture and sized for touch, so reusing it here would mean
 * either shrinking TV text back down or changing phone text size — this
 * avoids that tradeoff entirely.
 */
private enum class TvDestination { HOME, ALL_APPS, DASHBOARD, HOME_ASSISTANT, CALENDAR, TODO }

@Composable
fun TvHomeScreen(
    apps: List<LauncherAppInfo>,
    onAppClick: (LauncherAppInfo) -> Unit,
    dashboardRepository: DashboardRepository,
    jellyfinRepository: JellyfinRepository,
    homeAssistantRepository: HomeAssistantRepository,
    calendarRepository: CalendarRepository,
    todoistRepository: TodoistRepository,
    duolingoRepository: DuolingoRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(TvDestination.HOME) }

    when (destination) {
        TvDestination.HOME -> TvHomeContent(
            apps = apps,
            onAppClick = onAppClick,
            onOpenAllApps = { destination = TvDestination.ALL_APPS },
            onOpenDashboard = { destination = TvDestination.DASHBOARD },
            onOpenHomeAssistant = { destination = TvDestination.HOME_ASSISTANT },
            onOpenCalendar = { destination = TvDestination.CALENDAR },
            onOpenTodo = { destination = TvDestination.TODO },
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )

        TvDestination.ALL_APPS -> TvAllAppsScreen(
            apps = apps,
            onAppClick = onAppClick,
            onBack = { destination = TvDestination.HOME },
            modifier = modifier,
        )

        TvDestination.DASHBOARD -> TvSubScreen(onBack = { destination = TvDestination.HOME }, modifier = modifier) {
            DashboardScreen(
                dashboardRepository = dashboardRepository,
                jellyfinRepository = jellyfinRepository,
                modifier = Modifier.fillMaxSize(),
            )
        }

        TvDestination.HOME_ASSISTANT -> TvSubScreen(onBack = { destination = TvDestination.HOME }, modifier = modifier) {
            HomeAssistantScreen(homeAssistantRepository, modifier = Modifier.fillMaxSize())
        }

        TvDestination.CALENDAR -> TvSubScreen(onBack = { destination = TvDestination.HOME }, modifier = modifier) {
            CalendarScreen(
                calendarRepository,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            )
        }

        TvDestination.TODO -> TvSubScreen(onBack = { destination = TvDestination.HOME }, modifier = modifier) {
            TodoScreen(
                todoistRepository,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            )
        }
    }
}

@Composable
private fun TvHomeContent(
    apps: List<LauncherAppInfo>,
    onAppClick: (LauncherAppInfo) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenHomeAssistant: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenTodo: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites = remember(apps) { FavoritesRepository.resolveFavorites(apps) }
    val firstFocusRequester = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp),
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            TvClockHeader()
            Spacer(modifier = Modifier.height(48.dp))

            favorites.forEachIndexed { index, app ->
                TvTextRow(
                    label = app.label,
                    onClick = { onAppClick(app) },
                    focusRequester = if (index == 0) firstFocusRequester else null,
                )
            }

            if (favorites.isNotEmpty()) Spacer(modifier = Modifier.height(24.dp))

            TvTextRow(
                label = "All apps",
                onClick = onOpenAllApps,
                focusRequester = if (favorites.isEmpty()) firstFocusRequester else null,
            )
            TvTextRow(label = "Dashboard", onClick = onOpenDashboard)
            TvTextRow(label = "Home Assistant", onClick = onOpenHomeAssistant)
            TvTextRow(label = "Calendar", onClick = onOpenCalendar)
            TvTextRow(label = "Todo", onClick = onOpenTodo)

            Spacer(modifier = Modifier.height(24.dp))
            HairlineDivider()
            TvTextRow(label = "Settings", onClick = onOpenSettings)

            Spacer(modifier = Modifier.height(56.dp))
        }
    }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }
}

@Composable
private fun TvClockHeader(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }

    Column(modifier = modifier) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
            color = Color.White,
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp),
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/**
 * TV-only full app list — large [TvTextRow]s in a plain D-pad-navigable
 * [LazyColumn] (focusing an off-screen row auto-scrolls it into view, same
 * as any focusable item inside a scrollable container). No text filter: a
 * D-pad has no practical way to type into one, so unlike the phone's
 * [AllAppsScreen] this just lists every app.
 */
@Composable
private fun TvAllAppsScreen(
    apps: List<LauncherAppInfo>,
    onAppClick: (LauncherAppInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(horizontal = 56.dp),
    ) {
        TvTextRow(label = "← TV Home", onClick = onBack, focusRequester = backFocusRequester)
        HairlineDivider()

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                TvTextRow(label = app.label, onClick = { onAppClick(app) })
            }
        }
    }

    LaunchedEffect(Unit) {
        backFocusRequester.requestFocus()
    }
}

/**
 * Hosts a reused phone screen (Dashboard/Home Assistant/Calendar/Todo) under
 * a TV back row. Those screens are composed completely unmodified — this
 * only adds what they're missing for a TV sub-screen: a D-pad target to
 * return to [TvDestination.HOME], initial focus on it so up/down has
 * somewhere to start, and the back button doing the same thing. Every row
 * inside the reused screen (built from [AppTextRow]/`clickable`) already
 * reports focus and responds to DPAD_CENTER/Enter the same way [TvTextRow]
 * does, so no changes were needed inside those screens themselves.
 */
@Composable
private fun TvSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        TvTextRow(
            label = "← TV Home",
            onClick = onBack,
            focusRequester = backFocusRequester,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        HairlineDivider()
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            content()
        }
    }

    LaunchedEffect(Unit) {
        backFocusRequester.requestFocus()
    }
}

package dev.rileyboughner.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rileyboughner.launcher.FavoritesRepository
import dev.rileyboughner.launcher.LauncherAppInfo
import dev.rileyboughner.launcher.R
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.dashboard.DashboardRepository
import dev.rileyboughner.launcher.duolingo.DuolingoRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.resolveLeanbackTile
import dev.rileyboughner.launcher.todoist.TodoistRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Google TV / Leanback home screen. Apple TV-style: large poster/banner
 * tiles for Jellyfin, YouTube, Weave music, and the movie wheel (see
 * [TvTileRow]) that scale up and gain a focus ring on D-pad focus (see
 * [TvTile]), on a dark background, with the big clock/date header above
 * them. A second, smaller row below keeps Dashboard/Home Assistant/
 * Calendar/Todo reachable — those are first-party launcher features with no
 * real "brand artwork" of their own, so they stay compact labeled tiles
 * rather than full posters. "All apps" and Settings stay as plain text rows
 * beneath both tile rows, same as before the redesign.
 *
 * Weave has no installed Android app, so it opens as an in-launcher
 * [TvWebViewScreen] instead of a launch intent — see that file for why a
 * WebView is used here over handing off to a browser. The movie wheel is a
 * TV-native screen instead ([TvMovieWheelScreen]) — see that file for why a
 * WebView wasn't reused there.
 *
 * The Dashboard, Calendar, and Todo screens reuse the phone's screens
 * unmodified (see [TvSubScreen]) — only their framing (a back row above,
 * initial D-pad focus) is TV-specific. Home Assistant and "All apps" are
 * TV-only code ([TvHomeAssistantScreen], [TvAllAppsScreen]).
 */
private enum class TvDestination { HOME, ALL_APPS, DASHBOARD, HOME_ASSISTANT, CALENDAR, TODO, WEB_VIEW, MOVIE_WHEEL }

private const val WEAVE_URL = "https://weave.rileyboughner.dev"

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
    var webViewTitle by rememberSaveable { mutableStateOf("") }
    var webViewUrl by rememberSaveable { mutableStateOf("") }

    when (destination) {
        TvDestination.HOME -> TvHomeContent(
            apps = apps,
            onAppClick = onAppClick,
            onOpenAllApps = { destination = TvDestination.ALL_APPS },
            onOpenDashboard = { destination = TvDestination.DASHBOARD },
            onOpenHomeAssistant = { destination = TvDestination.HOME_ASSISTANT },
            onOpenCalendar = { destination = TvDestination.CALENDAR },
            onOpenTodo = { destination = TvDestination.TODO },
            onOpenWebTarget = { title, url ->
                webViewTitle = title
                webViewUrl = url
                destination = TvDestination.WEB_VIEW
            },
            onOpenMovieWheel = { destination = TvDestination.MOVIE_WHEEL },
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

        TvDestination.HOME_ASSISTANT -> TvHomeAssistantScreen(
            homeAssistantRepository = homeAssistantRepository,
            onBack = { destination = TvDestination.HOME },
            modifier = modifier,
        )

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

        TvDestination.WEB_VIEW -> TvWebViewScreen(
            title = webViewTitle,
            url = webViewUrl,
            onBack = { destination = TvDestination.HOME },
            modifier = modifier,
        )

        TvDestination.MOVIE_WHEEL -> TvMovieWheelScreen(
            onBack = { destination = TvDestination.HOME },
            modifier = modifier,
        )
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
    onOpenWebTarget: (title: String, url: String) -> Unit,
    onOpenMovieWheel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val favorites = remember(apps) { FavoritesRepository.resolveFavorites(apps) }
    val jellyfinTile = remember { resolveLeanbackTile(context, "org.jellyfin.androidtv", "Jellyfin") }
    val youtubeTile = remember { resolveLeanbackTile(context, "com.google.android.youtube.tv", "YouTube") }
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                if (jellyfinTile != null) {
                    TvTile(
                        label = jellyfinTile.label,
                        onClick = { context.startActivity(jellyfinTile.launchIntent) },
                        focusRequester = firstFocusRequester,
                    ) {
                        TvTileDrawableArtwork(jellyfinTile.artwork, contentDescription = jellyfinTile.label)
                    }
                }
                if (youtubeTile != null) {
                    TvTile(
                        label = youtubeTile.label,
                        onClick = { context.startActivity(youtubeTile.launchIntent) },
                        focusRequester = if (jellyfinTile == null) firstFocusRequester else null,
                    ) {
                        TvTileDrawableArtwork(youtubeTile.artwork, contentDescription = youtubeTile.label)
                    }
                }
                TvTile(
                    label = "Weave",
                    onClick = { onOpenWebTarget("Weave", WEAVE_URL) },
                    focusRequester = if (jellyfinTile == null && youtubeTile == null) firstFocusRequester else null,
                ) {
                    TvTileResourceArtwork(painterResource(R.drawable.tv_tile_weave), contentDescription = "Weave")
                }
                TvTile(
                    label = "Movie wheel",
                    onClick = onOpenMovieWheel,
                ) {
                    TvTileResourceArtwork(painterResource(R.drawable.tv_tile_movie_wheel), contentDescription = "Movie wheel")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TvTile(label = "Dashboard", onClick = onOpenDashboard, width = 170.dp, height = 96.dp, cornerRadius = 12.dp) {
                    TvGlyphArtwork("▦")
                }
                TvTile(label = "Home Assistant", onClick = onOpenHomeAssistant, width = 170.dp, height = 96.dp, cornerRadius = 12.dp) {
                    TvGlyphArtwork("⌂")
                }
                TvTile(label = "Calendar", onClick = onOpenCalendar, width = 170.dp, height = 96.dp, cornerRadius = 12.dp) {
                    TvGlyphArtwork("▤")
                }
                TvTile(label = "Todo", onClick = onOpenTodo, width = 170.dp, height = 96.dp, cornerRadius = 12.dp) {
                    TvGlyphArtwork("✓")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            HairlineDivider()

            favorites.forEach { app ->
                TvTextRow(label = app.label, onClick = { onAppClick(app) })
            }
            if (favorites.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))

            TvTextRow(label = "All apps", onClick = onOpenAllApps)
            TvTextRow(label = "Settings", onClick = onOpenSettings)

            Spacer(modifier = Modifier.height(56.dp))
        }
    }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }
}

@Composable
private fun TvGlyphArtwork(glyph: String, modifier: Modifier = Modifier) {
    Text(text = glyph, style = MaterialTheme.typography.displaySmall, color = Color.White.copy(alpha = 0.85f), modifier = modifier)
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

package dev.rileyboughner.launcher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rileyboughner.launcher.R
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantResult
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRoom
import dev.rileyboughner.launcher.homeassistant.HomeAssistantSnapshot
import dev.rileyboughner.launcher.resolveLeanbackTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val WHEEL_URL = "https://wheel.rileyboughner.dev"
private const val SCENE_FEEDBACK_MS = 1500L
private const val OVERSCAN_SAFE_FRACTION = 0.05f

/**
 * Google TV / Leanback home screen — a single page, top to bottom: clock +
 * date header, a 3-tile apps row (Jellyfin, YouTube, the movie-wheel web
 * app), then the living-room Home Assistant lights row and scenes row. No
 * other TV destinations exist anymore: the old separate Home Assistant
 * screen ([TvHomeAssistantScreen]) and native movie-wheel screen
 * ([TvMovieWheelScreen]) are both unreachable from here now — this page
 * reuses [LightTile]/[SceneTile]/[SectionLabel] straight out of
 * [TvHomeAssistantScreen.kt] instead of navigating to that screen, and the
 * movie-wheel tile opens the real web app via [Intent.ACTION_VIEW] instead
 * of navigating to the native wheel screen.
 *
 * Every edge of the page keeps at least [OVERSCAN_SAFE_FRACTION] (5%) of
 * [BoxWithConstraints]'s measured width/height as margin, since this
 * firmware renders at a fixed 1080p and overscans — a flat dp padding
 * wouldn't scale with the actual panel size the way a measured fraction does.
 *
 * [wallpaperBitmap] is the synced wallpaper from the theme-sync hub
 * ([TvMainActivity] polls it): drawn full-bleed behind the tiles under a
 * dark scrim so white text stays legible. Null (hub unreachable, or no
 * wallpaper synced yet) keeps the plain black background.
 */
@Composable
fun TvHomeScreen(
    homeAssistantRepository: HomeAssistantRepository,
    wallpaperBitmap: Bitmap? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var haResult by remember { mutableStateOf<HomeAssistantResult<HomeAssistantSnapshot>?>(null) }
    var activatedSceneId by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun refreshHomeAssistant() {
        haResult = homeAssistantRepository.fetchSnapshot()
    }

    LaunchedEffect(Unit) { refreshHomeAssistant() }

    LaunchedEffect(activatedSceneId) {
        if (activatedSceneId != null) {
            delay(SCENE_FEEDBACK_MS)
            activatedSceneId = null
        }
    }

    val jellyfinTile = remember { resolveLeanbackTile(context, "org.jellyfin.androidtv", "Jellyfin") }
    val youtubeTile = remember { resolveLeanbackTile(context, "com.google.android.youtube.tv", "YouTube") }
    val steamTile = remember { resolveLeanbackTile(context, "com.valvesoftware.steamlink", "Steam Link") }
    val firstFocusRequester = remember { FocusRequester() }
    val wallpaperImage = remember(wallpaperBitmap) { wallpaperBitmap?.asImageBitmap() }
    // Hoisted (not an inline rememberScrollState) so focusing any Apps-row
    // tile can scroll the page all the way back to the top: the default
    // bring-into-view only scrolls the minimum to reveal the tile, which
    // left the clock scrolled off-screen (v15 TV feedback).
    val verticalScrollState = rememberScrollState()
    val scrollToTop: () -> Unit = { scope.launch { verticalScrollState.animateScrollTo(0) } }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (wallpaperImage != null) {
            Image(
                bitmap = wallpaperImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Dark scrim over the photo so white text and tiles stay
            // legible no matter how bright the wallpaper is.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }

        val horizontalMargin = maxWidth * OVERSCAN_SAFE_FRACTION
        val verticalMargin = maxHeight * OVERSCAN_SAFE_FRACTION

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(verticalScrollState)
                .padding(horizontal = horizontalMargin, vertical = verticalMargin),
        ) {
            TvClockHeader()
            Spacer(modifier = Modifier.height(48.dp))

            SectionLabel("Apps")
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                if (jellyfinTile != null) {
                    TvTile(
                        label = jellyfinTile.label,
                        onClick = { context.startActivity(jellyfinTile.launchIntent) },
                        focusRequester = firstFocusRequester,
                        onFocus = scrollToTop,
                    ) {
                        TvTileDrawableArtwork(jellyfinTile.artwork, contentDescription = jellyfinTile.label)
                    }
                }
                if (youtubeTile != null) {
                    TvTile(
                        label = youtubeTile.label,
                        onClick = { context.startActivity(youtubeTile.launchIntent) },
                        focusRequester = if (jellyfinTile == null) firstFocusRequester else null,
                        onFocus = scrollToTop,
                    ) {
                        TvTileDrawableArtwork(youtubeTile.artwork, contentDescription = youtubeTile.label)
                    }
                }
                if (steamTile != null) {
                    TvTile(
                        label = steamTile.label,
                        onClick = { context.startActivity(steamTile.launchIntent) },
                        focusRequester = if (jellyfinTile == null && youtubeTile == null) firstFocusRequester else null,
                        onFocus = scrollToTop,
                    ) {
                        TvTileDrawableArtwork(steamTile.artwork, contentDescription = steamTile.label)
                    }
                }
                TvTile(
                    label = "Movie wheel",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WHEEL_URL)))
                    },
                    focusRequester = if (jellyfinTile == null && youtubeTile == null && steamTile == null) firstFocusRequester else null,
                    onFocus = scrollToTop,
                ) {
                    TvTileResourceArtwork(painterResource(R.drawable.tv_tile_movie_wheel), contentDescription = "Movie wheel")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            val livingRoom = livingRoomFrom(haResult)
            val haStatusMessage = haStatusMessage(haResult, livingRoom)

            SectionLabel("Lights")
            if (haStatusMessage != null) {
                TvHaMessage(haStatusMessage)
            } else {
                val room = livingRoom!!
                if (room.lights.isEmpty()) {
                    TvHaMessage("No lights found.")
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        room.lights.forEach { light ->
                            LightTile(
                                light = light,
                                onToggle = {
                                    scope.launch {
                                        homeAssistantRepository.toggleLight(light.entityId)
                                        refreshHomeAssistant()
                                    }
                                },
                                onBrightnessStep = { delta ->
                                    val current = light.brightnessPercent
                                    if (current != null) {
                                        scope.launch {
                                            val next = (current + delta).coerceIn(1, 100)
                                            homeAssistantRepository.setLightBrightness(light.entityId, next)
                                            refreshHomeAssistant()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            SectionLabel("Scenes")
            if (haStatusMessage != null) {
                TvHaMessage(haStatusMessage)
            } else {
                val room = livingRoom!!
                if (room.scenes.isEmpty()) {
                    TvHaMessage("No scenes found.")
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        room.scenes.forEach { scene ->
                            SceneTile(
                                scene = scene,
                                isActivated = activatedSceneId == scene.entityId,
                                onActivate = {
                                    activatedSceneId = scene.entityId
                                    scope.launch { homeAssistantRepository.activateScene(scene.entityId) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }
}

/** [HomeAssistantEntities.ROOMS]'s first entry is the Living Room — see that file's kdoc. */
private fun livingRoomFrom(result: HomeAssistantResult<HomeAssistantSnapshot>?): HomeAssistantRoom? =
    (result as? HomeAssistantResult.Success)?.value?.rooms?.firstOrNull { it.name == LIVING_ROOM_NAME }

private fun haStatusMessage(result: HomeAssistantResult<HomeAssistantSnapshot>?, livingRoom: HomeAssistantRoom?): String? =
    when {
        result == null -> "Loading…"
        result is HomeAssistantResult.NotConfigured -> "Home Assistant not configured. Add server + token in Settings."
        result is HomeAssistantResult.Error -> result.message
        livingRoom == null -> "Living Room not found."
        else -> null
    }

@Composable
private fun TvHaMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.7f),
        modifier = modifier.fillMaxWidth(),
    )
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

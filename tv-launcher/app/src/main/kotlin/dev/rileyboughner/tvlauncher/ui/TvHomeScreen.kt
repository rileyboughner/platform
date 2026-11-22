package dev.rileyboughner.tvlauncher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.rileyboughner.tvlauncher.R
import dev.rileyboughner.tvlauncher.homeassistant.HomeAssistantLight
import dev.rileyboughner.tvlauncher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.tvlauncher.homeassistant.HomeAssistantResult
import dev.rileyboughner.tvlauncher.homeassistant.HomeAssistantRoom
import dev.rileyboughner.tvlauncher.homeassistant.HomeAssistantScene
import dev.rileyboughner.tvlauncher.homeassistant.HomeAssistantSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val WEAVE_URL = "https://weave.rileyboughner.dev"
private const val SCENE_FEEDBACK_MS = 1500L
private const val BRIGHTNESS_STEP_PERCENT = 10

/**
 * The TV launcher's only screen — a single page, top to bottom: a large
 * clock + date, one row of four poster apps (Jellyfin, YouTube, Weave
 * Music, Moonlight), then the living-room Home Assistant lights and scenes rows.
 *
 * Apple TV-like: full-bleed synced wallpaper under a dark gradient scrim,
 * large rounded poster tiles, focused tile scales up with a bright ring
 * and a soft shadow, unfocused tiles dimmed, generous whitespace, no
 * clutter. D-pad navigable throughout.
 *
 * Layout lessons carried over from the old TV screen (its bugs are not
 * repeated here): side margins are a fixed 28.dp (the Leanback
 * overscan-safe margin), and every horizontally scrollable Row carries a
 * 20.dp leading AND trailing Spacer *inside* the Row, so the first/last
 * tile can scale up on focus without being clipped by the Row's edge.
 *
 * [wallpaperBitmap] is the synced wallpaper from the theme-sync hub
 * (MainActivity polls it): drawn full-bleed behind the tiles under a dark
 * gradient scrim so white text stays legible. Null (hub unreachable, or
 * no wallpaper synced yet) keeps the plain black background.
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
    val moonlightTile = remember { resolveLeanbackTile(context, "com.limelight", "Moonlight") }
    val firstFocusRequester = remember { FocusRequester() }
    val wallpaperImage = remember(wallpaperBitmap) { wallpaperBitmap?.asImageBitmap() }
    // Hoisted (not an inline rememberScrollState) so focusing any Apps-row
    // tile can scroll the page all the way back to the top: the default
    // bring-into-view only scrolls the minimum to reveal the tile, which
    // can leave the clock scrolled off-screen.
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
            // Dark gradient scrim over the photo so white text and tiles
            // stay legible no matter how bright the wallpaper is: darker
            // at the top (behind the clock) and at the bottom (behind the
            // lower rows), lighter in the middle where the app posters sit.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(verticalScrollState)
                .padding(horizontal = 28.dp, vertical = 28.dp),
        ) {
            TvClockHeader()
            Spacer(modifier = Modifier.height(44.dp))

            SectionLabel("Apps")
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                // Leading/trailing spacers INSIDE the row: room for the
                // first/last tile to scale up on focus without hitting
                // the Row's clip edge.
                Spacer(modifier = Modifier.width(20.dp))
                AppTile(
                    label = "Jellyfin",
                    enabled = jellyfinTile != null,
                    onClick = jellyfinTile?.let { tile -> { context.startActivity(tile.launchIntent) } },
                    focusRequester = firstFocusRequester,
                    onFocus = scrollToTop,
                ) {
                    if (jellyfinTile != null) {
                        TileBannerArtwork(jellyfinTile.artwork, contentDescription = jellyfinTile.label)
                    } else {
                        TileTextArtwork("Jellyfin")
                    }
                }
                AppTile(
                    label = "YouTube",
                    enabled = youtubeTile != null,
                    onClick = youtubeTile?.let { tile -> { context.startActivity(tile.launchIntent) } },
                    focusRequester = if (jellyfinTile == null) firstFocusRequester else null,
                    onFocus = scrollToTop,
                ) {
                    if (youtubeTile != null) {
                        TileBannerArtwork(youtubeTile.artwork, contentDescription = youtubeTile.label)
                    } else {
                        TileTextArtwork("YouTube")
                    }
                }
                AppTile(
                    label = "Weave Music",
                    enabled = true,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEAVE_URL)))
                    },
                    focusRequester = if (jellyfinTile == null && youtubeTile == null) firstFocusRequester else null,
                    onFocus = scrollToTop,
                ) {
                    TileVectorArtwork(
                        painterResource(R.drawable.tile_weave),
                        contentDescription = "Weave Music",
                    )
                }
                AppTile(
                    label = "Moonlight",
                    enabled = moonlightTile != null,
                    onClick = moonlightTile?.let { tile -> { context.startActivity(tile.launchIntent) } },
                    onFocus = scrollToTop,
                ) {
                    if (moonlightTile != null) {
                        TileBannerArtwork(moonlightTile.artwork, contentDescription = moonlightTile.label)
                    } else {
                        TileTextArtwork("Moonlight")
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
            }

            Spacer(modifier = Modifier.height(36.dp))

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
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.width(20.dp))
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
                        Spacer(modifier = Modifier.width(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            SectionLabel("Scenes")
            if (haStatusMessage != null) {
                TvHaMessage(haStatusMessage)
            } else {
                val room = livingRoom!!
                if (room.scenes.isEmpty()) {
                    TvHaMessage("No scenes found.")
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.width(20.dp))
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
                        Spacer(modifier = Modifier.width(20.dp))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }
}

/** HomeAssistantEntities.ROOMS' only entry is the Living Room. */
private fun livingRoomFrom(result: HomeAssistantResult<HomeAssistantSnapshot>?): HomeAssistantRoom? =
    (result as? HomeAssistantResult.Success)?.value?.rooms?.firstOrNull()

private fun haStatusMessage(result: HomeAssistantResult<HomeAssistantSnapshot>?, livingRoom: HomeAssistantRoom?): String? =
    when {
        result == null -> "Loading…"
        result is HomeAssistantResult.NotConfigured -> "Home Assistant not configured."
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
private fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = modifier.padding(bottom = 14.dp),
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
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
            fontWeight = FontWeight.Light,
            color = Color.White,
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/**
 * Apple TV-style poster tile: large rounded artwork that scales up and
 * gains a bright focus ring and a soft shadow on D-pad focus, dimmed when
 * unfocused. The label sits below. A tile whose app isn't installed
 * ([enabled] false) renders dimmed and is not focusable or clickable.
 */
@Composable
private fun AppTile(
    label: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    width: Dp = 300.dp,
    height: Dp = 170.dp,
    cornerRadius: Dp = 20.dp,
    onFocus: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.1f else 1f, label = "appTileScale")
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(if (enabled) 1f else 0.35f),
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .scale(scale)
                .shadow(if (isFocused) 16.dp else 0.dp, shape)
                .let { if (focusRequester != null && enabled) it.focusRequester(focusRequester) else it }
                .clip(shape)
                .background(Color(0xFF141414))
                .border(width = if (isFocused) 3.dp else 0.dp, color = Color.White, shape = shape)
                .alpha(if (isFocused) 1f else 0.65f)
                .onFocusChanged { state ->
                    if (state.isFocused) onFocus?.invoke()
                }
                .let {
                    if (enabled && onClick != null) {
                        it.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    } else {
                        it
                    }
                },
            contentAlignment = Alignment.Center,
            content = { content() },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
        )
    }
}

/** [AppTile] content: an installed Leanback app's banner artwork, cropped to fill the tile. */
@Composable
private fun TileBannerArtwork(drawable: Drawable, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) {
        drawable.toBitmap(
            width = drawable.intrinsicWidth.coerceAtLeast(1),
            height = drawable.intrinsicHeight.coerceAtLeast(1),
        ).asImageBitmap()
    }
    // fillMaxSize is load-bearing: without it the Image sizes to the
    // bitmap's intrinsic pixels instead of filling the tile.
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

/** [AppTile] content: a bundled vector drawable (the Weave tile art), scaled to fit. */
@Composable
private fun TileVectorArtwork(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize().padding(24.dp),
    )
}

/** [AppTile] content fallback: the app name in large text, for a tile whose app isn't installed. */
@Composable
private fun TileTextArtwork(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.8f),
        modifier = modifier,
    )
}

/**
 * Living-room light tile: name, on/off state, and a thin brightness bar.
 * D-pad center toggles. While focused, Channel Up / Channel Down steps
 * brightness 10% (the remote's channel rocker is otherwise unused on this
 * screen). Unavailable lights render dimmed and ignore input.
 */
@Composable
private fun LightTile(
    light: HomeAssistantLight,
    onToggle: () -> Unit,
    onBrightnessStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val canAdjustBrightness = light.isAvailable && light.isOn && light.brightnessPercent != null
    val shape = remember { RoundedCornerShape(16.dp) }

    val stateText = when {
        !light.isAvailable -> "Unavailable"
        !light.isOn -> "Off"
        light.brightnessPercent != null -> "On · ${light.brightnessPercent}%"
        else -> "On"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(if (light.isAvailable) 1f else 0.4f),
    ) {
        Box(
            modifier = Modifier
                .size(width = 230.dp, height = 120.dp)
                .shadow(if (isFocused) 12.dp else 0.dp, shape)
                .clip(shape)
                .background(if (light.isOn) Color(0xFF2A2A2A) else Color(0xFF141414))
                .border(width = if (isFocused) 3.dp else 0.dp, color = Color.White, shape = shape)
                .alpha(if (isFocused) 1f else 0.75f)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp || !canAdjustBrightness) return@onKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_UP -> { onBrightnessStep(BRIGHTNESS_STEP_PERCENT); true }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> { onBrightnessStep(-BRIGHTNESS_STEP_PERCENT); true }
                        else -> false
                    }
                }
                .let {
                    if (light.isAvailable) {
                        it.clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                    } else {
                        it
                    }
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (light.isOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = if (light.isOn) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = light.friendlyName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                if (light.brightnessPercent != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (light.brightnessPercent / 100f).coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scene tile: a compact pill with the scene name. Tap activates it; the
 * tile shows a bright ring and bold text for a moment as feedback.
 */
@Composable
private fun SceneTile(
    scene: HomeAssistantScene,
    isActivated: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = remember { RoundedCornerShape(16.dp) }

    Box(
        modifier = modifier
            .size(width = 210.dp, height = 64.dp)
            .shadow(if (isFocused || isActivated) 10.dp else 0.dp, shape)
            .clip(shape)
            .background(Color(0xFF1C1C1C))
            .border(
                width = if (isFocused || isActivated) 3.dp else 0.dp,
                color = Color.White,
                shape = shape,
            )
            .alpha(if (isFocused || isActivated) 1f else 0.75f)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onActivate),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = scene.friendlyName,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = if (isFocused || isActivated) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
        )
    }
}

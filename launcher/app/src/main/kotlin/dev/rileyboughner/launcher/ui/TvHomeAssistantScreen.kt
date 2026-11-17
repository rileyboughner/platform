package dev.rileyboughner.launcher.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.homeassistant.HomeAssistantLight
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantResult
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRoom
import dev.rileyboughner.launcher.homeassistant.HomeAssistantScene
import dev.rileyboughner.launcher.homeassistant.HomeAssistantSnapshot
import dev.rileyboughner.launcher.homeassistant.Presence
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val LIVING_ROOM_NAME = "Living Room"
private const val BRIGHTNESS_STEP_PERCENT = 10
private const val SCENE_FEEDBACK_MS = 1500L

/**
 * TV-native Home Assistant screen. No longer reachable as its own TV
 * destination — [dev.rileyboughner.launcher.ui.TvHomeScreen] folds the
 * living-room lights/scenes rows directly into the TV main page, reusing
 * [LightTile]/[SceneTile]/[SectionLabel] from this file (made `internal`
 * for that reuse) rather than this whole screen. This composable and its
 * living-room-only, D-pad-first design (see
 * [dev.rileyboughner.launcher.homeassistant.HomeAssistantEntities.ROOMS] for
 * the single source of truth on which lights/scenes that room has) are kept
 * around since [LightTile]/[SceneTile] are still used from here.
 */
@Composable
fun TvHomeAssistantScreen(
    homeAssistantRepository: HomeAssistantRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var result by remember { mutableStateOf<HomeAssistantResult<HomeAssistantSnapshot>?>(null) }
    var activatedSceneId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val backFocusRequester = remember { FocusRequester() }
    val currentOnBack = rememberUpdatedState(onBack)
    BackHandler(onBack = { currentOnBack.value() })

    suspend fun refresh() {
        result = homeAssistantRepository.fetchSnapshot()
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(activatedSceneId) {
        if (activatedSceneId != null) {
            delay(SCENE_FEEDBACK_MS)
            activatedSceneId = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        TvTextRow(
            label = "← TV Home",
            onClick = { currentOnBack.value() },
            focusRequester = backFocusRequester,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        HairlineDivider()

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (val r = result) {
                null -> CenteredHaMessage("Loading…")
                is HomeAssistantResult.NotConfigured ->
                    CenteredHaMessage("Home Assistant not configured. Add server + token in Settings.")
                is HomeAssistantResult.Error -> CenteredHaMessage(r.message)
                is HomeAssistantResult.Success -> {
                    val livingRoom = r.value.rooms.firstOrNull { it.name == LIVING_ROOM_NAME }
                    if (livingRoom == null) {
                        CenteredHaMessage("Living Room not found.")
                    } else {
                        LivingRoomContent(
                            room = livingRoom,
                            presence = r.value.presence,
                            activatedSceneId = activatedSceneId,
                            onToggleLight = { entityId ->
                                scope.launch {
                                    homeAssistantRepository.toggleLight(entityId)
                                    refresh()
                                }
                            },
                            onBrightnessStep = { entityId, currentPercent, delta ->
                                scope.launch {
                                    val next = (currentPercent + delta).coerceIn(1, 100)
                                    homeAssistantRepository.setLightBrightness(entityId, next)
                                    refresh()
                                }
                            },
                            onActivateScene = { entityId ->
                                activatedSceneId = entityId
                                scope.launch { homeAssistantRepository.activateScene(entityId) }
                            },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { backFocusRequester.requestFocus() }
}

@Composable
private fun LivingRoomContent(
    room: HomeAssistantRoom,
    presence: Presence,
    activatedSceneId: String?,
    onToggleLight: (String) -> Unit,
    onBrightnessStep: (entityId: String, currentPercent: Int, delta: Int) -> Unit,
    onActivateScene: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 24.dp),
    ) {
        Text(
            text = room.name,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Text(
            text = "Presence: ${presenceLabel(presence)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (room.lights.isEmpty()) {
            SectionLabel("Lights")
            CenteredHaMessage("No lights found.")
        } else {
            SectionLabel("Lights")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                room.lights.forEach { light ->
                    LightTile(
                        light = light,
                        onToggle = { onToggleLight(light.entityId) },
                        onBrightnessStep = { delta ->
                            light.brightnessPercent?.let { onBrightnessStep(light.entityId, it, delta) }
                        },
                    )
                }
            }
        }

        if (room.scenes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(40.dp))
            SectionLabel("Scenes")
            room.scenes.chunked(3).forEach { rowScenes ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    rowScenes.forEach { scene ->
                        SceneTile(
                            scene = scene,
                            isActivated = activatedSceneId == scene.entityId,
                            onActivate = { onActivateScene(scene.entityId) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * CENTER/OK toggles on/off. When the light is on, available, and reports
 * brightness, focused CHANNEL UP/DOWN steps brightness. LEFT/RIGHT are
 * deliberately left alone so they keep moving focus between tiles.
 */
@Composable
internal fun LightTile(
    light: HomeAssistantLight,
    onToggle: () -> Unit,
    onBrightnessStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAdjustBrightness = light.isAvailable && light.isOn && light.brightnessPercent != null
    val glyph = when {
        !light.isAvailable -> "⊘"
        light.isOn -> "●"
        else -> "○"
    }
    val statusText = when {
        !light.isAvailable -> "Unavailable"
        !light.isOn -> "Off"
        light.brightnessPercent != null -> "On · ${light.brightnessPercent}%"
        else -> "On"
    }

    TvTile(
        label = light.friendlyName,
        onClick = { if (light.isAvailable) onToggle() },
        width = 220.dp,
        height = 150.dp,
        cornerRadius = 16.dp,
        modifier = modifier.onKeyEvent { event ->
            if (!canAdjustBrightness || event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { onBrightnessStep(BRIGHTNESS_STEP_PERCENT); true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { onBrightnessStep(-BRIGHTNESS_STEP_PERCENT); true }
                else -> false
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = glyph, style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (light.isAvailable) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun SceneTile(
    scene: HomeAssistantScene,
    isActivated: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvTile(
        label = scene.friendlyName,
        onClick = onActivate,
        width = 200.dp,
        height = 110.dp,
        cornerRadius = 14.dp,
        modifier = modifier,
    ) {
        Text(
            text = if (isActivated) "✓ Activated" else "✦",
            style = if (isActivated) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = if (isActivated) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White.copy(alpha = 0.85f),
        modifier = modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun CenteredHaMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
    }
}

private fun presenceLabel(presence: Presence): String = when (presence) {
    Presence.HOME -> "Home"
    Presence.AWAY -> "Away"
    Presence.UNKNOWN -> "Unknown"
}

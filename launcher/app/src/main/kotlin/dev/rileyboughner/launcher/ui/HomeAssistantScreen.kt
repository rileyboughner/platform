package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.homeassistant.HomeAssistantLight
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantResult
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRoom
import dev.rileyboughner.launcher.homeassistant.HomeAssistantSnapshot
import dev.rileyboughner.launcher.homeassistant.Presence
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Home Assistant page, between Dashboard and Apps in the pager: real
 * presence plus one room at a time (lights/brightness, scenes) via the HA
 * REST API — see [HomeAssistantRepository]. Rooms are switched with tap-only
 * tabs just below the presence line — deliberately not a nested horizontal
 * swipe, which would fight the outer pager's own swipe gesture. Text-first
 * per the monochrome restyle; the only non-text controls are the per-light
 * switch and brightness slider.
 */
@Composable
fun HomeAssistantScreen(homeAssistantRepository: HomeAssistantRepository, modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf<HomeAssistantResult<HomeAssistantSnapshot>?>(null) }
    var selectedRoomIndex by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        result = homeAssistantRepository.fetchSnapshot()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Home Assistant",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        when (val r = result) {
            null -> SectionMessage("Loading…")
            is HomeAssistantResult.NotConfigured ->
                SectionMessage("Home Assistant not configured. Add server + token in Settings.")
            is HomeAssistantResult.Error -> SectionMessage(r.message)
            is HomeAssistantResult.Success -> {
                val snapshot = r.value

                Text(
                    text = "Presence: ${presenceLabel(snapshot.presence)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )

                HairlineDivider()
                RoomTabRow(
                    roomNames = snapshot.rooms.map { it.name },
                    selectedIndex = selectedRoomIndex,
                    onSelect = { selectedRoomIndex = it },
                )
                HairlineDivider()

                snapshot.rooms.getOrNull(selectedRoomIndex)?.let { room ->
                    RoomSection(
                        room = room,
                        onToggleLight = { entityId ->
                            scope.launch {
                                homeAssistantRepository.toggleLight(entityId)
                                refresh()
                            }
                        },
                        onSetBrightness = { entityId, percent ->
                            scope.launch {
                                homeAssistantRepository.setLightBrightness(entityId, percent)
                                refresh()
                            }
                        },
                        onActivateScene = { entityId ->
                            scope.launch { homeAssistantRepository.activateScene(entityId) }
                        },
                    )
                }
            }
        }
    }
}

/** Tap-only room tabs — no swipe gesture here, so it never fights the pager's own horizontal swipe. */
@Composable
private fun RoomTabRow(
    roomNames: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        roomNames.forEachIndexed { index, name ->
            val selected = index == selectedIndex
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable(onClick = { onSelect(index) })
                    .padding(end = 24.dp),
            )
        }
    }
}

@Composable
private fun RoomSection(
    room: HomeAssistantRoom,
    onToggleLight: (String) -> Unit,
    onSetBrightness: (String, Int) -> Unit,
    onActivateScene: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(room.name)

        if (room.lights.isEmpty()) {
            SectionMessage("No lights found.")
        } else {
            room.lights.forEach { light ->
                LightRow(
                    light = light,
                    onToggle = { onToggleLight(light.entityId) },
                    onBrightnessChangeFinished = { percent -> onSetBrightness(light.entityId, percent) },
                )
            }
        }

        if (room.scenes.isNotEmpty()) {
            SubSectionHeader("Scenes")
            room.scenes.forEach { scene ->
                AppTextRow(
                    label = scene.friendlyName,
                    onClick = { onActivateScene(scene.entityId) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun SubSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun LightRow(
    light: HomeAssistantLight,
    onToggle: () -> Unit,
    onBrightnessChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(light.entityId, light.brightnessPercent) {
        mutableStateOf((light.brightnessPercent ?: 0).toFloat())
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = light.friendlyName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = light.isOn, onCheckedChange = { onToggle() })
        }
        if (light.isOn && light.brightnessPercent != null) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onBrightnessChangeFinished(sliderValue.roundToInt()) },
                valueRange = 1f..100f,
            )
        }
    }
}

private fun presenceLabel(presence: Presence): String = when (presence) {
    Presence.HOME -> "Home"
    Presence.AWAY -> "Away"
    Presence.UNKNOWN -> "Unknown"
}

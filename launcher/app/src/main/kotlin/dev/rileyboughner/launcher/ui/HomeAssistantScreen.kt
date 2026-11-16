package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.homeassistant.HomeAssistantLight
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantResult
import dev.rileyboughner.launcher.homeassistant.HomeAssistantSnapshot
import dev.rileyboughner.launcher.homeassistant.Presence
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Home Assistant page, between Dashboard and Apps in the pager: real
 * presence, light toggles/brightness, and scenes via the HA REST API — see
 * [HomeAssistantRepository]. Text-first per the monochrome restyle; the
 * only non-text controls are the per-light switch and brightness slider.
 */
@Composable
fun HomeAssistantScreen(homeAssistantRepository: HomeAssistantRepository, modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf<HomeAssistantResult<HomeAssistantSnapshot>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        result = homeAssistantRepository.fetchSnapshot()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                SectionHeader("Lights")
                if (snapshot.lights.isEmpty()) {
                    SectionMessage("No lights found.")
                } else {
                    snapshot.lights.forEach { light ->
                        LightRow(
                            light = light,
                            onToggle = {
                                scope.launch {
                                    homeAssistantRepository.toggleLight(light.entityId)
                                    refresh()
                                }
                            },
                            onBrightnessChangeFinished = { percent ->
                                scope.launch {
                                    homeAssistantRepository.setLightBrightness(light.entityId, percent)
                                    refresh()
                                }
                            },
                        )
                    }
                }

                HairlineDivider()
                SectionHeader("Scenes")
                if (snapshot.scenes.isEmpty()) {
                    SectionMessage("No scenes found.")
                } else {
                    snapshot.scenes.forEach { scene ->
                        AppTextRow(
                            label = scene.friendlyName,
                            onClick = { scope.launch { homeAssistantRepository.activateScene(scene.entityId) } },
                        )
                    }
                }
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

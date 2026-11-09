package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.R

/**
 * STUB. Small "glanceable entities" placeholder panel, per the platform
 * proposal (section 3, "Home controls") — presence, TV power, maybe
 * lights, not a full dashboard clone.
 *
 * Intentionally no Home Assistant REST/WebSocket client, no network calls,
 * no auth — just a static placeholder row showing what the real panel will
 * eventually surface.
 */
@Composable
fun HomeAssistantPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_assistant_panel_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.home_assistant_panel_stub_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package dev.rileyboughner.launcher.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.ui.AppTextRow
import dev.rileyboughner.launcher.ui.HairlineDivider

/**
 * Minimal, one-screen Settings: paste a Todoist API token, save it. Riley
 * generates the token himself from Todoist (Settings → Integrations →
 * Developer) and pastes it here — this app never asks for one and never
 * hardcodes one.
 */
@Composable
fun SettingsScreen(
    initialToken: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var token by rememberSaveable { mutableStateOf(initialToken) }

    Column(modifier = modifier.fillMaxSize()) {
        AppTextRow(label = "← Back", onClick = onBack)
        HairlineDivider()

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        Text(
            text = "Paste your Todoist API token (Todoist → Settings → Integrations → Developer).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        TextField(
            value = token,
            onValueChange = { token = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )

        HairlineDivider()
        AppTextRow(label = "Save", onClick = { onSave(token) })
    }
}

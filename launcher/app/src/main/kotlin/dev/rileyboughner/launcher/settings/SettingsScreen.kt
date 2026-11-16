package dev.rileyboughner.launcher.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

data class SettingsFields(
    val todoistToken: String,
    val jellyfinServerUrl: String,
    val jellyfinApiKey: String,
    val homeAssistantServerUrl: String,
    val homeAssistantToken: String,
    val duolingoUsername: String,
)

/**
 * Minimal, one-screen Settings: paste in the Todoist API token, Jellyfin
 * server URL + API key, and Home Assistant server URL + long-lived token,
 * then save. Riley generates/enters all of these himself — this app never
 * asks for one on his behalf and never hardcodes one (other than the
 * default Home Assistant server URL).
 */
@Composable
fun SettingsScreen(
    initialFields: SettingsFields,
    onSave: (SettingsFields) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var todoistToken by rememberSaveable { mutableStateOf(initialFields.todoistToken) }
    var jellyfinServerUrl by rememberSaveable { mutableStateOf(initialFields.jellyfinServerUrl) }
    var jellyfinApiKey by rememberSaveable { mutableStateOf(initialFields.jellyfinApiKey) }
    var homeAssistantServerUrl by rememberSaveable { mutableStateOf(initialFields.homeAssistantServerUrl) }
    var homeAssistantToken by rememberSaveable { mutableStateOf(initialFields.homeAssistantToken) }
    var duolingoUsername by rememberSaveable { mutableStateOf(initialFields.duolingoUsername) }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTextRow(label = "← Back", onClick = onBack)
        HairlineDivider()

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        SettingsSection(title = "Todoist", description = "Todoist → Settings → Integrations → Developer.") {
            LabeledField(label = "API token", value = todoistToken, onValueChange = { todoistToken = it })
        }

        SettingsSection(title = "Jellyfin", description = "Server URL and an API key (Jellyfin dashboard → API Keys).") {
            LabeledField(label = "Server URL", value = jellyfinServerUrl, onValueChange = { jellyfinServerUrl = it })
            LabeledField(label = "API key", value = jellyfinApiKey, onValueChange = { jellyfinApiKey = it })
        }

        SettingsSection(
            title = "Home Assistant",
            description = "Server URL and a long-lived access token (profile → Security → Long-lived access tokens).",
        ) {
            LabeledField(
                label = "Server URL",
                value = homeAssistantServerUrl,
                onValueChange = { homeAssistantServerUrl = it },
            )
            LabeledField(label = "Access token", value = homeAssistantToken, onValueChange = { homeAssistantToken = it })
        }

        SettingsSection(title = "Duolingo", description = "Your public Duolingo username — no password needed.") {
            LabeledField(label = "Username", value = duolingoUsername, onValueChange = { duolingoUsername = it })
        }

        HairlineDivider()
        AppTextRow(
            label = "Save",
            onClick = {
                onSave(
                    SettingsFields(
                        todoistToken = todoistToken,
                        jellyfinServerUrl = jellyfinServerUrl,
                        jellyfinApiKey = jellyfinApiKey,
                        homeAssistantServerUrl = homeAssistantServerUrl,
                        homeAssistantToken = homeAssistantToken,
                        duolingoUsername = duolingoUsername,
                    ),
                )
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        content()
        HairlineDivider()
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

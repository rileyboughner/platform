package dev.rileyboughner.launcher.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import dev.rileyboughner.launcher.health.HealthConnectRepository
import dev.rileyboughner.launcher.health.READ_SLEEP_PERMISSIONS
import dev.rileyboughner.launcher.ui.AppTextRow
import dev.rileyboughner.launcher.ui.HairlineDivider

data class SettingsFields(
    val todoistToken: String,
    val jellyfinServerUrl: String,
    val jellyfinApiKey: String,
    val homeAssistantServerUrl: String,
    val homeAssistantToken: String,
    val duolingoUsername: String,
    val showGlanceInfo: Boolean,
)

/**
 * Settings, grouped into Main page (glance toggle + the main-page app
 * picker entry point), Accounts & services (Todoist, Jellyfin, Home
 * Assistant, Duolingo), and Permissions (Health Connect, screen time).
 * Riley generates/enters all the accounts & services values himself — this
 * app never asks for one on his behalf and never hardcodes one (other than
 * the default Home Assistant/Jellyfin server URLs).
 */
@Composable
fun SettingsScreen(
    initialFields: SettingsFields,
    onSave: (SettingsFields) -> Unit,
    onBack: () -> Unit,
    onOpenMainPageFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var todoistToken by rememberSaveable { mutableStateOf(initialFields.todoistToken) }
    var jellyfinServerUrl by rememberSaveable { mutableStateOf(initialFields.jellyfinServerUrl) }
    var jellyfinApiKey by rememberSaveable { mutableStateOf(initialFields.jellyfinApiKey) }
    var homeAssistantServerUrl by rememberSaveable { mutableStateOf(initialFields.homeAssistantServerUrl) }
    var homeAssistantToken by rememberSaveable { mutableStateOf(initialFields.homeAssistantToken) }
    var duolingoUsername by rememberSaveable { mutableStateOf(initialFields.duolingoUsername) }
    var showGlanceInfo by rememberSaveable { mutableStateOf(initialFields.showGlanceInfo) }

    val context = LocalContext.current
    val healthConnectAvailable = remember { HealthConnectRepository.isAvailable(context) }
    var hasSleepPermission by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasSleepPermission = HealthConnectRepository.hasSleepPermission(context) }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted -> hasSleepPermission = granted.containsAll(READ_SLEEP_PERMISSIONS) }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTextRow(label = "← Back", onClick = onBack)
        HairlineDivider()

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        SettingsSection(
            title = "Main page",
            description = "The glance block (sleep, screen time, Duolingo) and which apps show under the clock.",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Show glance info",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(checked = showGlanceInfo, onCheckedChange = { showGlanceInfo = it })
            }
            AppTextRow(label = "Main-page apps", onClick = onOpenMainPageFavorites)
        }

        SettingsSection(
            title = "Accounts & services",
            description = "Server URLs and tokens for the integrations used elsewhere in the app.",
        ) {
            SubSectionLabel(text = "Todoist (Settings → Integrations → Developer)")
            LabeledField(label = "Todoist API token", value = todoistToken, onValueChange = { todoistToken = it })

            SubSectionLabel(text = "Jellyfin (dashboard → API Keys)")
            LabeledField(
                label = "Jellyfin server URL",
                value = jellyfinServerUrl,
                onValueChange = { jellyfinServerUrl = it },
            )
            LabeledField(label = "Jellyfin API key", value = jellyfinApiKey, onValueChange = { jellyfinApiKey = it })

            SubSectionLabel(text = "Home Assistant (profile → Security → Long-lived access tokens)")
            LabeledField(
                label = "Home Assistant server URL",
                value = homeAssistantServerUrl,
                onValueChange = { homeAssistantServerUrl = it },
            )
            LabeledField(
                label = "Home Assistant access token",
                value = homeAssistantToken,
                onValueChange = { homeAssistantToken = it },
            )

            SubSectionLabel(text = "Duolingo (public username, no password needed)")
            LabeledField(
                label = "Duolingo username",
                value = duolingoUsername,
                onValueChange = { duolingoUsername = it },
            )
        }

        SettingsSection(
            title = "Permissions",
            description = "Access this app needs from the system to show the main-page glance info.",
        ) {
            SubSectionLabel(
                text = "Sleep on the main page reads from Health Connect. Fitbit sleep only shows up here if " +
                    "the Fitbit app is set to sync sleep to Health Connect (Fitbit app → Health Connect settings).",
            )
            val statusLabel = when {
                !healthConnectAvailable -> "Health Connect isn't available on this device"
                hasSleepPermission -> "Sleep access granted"
                else -> "Sleep access not granted"
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (healthConnectAvailable && !hasSleepPermission) {
                AppTextRow(
                    label = "Grant Health Connect access",
                    onClick = { healthPermissionLauncher.launch(READ_SLEEP_PERMISSIONS) },
                )
            }

            SubSectionLabel(text = "Needed to show today's total foreground time on the main page.")
            AppTextRow(
                label = "Grant usage access",
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
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
                        showGlanceInfo = showGlanceInfo,
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

/** A small label introducing one service/permission within a [SettingsSection] that groups several. */
@Composable
private fun SubSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
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

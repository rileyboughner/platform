package dev.rileyboughner.launcher.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.rileyboughner.launcher.health.HealthConnectRepository
import dev.rileyboughner.launcher.health.READ_SLEEP_PERMISSIONS
import dev.rileyboughner.launcher.BuildConfig
import dev.rileyboughner.launcher.theme.SYNCED_THEMES
import dev.rileyboughner.launcher.ui.AppTextRow
import dev.rileyboughner.launcher.ui.HairlineDivider
import dev.rileyboughner.launcher.update.ManualUpdateCheckResult
import kotlinx.coroutines.launch

data class SettingsFields(
    val todoistToken: String,
    val jellyfinServerUrl: String,
    val jellyfinApiKey: String,
    val homeAssistantServerUrl: String,
    val homeAssistantToken: String,
    val duolingoUsername: String,
    val showGlanceInfo: Boolean,
    val syncEnabled: Boolean,
    val syncTheme: String,
    val syncMode: String,
)

/**
 * Settings, grouped into Main page (glance toggle + the main-page app
 * picker entry point), Accounts & services (Todoist, Jellyfin, Home
 * Assistant, Duolingo), Permissions (Health Connect, screen time), and
 * Updates (installed version + a manual update check). Riley
 * generates/enters all the accounts & services values himself — this
 * app never asks for one on his behalf and never hardcodes one (other than
 * the default Home Assistant/Jellyfin server URLs).
 */
@Composable
fun SettingsScreen(
    initialFields: SettingsFields,
    onSave: (SettingsFields) -> Unit,
    onBack: () -> Unit,
    onOpenMainPageFavorites: () -> Unit,
    manualChecking: Boolean,
    manualCheckResult: ManualUpdateCheckResult?,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var todoistToken by rememberSaveable { mutableStateOf(initialFields.todoistToken) }
    var jellyfinServerUrl by rememberSaveable { mutableStateOf(initialFields.jellyfinServerUrl) }
    var jellyfinApiKey by rememberSaveable { mutableStateOf(initialFields.jellyfinApiKey) }
    var homeAssistantServerUrl by rememberSaveable { mutableStateOf(initialFields.homeAssistantServerUrl) }
    var homeAssistantToken by rememberSaveable { mutableStateOf(initialFields.homeAssistantToken) }
    var duolingoUsername by rememberSaveable { mutableStateOf(initialFields.duolingoUsername) }
    var showGlanceInfo by rememberSaveable { mutableStateOf(initialFields.showGlanceInfo) }
    var syncEnabled by rememberSaveable { mutableStateOf(initialFields.syncEnabled) }
    var syncTheme by rememberSaveable { mutableStateOf(initialFields.syncTheme) }
    var syncMode by rememberSaveable { mutableStateOf(initialFields.syncMode) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Re-read on every ON_RESUME rather than once via a plain `remember` —
    // getSdkStatus() can flip from unavailable to available between opens
    // of this screen (provider install/update, or simply after this app's
    // own getOrCreate() call below finally registers it with the
    // controller), and a one-shot read would never pick that up without a
    // full app restart.
    var sdkStatusRaw by remember { mutableStateOf(HealthConnectRepository.sdkStatus(context)) }
    var hasSleepPermission by remember { mutableStateOf(false) }
    var registrationOutcome by remember { mutableStateOf<String?>(null) }
    var grantOutcome by remember { mutableStateOf<String?>(null) }
    val healthConnectAvailable = sdkStatusRaw == HealthConnectClient.SDK_AVAILABLE

    fun refreshHealthConnectState() {
        sdkStatusRaw = HealthConnectRepository.sdkStatus(context)
        coroutineScope.launch { hasSleepPermission = HealthConnectRepository.hasSleepPermission(context) }
        // Unconditional: call getOrCreate()/getGrantedPermissions() regardless of
        // what the status check above says, so the app actually registers with
        // the Health Connect controller even when that check is pessimistic.
        coroutineScope.launch { registrationOutcome = HealthConnectRepository.attemptRegistration(context) }
    }

    LaunchedEffect(Unit) { refreshHealthConnectState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshHealthConnectState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        hasSleepPermission = granted.containsAll(READ_SLEEP_PERMISSIONS)
        grantOutcome = "Last grant attempt: ${granted.size} permission(s) returned, " +
            "sleep included: ${granted.containsAll(READ_SLEEP_PERMISSIONS)}"
    }

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
            title = "Theme & wallpaper sync",
            description = "Syncs the pinned theme and wallpaper with the server hub. " +
                "Off by default.",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Sync with hub",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(checked = syncEnabled, onCheckedChange = { syncEnabled = it })
            }

            SubSectionLabel(text = "Sync scope")
            SelectableRow(
                label = "Unified — every device shares one theme",
                selected = syncMode == "unified",
                onClick = { syncMode = "unified" },
            )
            SelectableRow(
                label = "Split — desktop/laptop and phone/tablet sync separately",
                selected = syncMode == "split",
                onClick = { syncMode = "split" },
            )

            SubSectionLabel(text = "Theme")
            SYNCED_THEMES.forEach { theme ->
                SelectableRow(
                    label = theme.displayName,
                    selected = syncTheme == theme.key,
                    onClick = { syncTheme = theme.key },
                )
            }
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
            // Raw diagnostics: the status check above can be wrong (that's the
            // whole reason attemptRegistration() runs unconditionally below it),
            // so surface exactly what the SDK and the registration attempt
            // actually returned instead of only a derived yes/no.
            Text(
                text = "SDK status: ${HealthConnectRepository.sdkStatusName(sdkStatusRaw)} ($sdkStatusRaw)\n" +
                    "Registration: ${registrationOutcome ?: "checking…"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            grantOutcome?.let { outcome ->
                Text(
                    text = outcome,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            if (healthConnectAvailable && !hasSleepPermission) {
                AppTextRow(
                    label = "Grant Health Connect access",
                    onClick = {
                        try {
                            healthPermissionLauncher.launch(READ_SLEEP_PERMISSIONS)
                        } catch (e: Exception) {
                            grantOutcome = "Grant launch failed: ${e::class.simpleName}: ${e.message}"
                        }
                    },
                )
            }

            SubSectionLabel(text = "Needed to show today's total foreground time on the main page.")
            AppTextRow(
                label = "Grant usage access",
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
        }

        SettingsSection(
            title = "Updates",
            description = "The launcher checks for updates on launch. Check here any time — a manual check works even for a version dismissed with Later.",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppTextRow(
                label = if (manualChecking) "Checking…" else "Check for updates",
                onClick = { if (!manualChecking) onCheckForUpdates() },
            )
            when (manualCheckResult) {
                is ManualUpdateCheckResult.UpToDate -> {
                    Text(
                        text = "You're up to date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                is ManualUpdateCheckResult.Unreachable -> {
                    Text(
                        text = "Couldn't reach GitHub — check your connection and try again",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                else -> {}
            }
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
                        syncEnabled = syncEnabled,
                        syncTheme = syncTheme,
                        syncMode = syncMode,
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

/** One option in a single-choice list (sync scope, synced theme) — a checkmark plus the accent color (via MaterialTheme.colorScheme.primary) mark the selected row, text-only like the rest of this screen. */
@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = if (selected) "✓ $label" else "   $label",
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
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

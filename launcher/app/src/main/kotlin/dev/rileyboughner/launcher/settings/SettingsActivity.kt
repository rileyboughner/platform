package dev.rileyboughner.launcher.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import dev.rileyboughner.launcher.AppListRepository
import dev.rileyboughner.launcher.BuildConfig
import dev.rileyboughner.launcher.theme.MonoLauncherTheme
import dev.rileyboughner.launcher.theme.ThemeSyncRepository
import dev.rileyboughner.launcher.theme.accentColorFor
import dev.rileyboughner.launcher.update.AllowUnknownSourcesDialog
import dev.rileyboughner.launcher.update.ManualUpdateCheckResult
import dev.rileyboughner.launcher.update.UpdateInfo
import dev.rileyboughner.launcher.update.UpdateInstaller
import dev.rileyboughner.launcher.update.UpdatePromptDialog
import dev.rileyboughner.launcher.update.UpdateRepository
import kotlinx.coroutines.launch

/**
 * Settings, reached from the "Settings" text row on the Apps page. Lets
 * Riley paste in his own Todoist API token, Jellyfin server URL + API key,
 * and Home Assistant server URL + long-lived token, and pick which apps
 * show on the main page (under what name, and in what order) — see
 * [SettingsRepository] for storage, [SettingsScreen] for the main UI, and
 * [MainPageFavoritesPickerScreen] for the app picker.
 *
 * Also hosts the manual update check (version row + "Check for updates"
 * button in [SettingsScreen]'s Updates section): the check runs on tap
 * and ignores any version dismissed with Later, and an available update
 * shows the same [UpdatePromptDialog] the launch-time prompt uses.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appListRepository: AppListRepository
    private lateinit var themeSyncRepository: ThemeSyncRepository
    private lateinit var updateRepository: UpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        appListRepository = AppListRepository(applicationContext)
        themeSyncRepository = ThemeSyncRepository(applicationContext, settingsRepository)
        updateRepository = UpdateRepository(applicationContext)
        val allApps = appListRepository.getLaunchableApps()

        setContent {
            MonoLauncherTheme(accentColor = accentColorFor(settingsRepository.getSyncTheme())) {
                var showFavoritesPicker by remember { mutableStateOf(false) }
                var manualChecking by remember { mutableStateOf(false) }
                var manualCheckResult by remember { mutableStateOf<ManualUpdateCheckResult?>(null) }
                var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
                var downloading by remember { mutableStateOf(false) }
                var showUnknownSourcesPrompt by remember { mutableStateOf(false) }
                val context = LocalContext.current

                if (showFavoritesPicker) {
                    MainPageFavoritesPickerScreen(
                        allApps = allApps,
                        initialFavorites = settingsRepository.getEffectiveMainPageFavorites(allApps),
                        onSave = { favorites ->
                            settingsRepository.setMainPageFavorites(favorites)
                            showFavoritesPicker = false
                        },
                        onBack = { showFavoritesPicker = false },
                    )
                } else {
                    SettingsScreen(
                        initialFields = SettingsFields(
                            todoistToken = settingsRepository.getTodoistToken() ?: "",
                            nextcloudUsername = settingsRepository.getNextcloudUsername() ?: "",
                            nextcloudAppPassword = settingsRepository.getNextcloudAppPassword() ?: "",
                            jellyfinServerUrl = settingsRepository.getJellyfinServerUrl(),
                            jellyfinApiKey = settingsRepository.getJellyfinApiKey() ?: "",
                            homeAssistantServerUrl = settingsRepository.getHomeAssistantServerUrl(),
                            homeAssistantToken = settingsRepository.getHomeAssistantToken() ?: "",
                            duolingoUsername = settingsRepository.getDuolingoUsername() ?: "",
                            showGlanceInfo = settingsRepository.getShowGlanceInfo(),
                            syncEnabled = settingsRepository.getSyncEnabled(),
                            syncTheme = settingsRepository.getSyncTheme(),
                            syncMode = settingsRepository.getSyncModeCache(),
                        ),
                        onSave = { fields ->
                            settingsRepository.setTodoistToken(fields.todoistToken)
                            settingsRepository.setNextcloudUsername(fields.nextcloudUsername)
                            settingsRepository.setNextcloudAppPassword(fields.nextcloudAppPassword)
                            settingsRepository.setJellyfinServerUrl(fields.jellyfinServerUrl)
                            settingsRepository.setJellyfinApiKey(fields.jellyfinApiKey)
                            settingsRepository.setHomeAssistantServerUrl(fields.homeAssistantServerUrl)
                            settingsRepository.setHomeAssistantToken(fields.homeAssistantToken)
                            settingsRepository.setDuolingoUsername(fields.duolingoUsername)
                            settingsRepository.setShowGlanceInfo(fields.showGlanceInfo)
                            settingsRepository.setSyncEnabled(fields.syncEnabled)
                            // Network pushes (theme/mode to the hub) run on
                            // lifecycleScope rather than finishing first: both
                            // are no-ops when sync is off, and otherwise need
                            // to actually complete the write before this
                            // Activity (and its ThemeSyncRepository instance)
                            // goes away.
                            lifecycleScope.launch {
                                themeSyncRepository.pushMode(fields.syncMode)
                                themeSyncRepository.pushTheme(fields.syncTheme)
                                finish()
                            }
                        },
                        onBack = { finish() },
                        onOpenMainPageFavorites = { showFavoritesPicker = true },
                        manualChecking = manualChecking,
                        manualCheckResult = manualCheckResult,
                        onCheckForUpdates = {
                            manualChecking = true
                            manualCheckResult = null
                            lifecycleScope.launch {
                                val result = updateRepository.checkForUpdateManual(BuildConfig.VERSION_CODE)
                                manualChecking = false
                                manualCheckResult = result
                                if (result is ManualUpdateCheckResult.Available) {
                                    pendingUpdate = result.info
                                }
                            }
                        },
                        fetchServerTokens = { TokenSyncApi.fetchTokens() },
                    )
                }

                pendingUpdate?.let { info ->
                    UpdatePromptDialog(
                        updateInfo = info,
                        downloading = downloading,
                        onUpdate = {
                            downloading = true
                            lifecycleScope.launch {
                                try {
                                    if (UpdateInstaller.downloadAndInstall(context, info.apkUrl)) {
                                        pendingUpdate = null
                                    } else {
                                        showUnknownSourcesPrompt = true
                                    }
                                } catch (e: Exception) {
                                    pendingUpdate = null
                                } finally {
                                    downloading = false
                                }
                            }
                        },
                        onLater = {
                            updateRepository.dismiss(info.versionCode)
                            pendingUpdate = null
                        },
                    )
                }

                if (showUnknownSourcesPrompt) {
                    AllowUnknownSourcesDialog(
                        onOpenSettings = {
                            showUnknownSourcesPrompt = false
                            context.startActivity(UpdateInstaller.unknownSourcesSettingsIntent(context))
                        },
                        onDismiss = { showUnknownSourcesPrompt = false },
                    )
                }
            }
        }
    }
}

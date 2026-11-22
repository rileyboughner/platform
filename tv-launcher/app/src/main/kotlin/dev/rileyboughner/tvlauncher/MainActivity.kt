package dev.rileyboughner.tvlauncher

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import dev.rileyboughner.tvlauncher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.tvlauncher.settings.SettingsStore
import dev.rileyboughner.tvlauncher.settings.TokenSyncApi
import dev.rileyboughner.tvlauncher.theme.ThemeSyncRepository
import dev.rileyboughner.tvlauncher.theme.TvLauncherTheme
import dev.rileyboughner.tvlauncher.ui.TvHomeScreen
import dev.rileyboughner.tvlauncher.update.UpdatePrompt
import dev.rileyboughner.tvlauncher.update.UpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Google TV / Android TV entry point — the only activity in this app. It
 * declares the LEANBACK_LAUNCHER + HOME intent filters in
 * AndroidManifest.xml, so this activity is offered as a "home" on
 * Leanback (Android TV / Google TV) devices.
 *
 * This is a separate, standalone TV launcher (package
 * `dev.rileyboughner.tvlauncher`), not the phone launcher's TV screen:
 * its only screen is [TvHomeScreen] (Jellyfin, YouTube, Weave Music,
 * living-room Home Assistant lights/scenes, synced wallpaper). No phone
 * UI, no dashboard, no calendar, no todo, no settings screen.
 *
 * Two launch-time jobs run here:
 * - Home Assistant token: pulled from the launcher token server on every
 *   launch ([TokenSyncApi]) and stored encrypted; a missing/blank fetched
 *   token leaves the stored one alone, so an unreachable server never
 *   wipes a working token.
 * - Wallpaper: polled from the theme-sync hub ([ThemeSyncRepository.pollTv])
 *   while this activity is in the foreground, plus once immediately on
 *   every resume; the bitmap is drawn as the home screen's background.
 *   The hub being unreachable is an expected, graceful state — the screen
 *   just keeps its plain black background.
 *
 * It also hosts the shared update prompt ([UpdatePrompt]) so the TV
 * updates itself without ADB.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var homeAssistantRepository: HomeAssistantRepository
    private lateinit var updateRepository: UpdateRepository
    private lateinit var themeSyncRepository: ThemeSyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(applicationContext)
        // The TV has no Settings screen, so it pulls its Home Assistant
        // token from the server on every launch instead of waiting for a
        // manual sync: a missing/blank token leaves the stored one alone,
        // so an unreachable server never wipes a working token.
        lifecycleScope.launch {
            TokenSyncApi.fetchTokens()
                ?.homeAssistantToken
                ?.takeIf { it.isNotBlank() }
                ?.let { settingsStore.setHomeAssistantToken(it) }
        }
        homeAssistantRepository = HomeAssistantRepository(settingsStore)
        updateRepository = UpdateRepository(applicationContext)
        themeSyncRepository = ThemeSyncRepository(settingsStore)

        setContent {
            var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }

            WallpaperPoller {
                themeSyncRepository.pollTv()?.let { result ->
                    result.wallpaperBitmap?.let { wallpaperBitmap = it }
                }
            }

            TvLauncherTheme {
                TvHomeScreen(
                    homeAssistantRepository = homeAssistantRepository,
                    wallpaperBitmap = wallpaperBitmap,
                )
                UpdatePrompt(updateRepository)
            }
        }
    }
}

/**
 * Keeps the synced wallpaper current while this activity is actually in
 * the foreground, plus once immediately on every ON_RESUME: poll now,
 * then re-poll every 30s. Matching ON_PAUSE/ON_RESUME (rather than a
 * plain always-running loop) stops this from polling the hub while the
 * launcher isn't on screen. Poll failures are swallowed by
 * [ThemeSyncRepository.pollTv] — a null result just keeps the current
 * wallpaper.
 */
@Composable
private fun WallpaperPoller(poll: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        var pollJob: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    pollJob = coroutineScope.launch {
                        while (isActive) {
                            runCatching { poll() }
                            delay(30_000)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> pollJob?.cancel()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pollJob?.cancel()
        }
    }
}

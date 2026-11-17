package dev.rileyboughner.launcher

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.settings.SettingsRepository
import dev.rileyboughner.launcher.settings.TokenSyncApi
import dev.rileyboughner.launcher.theme.MonoLauncherTheme
import dev.rileyboughner.launcher.theme.ThemeSyncPoller
import dev.rileyboughner.launcher.theme.ThemeSyncRepository
import dev.rileyboughner.launcher.theme.accentColorFor
import dev.rileyboughner.launcher.ui.TvHomeScreen
import dev.rileyboughner.launcher.update.UpdatePrompt
import dev.rileyboughner.launcher.update.UpdateRepository
import kotlinx.coroutines.launch

/**
 * Google TV / Android TV entry point. Declares the LEANBACK_LAUNCHER
 * intent filter in AndroidManifest.xml, so this activity is offered as a
 * candidate "home" on Leanback (Android TV / Google TV) devices.
 *
 * A separate activity from [MainActivity] so the 10-foot D-pad-navigable
 * [TvHomeScreen] and the phone's touch/gesture UI never load on the wrong
 * form factor. The TV page is a single screen (clock, apps row, Home
 * Assistant lights/scenes rows) with no further navigation, so this
 * activity only needs [HomeAssistantRepository] — it shares that
 * repository's underlying [SettingsRepository]-backed server URL/token with
 * the phone UI's Settings screen, but has no TV-side settings screen of its
 * own.
 *
 * Theme sync runs here too ([ThemeSyncRepository.pollTv], always on — the
 * TV has no settings toggle): the synced theme's accent tints
 * [MonoLauncherTheme], and the synced wallpaper bitmap is drawn as the home
 * screen's background behind the tiles.
 *
 * It also hosts the shared update prompt ([UpdatePrompt]) so the TV
 * updates itself without ADB, and runs the one-time baked-token migration
 * ([SettingsRepository.migrateBakedHomeAssistantTokenIfNeeded]) so
 * token-less self-updates keep Home Assistant working.
 */
class TvMainActivity : ComponentActivity() {

    private lateinit var homeAssistantRepository: HomeAssistantRepository
    private lateinit var updateRepository: UpdateRepository
    private lateinit var themeSyncRepository: ThemeSyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(applicationContext)
        settingsRepository.migrateBakedHomeAssistantTokenIfNeeded()
        // The TV has no Settings screen, so it pulls its Home Assistant
        // token from the server on every launch instead of waiting for a
        // manual sync: a missing/blank token leaves the stored one alone,
        // so an unreachable server never wipes a working token, and a
        // rotated server token propagates on its own.
        lifecycleScope.launch {
            TokenSyncApi.fetchTokens()
                ?.homeAssistantToken
                ?.takeIf { it.isNotBlank() }
                ?.let { settingsRepository.setHomeAssistantToken(it) }
        }
        homeAssistantRepository = HomeAssistantRepository(settingsRepository)
        updateRepository = UpdateRepository(applicationContext)
        themeSyncRepository = ThemeSyncRepository(applicationContext, settingsRepository)

        setContent {
            var accentColor by remember { mutableStateOf(accentColorFor(settingsRepository.getSyncTheme())) }
            var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }

            ThemeSyncPoller {
                themeSyncRepository.pollTv()?.let { result ->
                    accentColor = accentColorFor(result.themeKey)
                    result.wallpaperBitmap?.let { wallpaperBitmap = it }
                }
            }

            MonoLauncherTheme(accentColor = accentColor) {
                TvHomeScreen(
                    homeAssistantRepository = homeAssistantRepository,
                    wallpaperBitmap = wallpaperBitmap,
                )
                UpdatePrompt(updateRepository)
            }
        }
    }
}

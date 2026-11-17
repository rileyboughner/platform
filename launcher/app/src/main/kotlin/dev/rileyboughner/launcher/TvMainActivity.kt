package dev.rileyboughner.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.settings.SettingsRepository
import dev.rileyboughner.launcher.theme.MonoLauncherTheme
import dev.rileyboughner.launcher.ui.TvHomeScreen
import dev.rileyboughner.launcher.update.UpdatePrompt
import dev.rileyboughner.launcher.update.UpdateRepository

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
 * It also hosts the shared update prompt ([UpdatePrompt]) so the TV
 * updates itself without ADB, and runs the one-time baked-token migration
 * ([SettingsRepository.migrateBakedHomeAssistantTokenIfNeeded]) so
 * token-less self-updates keep Home Assistant working.
 */
class TvMainActivity : ComponentActivity() {

    private lateinit var homeAssistantRepository: HomeAssistantRepository
    private lateinit var updateRepository: UpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(applicationContext)
        settingsRepository.migrateBakedHomeAssistantTokenIfNeeded()
        homeAssistantRepository = HomeAssistantRepository(settingsRepository)
        updateRepository = UpdateRepository(applicationContext)

        setContent {
            MonoLauncherTheme {
                TvHomeScreen(homeAssistantRepository = homeAssistantRepository)
                UpdatePrompt(updateRepository)
            }
        }
    }
}

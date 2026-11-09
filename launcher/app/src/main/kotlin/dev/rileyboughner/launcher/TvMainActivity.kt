package dev.rileyboughner.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import dev.rileyboughner.launcher.theme.LauncherTheme
import dev.rileyboughner.launcher.ui.TvHomeScreen

/**
 * Google TV / Android TV entry point. Declares the LEANBACK_LAUNCHER
 * intent filter in AndroidManifest.xml, so this activity is offered as a
 * candidate "home" on Leanback (Android TV / Google TV) devices.
 *
 * Stub only — see [TvHomeScreen]. A separate activity from [MainActivity]
 * so the 10-foot Compose-for-TV UI and the touch UI never load on the
 * wrong form factor.
 */
class TvMainActivity : ComponentActivity() {

    private lateinit var appListRepository: AppListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appListRepository = AppListRepository(applicationContext)

        setContent {
            val apps = remember { appListRepository.getLaunchableApps() }

            LauncherTheme {
                TvHomeScreen(
                    apps = apps,
                    onAppClick = { app -> appListRepository.launchApp(app.packageName) },
                )
            }
        }
    }
}

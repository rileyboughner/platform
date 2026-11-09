package dev.rileyboughner.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import dev.rileyboughner.launcher.theme.LauncherTheme
import dev.rileyboughner.launcher.ui.AppGridScreen

/**
 * Phone/tablet entry point. Declares the HOME + LAUNCHER intent filters in
 * AndroidManifest.xml, so Android offers this activity when the user picks
 * a default home app in Settings → Apps → Default apps → Home app.
 */
class MainActivity : ComponentActivity() {

    private lateinit var appListRepository: AppListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appListRepository = AppListRepository(applicationContext)

        setContent {
            val apps = remember { appListRepository.getLaunchableApps() }

            LauncherTheme {
                AppGridScreen(
                    apps = apps,
                    onAppClick = { app -> appListRepository.launchApp(app.packageName) },
                )
            }
        }
    }
}

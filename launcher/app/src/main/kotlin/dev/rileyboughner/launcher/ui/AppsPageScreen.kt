package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.AppListRepository
import dev.rileyboughner.launcher.FavoritesRepository

/**
 * The Apps page — center page of the pager, and the default/start page.
 * Minimal black-and-white text launcher per Riley's restyle request: large
 * text rows, no icons, hairline separators only (see [dev.rileyboughner.launcher.theme.MonoLauncherTheme]).
 *
 * Shows, top to bottom: the existing Jellyfin/Home Assistant stub rows
 * (kept as-is, unmodified), the hardcoded favorites list from
 * [FavoritesRepository], then "All apps" and "Settings" nav rows. Tapping
 * "All apps" swaps in [AllAppsScreen] in place (not a separate pager page).
 */
@Composable
fun AppsPageScreen(
    appListRepository: AppListRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allApps = remember { appListRepository.getLaunchableApps() }
    val favorites = remember(allApps) { FavoritesRepository.resolveFavorites(allApps) }
    var showAllApps by rememberSaveable { mutableStateOf(false) }

    if (showAllApps) {
        AllAppsScreen(
            apps = allApps,
            onAppClick = { app -> appListRepository.launchApp(app.packageName) },
            onBack = { showAllApps = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        JellyfinContinueWatchingRow(modifier = Modifier.fillMaxWidth())
        HomeAssistantPanel(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        favorites.forEach { app ->
            AppTextRow(
                label = app.label,
                badgeCount = app.badgeCount,
                onClick = { appListRepository.launchApp(app.packageName) },
            )
            HairlineDivider()
        }

        AppTextRow(label = "All apps", onClick = { showAllApps = true })
        HairlineDivider()
        AppTextRow(label = "Settings", onClick = onOpenSettings)
    }
}

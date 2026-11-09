package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.rileyboughner.launcher.LauncherAppInfo
import dev.rileyboughner.launcher.R

/**
 * STUB. Minimal Google TV / Leanback "10-foot" screen — a single D-pad
 * navigable row of installed apps, no browse rail, no rows-of-rows, no
 * Jellyfin/Home Assistant panels yet. This exists to prove out the TV
 * entry point ([dev.rileyboughner.launcher.TvMainActivity]) and the
 * Compose-for-TV dependency wiring; the real TV layout is future work.
 */
@Composable
fun TvHomeScreen(
    apps: List<LauncherAppInfo>,
    onAppClick: (LauncherAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(32.dp)) {
            Text(text = stringResource(R.string.tv_home_title))
            Text(text = stringResource(R.string.tv_home_stub_notice))

            TvLazyRow(modifier = Modifier.padding(top = 24.dp)) {
                items(apps) { app ->
                    TvAppTile(app = app, onClick = { onAppClick(app) })
                }
            }
        }
    }
}

@Composable
private fun TvAppTile(app: LauncherAppInfo, onClick: () -> Unit) {
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = Modifier.padding(8.dp),
    ) {
        Text(text = app.label)
    }
}

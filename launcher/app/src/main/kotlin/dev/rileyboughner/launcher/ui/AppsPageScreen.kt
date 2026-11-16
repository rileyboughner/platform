package dev.rileyboughner.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.rileyboughner.launcher.AppListRepository
import dev.rileyboughner.launcher.FavoritesRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The Apps page — center page of the pager, and the default/start page.
 * Restyled to look like Olauncher (minimal AF launcher): a large clock +
 * date at the top over the system wallpaper, then plain left-aligned
 * favorite rows with generous spacing (no icons, no dividers), then "All
 * apps" and "Settings" nav rows. Tapping "All apps" swaps in
 * [AllAppsScreen] in place (not a separate pager page).
 */
@Composable
fun AppsPageScreen(
    appListRepository: AppListRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allApps = remember { appListRepository.getLaunchableApps() }
    val favorites = remember(allApps) { FavoritesRepository.resolveFavorites(allApps) }
    var showAllApps by rememberSaveable { mutableStateOf(false) }

    if (showAllApps) {
        AllAppsScreen(
            apps = allApps,
            onAppClick = { app -> appListRepository.launchApp(app.packageName) },
            onBack = { showAllApps = false },
            modifier = modifier.background(MaterialTheme.colorScheme.background),
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        WallpaperBackground(modifier = Modifier.fillMaxSize())

        // Subtle dark scrim over the wallpaper so white text stays legible.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            ClockHeader(onClockClick = { openAlarmApp(context) })

            Spacer(modifier = Modifier.height(48.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                favorites.forEach { app ->
                    FavoriteRow(label = app.label, onClick = { appListRepository.launchApp(app.packageName) })
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            FavoriteRow(label = "All apps", onClick = { showAllApps = true })
            FavoriteRow(label = "Settings", onClick = onOpenSettings)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ClockHeader(onClockClick: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }

    Column(
        modifier = modifier.clickable(onClick = onClockClick),
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** Plain text row: left-aligned, no icon, no divider, generous padding for the Apps page. */
@Composable
private fun FavoriteRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/** Paints the system wallpaper behind the page; falls back to nothing (solid scrim shows through) if unavailable. */
@Composable
private fun WallpaperBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wallpaperDrawable = remember {
        runCatching { WallpaperManager.getInstance(context).drawable }.getOrNull()
    }
    if (wallpaperDrawable == null) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { imageView -> imageView.setImageDrawable(wallpaperDrawable) },
    )
}

private fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No alarm app found", Toast.LENGTH_SHORT).show()
    }
}

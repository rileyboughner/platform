package dev.rileyboughner.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository
import dev.rileyboughner.launcher.jellyfin.JellyfinResult
import dev.rileyboughner.launcher.jellyfin.JellyfinResumeItem

private const val JELLYFIN_PACKAGE_NAME = "org.jellyfin.mobile"

/**
 * "Continue watching" section of the homelab dashboard page: real Jellyfin
 * resume items (poster + title + progress bar), each tap deep-linking back
 * into the Jellyfin app (or its web UI if the app isn't installed).
 */
@Composable
fun JellyfinContinueWatchingSection(jellyfinRepository: JellyfinRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<JellyfinResult<List<JellyfinResumeItem>>?>(null) }

    LaunchedEffect(Unit) {
        result = jellyfinRepository.fetchResumeItems()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )

        when (val r = result) {
            null -> DashboardMessage("Loading…")
            is JellyfinResult.NotConfigured -> DashboardMessage("Jellyfin not configured. Add server + API key in Settings.")
            is JellyfinResult.Error -> DashboardMessage(r.message)
            is JellyfinResult.Success -> {
                if (r.value.isEmpty()) {
                    DashboardMessage("Nothing to continue watching.")
                } else {
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        items(r.value, key = { it.id }) { item ->
                            PosterCard(
                                item = item,
                                onClick = { openJellyfin(context, jellyfinRepository.serverUrl(), item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun PosterCard(item: JellyfinResumeItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            LinearProgressIndicator(
                progress = item.progressFraction,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                color = MaterialTheme.colorScheme.onBackground,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Deep-links into the Jellyfin app if installed; opens the web UI in a browser otherwise. */
private fun openJellyfin(context: Context, serverUrl: String?, itemId: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(JELLYFIN_PACKAGE_NAME)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return
    }
    if (serverUrl != null) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("$serverUrl/web/index.html#!/details?id=$itemId"))
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(webIntent)
    }
}

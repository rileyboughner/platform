package dev.rileyboughner.tvlauncher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.rileyboughner.tvlauncher.jellyfin.JellyfinRepository
import dev.rileyboughner.tvlauncher.jellyfin.JellyfinResult
import dev.rileyboughner.tvlauncher.jellyfin.JellyfinResumeItem

private const val JELLYFIN_TV_PACKAGE_NAME = "org.jellyfin.androidtv"

/**
 * "Continue Watching" row for the TV home screen: the house account's
 * in-progress Jellyfin items — poster, title, progress bar, remaining
 * time. Tapping one opens Jellyfin.
 *
 * Hidden entirely (label included) when there is nothing in progress,
 * Jellyfin isn't configured, or the fetch fails: the home screen never
 * shows an empty row or an error for this. A trailing spacer is part of
 * the row so the gap below it only exists when the row is visible.
 *
 * Posters are portrait 2:3 like the app tiles are landscape 16:9, and the
 * focus treatment matches the app tiles: scale up, bright ring, soft
 * shadow, dimmed when unfocused. D-pad navigable throughout.
 */
@Composable
fun JellyfinContinueWatchingRow(
    jellyfinRepository: JellyfinRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<JellyfinResumeItem>?>(null) }

    LaunchedEffect(Unit) {
        items = (jellyfinRepository.fetchResumeItems() as? JellyfinResult.Success)?.value
    }

    val resumeItems = items ?: return
    if (resumeItems.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            // Leading/trailing spacers INSIDE the row: room for the
            // first/last poster to scale up on focus without hitting
            // the Row's clip edge (same pattern as the app tiles row).
            Spacer(modifier = Modifier.width(20.dp))
            resumeItems.forEach { item ->
                ResumePosterCard(
                    item = item,
                    onClick = { openJellyfin(context, jellyfinRepository.serverUrl(), item.id) },
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
        }
        Spacer(modifier = Modifier.height(36.dp))
    }
}

/** One resume item: portrait poster with a progress bar, title and detail line below. */
@Composable
private fun ResumePosterCard(
    item: JellyfinResumeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1f, label = "resumeCardScale")
    val shape = remember { RoundedCornerShape(16.dp) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(170.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 170.dp, height = 255.dp)
                .scale(scale)
                .shadow(if (isFocused) 14.dp else 0.dp, shape)
                .clip(shape)
                .background(Color(0xFF141414))
                .border(width = if (isFocused) 3.dp else 0.dp, color = Color.White, shape = shape)
                .alpha(if (isFocused) 1f else 0.8f)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
            }
            // Progress bar pinned to the bottom of the poster: white fill
            // over a translucent track, same language as the light tiles'
            // brightness bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = item.progressFraction.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(Color.White),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val detail = listOfNotNull(item.subtitle, item.remainingText)
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Opens the Jellyfin Android TV app if installed (same launch intent the
 * Jellyfin app tile uses); opens the item's web details page in a browser
 * otherwise. The Android TV app has no documented per-item deep link, so
 * tapping opens Jellyfin itself rather than jumping straight to the item.
 */
private fun openJellyfin(context: Context, serverUrl: String, itemId: String) {
    val launchIntent = context.packageManager.getLeanbackLaunchIntentForPackage(JELLYFIN_TV_PACKAGE_NAME)
        ?: context.packageManager.getLaunchIntentForPackage(JELLYFIN_TV_PACKAGE_NAME)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return
    }
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("$serverUrl/web/index.html#!/details?id=$itemId"))
    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(webIntent)
}

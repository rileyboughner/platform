package dev.rileyboughner.launcher.ui

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.rememberBringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

/**
 * Apple TV-style tile: poster/banner artwork that scales up and gains a
 * bright focus ring on D-pad focus, with its label below. The single
 * building block for both the main tile row (Jellyfin/YouTube/Weave/movie
 * wheel) and the smaller second row (Dashboard/Home Assistant/Calendar/
 * Todo) — callers just pick a size and image content.
 *
 * Focus scrolling: when a tile gains focus, the WHOLE tile (box + label
 * below it) is brought into view — the default focused-node behavior only
 * guarantees the box itself, which left labels cut off at the screen edge
 * on the lower rows. Callers that want different focus behavior (the Apps
 * row scrolls the page all the way to the top instead) pass [onFocus],
 * which replaces the bring-into-view request.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    width: Dp = 260.dp,
    height: Dp = 146.dp,
    cornerRadius: Dp = 16.dp,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.12f else 1f, label = "tvTileScale")
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val bringIntoViewRequester = rememberBringIntoViewRequester()
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .scale(scale)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .clip(shape)
                .background(Color(0xFF141414))
                .border(width = if (isFocused) 4.dp else 0.dp, color = Color.White, shape = shape)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        if (onFocus != null) {
                            onFocus()
                        } else {
                            scope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    }
                }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
            content = content,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
        )
    }
}

/** [TvTile] content: a [Drawable] (a loaded app banner/icon) scaled to fit the tile. */
@Composable
fun TvTileDrawableArtwork(drawable: Drawable, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = rememberDrawableBitmap(drawable)
    // fillMaxSize is load-bearing: without it the Image sizes to the
    // bitmap's intrinsic pixels (a 320x180 banner draws as a small rect
    // in the middle of the tile — the Jellyfin tile before v15). Fit then
    // scales it to the box instead of drawing it at native size.
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .padding(if (bitmap.width == bitmap.height) 20.dp else 0.dp),
    )
}

/** [TvTile] content: a bundled drawable resource (the Weave/movie-wheel tile art) scaled to fit the tile. */
@Composable
fun TvTileResourceArtwork(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.padding(20.dp),
    )
}

@Composable
private fun rememberDrawableBitmap(drawable: Drawable): ImageBitmap = remember(drawable) {
    drawable.toBitmap(
        width = drawable.intrinsicWidth.coerceAtLeast(1),
        height = drawable.intrinsicHeight.coerceAtLeast(1),
    ).asImageBitmap()
}

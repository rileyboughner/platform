package dev.rileyboughner.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * favorite rows with generous spacing (no icons, no dividers). There's no
 * separate "All apps" row — swipe down already opens [AllAppsScreen] in
 * place (not a separate pager page), so the button was redundant. There's
 * also no "Settings" row — tapping the page-dot strip at the bottom of
 * [dev.rileyboughner.launcher.ui.LauncherPagerScreen] already opens
 * Settings, so a second entry point here was redundant too (Riley's phone
 * feedback on v8).
 *
 * Gestures on this page (Riley's phone feedback):
 * - Swipe down opens [AllAppsScreen] with the search field focused/keyboard
 *   open, so he can start typing immediately.
 * - Swipe up opens the browser.
 * - Double-tap on empty background (not on a row) opens Messages.
 */
@Composable
fun AppsPageScreen(
    appListRepository: AppListRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allApps = remember { appListRepository.getLaunchableApps() }
    val favorites = remember(allApps) { FavoritesRepository.resolveFavorites(allApps) }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    var autoFocusSearch by rememberSaveable { mutableStateOf(false) }

    if (showAllApps) {
        AllAppsScreen(
            apps = allApps,
            onAppClick = { app -> appListRepository.launchApp(app.packageName) },
            onBack = {
                showAllApps = false
                autoFocusSearch = false
            },
            autoFocusSearch = autoFocusSearch,
            modifier = modifier.background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val scrollState = rememberScrollState()
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    var pullDownPx by remember { mutableStateOf(0f) }
    var pulledDownThisGesture by remember { mutableStateOf(false) }
    var pullUpPx by remember { mutableStateOf(0f) }
    var pulledUpThisGesture by remember { mutableStateOf(false) }

    // Swipe down/up only engage once the vertical drag is already at the
    // scroll boundary in that direction, and fire on distance-crossed
    // *during* the drag rather than on release velocity — same technique
    // (and threshold) as CalendarScreen's pull-to-toggle connection, which
    // is what actually proved reliable on Riley's phone (a deliberate pull
    // has near-zero release velocity, and onPreFling never fired for it).
    // Gating on the boundary — rather than swallowing every vertical drag —
    // is also what keeps this from interfering with the HorizontalPager's
    // independent horizontal swipes, and with ordinary scrolling if the
    // favorites list is ever long enough to overflow the screen.
    val swipeConnection = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) {
                    pullDownPx = 0f
                    pulledDownThisGesture = false
                    pullUpPx = 0f
                    pulledUpThisGesture = false
                    return Offset.Zero
                }
                val atTop = scrollState.value == 0
                val atBottom = scrollState.value >= scrollState.maxValue

                if (available.y > 0f && atTop) {
                    pullUpPx = 0f
                    pulledUpThisGesture = false
                    pullDownPx += available.y
                    if (!pulledDownThisGesture && pullDownPx > swipeThresholdPx) {
                        pulledDownThisGesture = true
                        autoFocusSearch = true
                        showAllApps = true
                    }
                    return Offset(0f, available.y)
                }
                if (available.y < 0f && atBottom) {
                    pullDownPx = 0f
                    pulledDownThisGesture = false
                    pullUpPx += available.y
                    if (!pulledUpThisGesture && pullUpPx < -swipeThresholdPx) {
                        pulledUpThisGesture = true
                        openBrowser(context)
                    }
                    return Offset(0f, available.y)
                }
                pullDownPx = 0f
                pulledDownThisGesture = false
                pullUpPx = 0f
                pulledUpThisGesture = false
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pullDownPx = 0f
                pulledDownThisGesture = false
                pullUpPx = 0f
                pulledUpThisGesture = false
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Double-tap on empty background opens Messages. Rows underneath
            // (FavoriteRow's own `clickable`) consume their tap first, so a
            // double-tap landing on a row never reaches this detector.
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { openMessages(context) })
            },
    ) {
        WallpaperBackground(modifier = Modifier.fillMaxSize())

        // Subtle dark scrim over the wallpaper so white text stays legible.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .nestedScroll(swipeConnection)
                .verticalScroll(scrollState)
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

/**
 * Plain text row: left-aligned, no icon, no divider, generous padding for
 * the Apps page. Font size is bumped up from the bare `headlineSmall`
 * (24sp) to 30sp per Riley's feedback that the main page text was too
 * small — scoped to just this row style, not the shared Material typography,
 * so it doesn't also grow the All-apps list or Settings screen rows.
 */
@Composable
private fun FavoriteRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 38.sp),
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/**
 * Fallback path only: paints the system wallpaper behind the page by
 * reading [WallpaperManager]'s drawable directly into an ImageView. The
 * primary mechanism is MainActivity's window itself showing the real
 * system wallpaper (Theme.Launcher.Wallpaper, android:windowShowWallpaper +
 * windowIsTranslucent) — that works even for live wallpapers and is what
 * actually renders on Riley's Pixel, where this drawable read returns null.
 * When it does return null, this composable draws nothing, leaving the
 * transparent area for the window's own wallpaper to show through; when it
 * succeeds, it just redundantly paints the same image on top.
 */
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

/** Swipe-up target: Vivaldi, falling back to whatever the system default browser is. */
private fun openBrowser(context: Context) {
    val vivaldiIntent = context.packageManager.getLaunchIntentForPackage(VIVALDI_PACKAGE)
    if (vivaldiIntent != null) {
        context.startActivity(vivaldiIntent)
        return
    }
    val defaultBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (defaultBrowserIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(defaultBrowserIntent)
    } else {
        Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
    }
}

/** Double-tap-background target: Messages, falling back to the stock Android Messaging app. */
private fun openMessages(context: Context) {
    val messagingIntent = context.packageManager.getLaunchIntentForPackage(MESSAGES_PACKAGE)
        ?: context.packageManager.getLaunchIntentForPackage(MESSAGES_FALLBACK_PACKAGE)
    if (messagingIntent != null) {
        context.startActivity(messagingIntent)
    } else {
        Toast.makeText(context, "No messaging app found", Toast.LENGTH_SHORT).show()
    }
}

private const val VIVALDI_PACKAGE = "com.vivaldi.browser"
private const val MESSAGES_PACKAGE = "com.google.android.apps.messaging"
private const val MESSAGES_FALLBACK_PACKAGE = "com.android.mms"

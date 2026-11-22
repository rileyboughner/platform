package dev.rileyboughner.launcher.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.rileyboughner.launcher.calendar.CalendarEvent
import dev.rileyboughner.launcher.calendar.CalendarRepository
import dev.rileyboughner.launcher.calendar.eventDay
import dev.rileyboughner.launcher.calendar.formatDayLabel
import dev.rileyboughner.launcher.calendar.formatEventTime
import java.time.LocalDate

/**
 * The Calendar page (right of Apps in the pager). Requests `READ_CALENDAR`
 * the first time this page is shown; shows a plain explanation if denied.
 * Read-only — no event creation or editing anywhere here; tapping an event
 * hands off to the system calendar app instead, and swiping down from the
 * top of the list opens the calendar app's main view. The "Today only" /
 * "All events" filter is owned entirely by the tappable header chip now —
 * see the pull-gesture comment below for why it no longer shares the pull.
 */
@Composable
fun CalendarScreen(calendarRepository: CalendarRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionWasRequested by rememberSaveable { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Swipe-down-to-open-calendar-app state. Defaults to "Today only" for
    // the filter, which is now owned solely by the tappable header chip
    // (below) — the pull gesture at the top of the list used to toggle that
    // same filter, but Riley wants pulling down here to open the calendar
    // app instead, so the two gestures can no longer share this physical
    // motion. Swipe-down wins; the chip is the only way to change the
    // filter now.
    //
    // The open fires as soon as the pull *distance* crosses the threshold,
    // during the drag itself (in onPreScroll) rather than waiting for
    // onPreFling at release. On Riley's phone the old onPreFling-triggered
    // version never fired: a deliberate, unhurried pull-down has near-zero
    // release velocity, and a drag that starts near the top edge of the
    // screen can get cut short by an ACTION_CANCEL (status bar / gesture-nav
    // edge swipes compete for that touch) before Compose ever dispatches a
    // fling for it. Triggering on distance-crossed means it has already
    // happened the moment the pull "catches", before any of that can
    // intervene.
    var showAllEvents by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var pullDistancePx by remember { mutableStateOf(0f) }
    var openedThisGesture by remember { mutableStateOf(false) }
    val pullThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }

    val pullToOpenConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (source != NestedScrollSource.Drag || !atTop || available.y <= 0f) {
                    pullDistancePx = 0f
                    openedThisGesture = false
                    return Offset.Zero
                }
                pullDistancePx += available.y
                if (!openedThisGesture && pullDistancePx > pullThresholdPx) {
                    openedThisGesture = true
                    openCalendarApp(context)
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pullDistancePx = 0f
                openedThisGesture = false
                return Velocity.Zero
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionWasRequested = true
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            isLoading = true
            events = calendarRepository.queryUpcomingEvents()
            isLoading = false
        }
    }

    val today = LocalDate.now()
    val visibleEvents = if (showAllEvents) events else events.filter { eventDay(it).isEqual(today) }

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        FilterModeHeader(showAllEvents, onToggle = { showAllEvents = !showAllEvents })
        when {
            !permissionGranted && permissionWasRequested -> EmptyCalendarState(
                "Calendar access was denied. Grant READ_CALENDAR in system Settings to see upcoming events here.",
            )
            !permissionGranted -> EmptyCalendarState("Waiting for calendar permission…")
            isLoading && events.isEmpty() -> EmptyCalendarState("Loading…")
            visibleEvents.isEmpty() && showAllEvents -> EmptyCalendarState("No upcoming events.")
            visibleEvents.isEmpty() -> EmptyCalendarState("No events today.")
            else -> {
                val grouped = groupByDay(visibleEvents)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.nestedScroll(pullToOpenConnection),
                ) {
                    grouped.forEach { (day, dayEvents) ->
                        item { DayHeader(day) }
                        items(dayEvents, key = { it.id to it.startMillis }) { event ->
                            EventRow(event, onClick = { openCalendarEvent(context, event) })
                            HairlineDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tappable chip showing the current filter mode. This is the guaranteed
 * fallback for toggling "Today only" / "All events" — it always works even
 * if the pull-down gesture on the list below is finicky on a given device.
 */
@Composable
private fun FilterModeHeader(showAllEvents: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = if (showAllEvents) "All events" else "Today only",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DayHeader(day: LocalDate, modifier: Modifier = Modifier) {
    Text(
        text = formatDayLabel(day),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Small dot in the event's calendar color (synced from Nextcloud via
        // DAVx5); falls back to the theme outline when the provider has none.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    event.calendarColor?.let { Color(it) } ?: MaterialTheme.colorScheme.outline,
                ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formatEventTime(event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCalendarState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * Opens the event in the system calendar app via `ACTION_VIEW` on the
 * event's content URI. Attempts `startActivity` directly and catches
 * [ActivityNotFoundException] rather than gating on `resolveActivity` first
 * — on Riley's phone the `resolveActivity` check was silently coming back
 * null (package-visibility query filtering, even with the calendar app
 * installed), which meant the tap looked like it did nothing at all. Falls
 * back to the calendar app's main view at the event's start time if even
 * that fails, instead of silently doing nothing.
 */
private fun openCalendarEvent(context: Context, event: CalendarEvent) {
    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
    val eventIntent = Intent(Intent.ACTION_VIEW, eventUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(eventIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // Fall through to the main calendar view below.
    }

    val fallbackUri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .appendPath(event.startMillis.toString())
        .build()
    openCalendarAppAt(context, fallbackUri)
}

/** Swipe-down target: the calendar app's main view "now" — see [pullToOpenConnection] above. */
private fun openCalendarApp(context: Context) {
    openCalendarAppAt(context, CalendarContract.CONTENT_URI)
}

private fun openCalendarAppAt(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
    }
}

private fun groupByDay(events: List<CalendarEvent>): List<Pair<LocalDate, List<CalendarEvent>>> =
    events
        .groupBy(::eventDay)
        .toSortedMap()
        .map { (day, dayEvents) -> day to dayEvents }

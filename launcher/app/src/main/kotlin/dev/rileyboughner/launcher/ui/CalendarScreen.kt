package dev.rileyboughner.launcher.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Calendar page (right of Apps in the pager). Requests `READ_CALENDAR`
 * the first time this page is shown; shows a plain explanation if denied.
 * Read-only — no event creation or editing anywhere here; tapping an event
 * hands off to the system calendar app instead.
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

    // Pull-down-to-toggle filter state. Defaults to "Today only". The pull
    // only takes effect via the nested-scroll connection below, which lets
    // it through only once the event list is already scrolled to its top —
    // otherwise this is indistinguishable from a normal scroll-up gesture.
    //
    // The toggle fires as soon as the pull *distance* crosses the threshold,
    // during the drag itself (in onPreScroll) rather than waiting for
    // onPreFling at release. On Riley's phone the old onPreFling-triggered
    // version never fired: a deliberate, unhurried pull-down has near-zero
    // release velocity, and a drag that starts near the top edge of the
    // screen can get cut short by an ACTION_CANCEL (status bar / gesture-nav
    // edge swipes compete for that touch) before Compose ever dispatches a
    // fling for it. Triggering on distance-crossed means the toggle has
    // already happened the moment the pull "catches", before any of that
    // can intervene. A tappable header chip (below) is the guaranteed
    // fallback if the HorizontalPager ever wins the vertical drag outright.
    var showAllEvents by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var pullDistancePx by remember { mutableStateOf(0f) }
    var toggledThisGesture by remember { mutableStateOf(false) }
    val pullThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }

    val pullToggleConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (source != NestedScrollSource.Drag || !atTop || available.y <= 0f) {
                    pullDistancePx = 0f
                    toggledThisGesture = false
                    return Offset.Zero
                }
                pullDistancePx += available.y
                if (!toggledThisGesture && pullDistancePx > pullThresholdPx) {
                    showAllEvents = !showAllEvents
                    toggledThisGesture = true
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pullDistancePx = 0f
                toggledThisGesture = false
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

    Column(modifier = modifier.fillMaxSize()) {
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
                    modifier = Modifier.nestedScroll(pullToggleConnection),
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
        text = dayLabel(day),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = timeLabel(event),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
 * event's content URI. If nothing can resolve that (no calendar app, or the
 * URI is otherwise unresolvable), falls back to opening the calendar app's
 * main view at the event's start time instead of silently doing nothing.
 */
private fun openCalendarEvent(context: Context, event: CalendarEvent) {
    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
    val eventIntent = Intent(Intent.ACTION_VIEW, eventUri)
    if (eventIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(eventIntent)
        return
    }

    val fallbackUri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .appendPath(event.startMillis.toString())
        .build()
    val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
    if (fallbackIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(fallbackIntent)
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

// All-day instances are stored by CalendarContract in UTC regardless of the
// device's zone, so they need UTC to land on the calendar date Riley expects
// instead of shifting a day near midnight.
private fun eventZone(event: CalendarEvent): ZoneId = if (event.allDay) ZoneOffset.UTC else ZoneId.systemDefault()

private fun eventDay(event: CalendarEvent): LocalDate =
    Instant.ofEpochMilli(event.startMillis).atZone(eventZone(event)).toLocalDate()

private fun groupByDay(events: List<CalendarEvent>): List<Pair<LocalDate, List<CalendarEvent>>> =
    events
        .groupBy(::eventDay)
        .toSortedMap()
        .map { (day, dayEvents) -> day to dayEvents }

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when {
        day.isEqual(today) -> "Today"
        day.isEqual(today.plusDays(1)) -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

private fun timeLabel(event: CalendarEvent): String {
    if (event.allDay) return "All day"
    val start = Instant.ofEpochMilli(event.startMillis).atZone(ZoneId.systemDefault())
    val end = Instant.ofEpochMilli(event.endMillis).atZone(ZoneId.systemDefault())
    return "${timeFormatter.format(start)} – ${timeFormatter.format(end)}"
}

package dev.rileyboughner.launcher.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
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
 * Read-only — no event creation or editing anywhere here.
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

    Column(modifier = modifier.fillMaxSize()) {
        when {
            !permissionGranted && permissionWasRequested -> EmptyCalendarState(
                "Calendar access was denied. Grant READ_CALENDAR in system Settings to see upcoming events here.",
            )
            !permissionGranted -> EmptyCalendarState("Waiting for calendar permission…")
            isLoading && events.isEmpty() -> EmptyCalendarState("Loading…")
            events.isEmpty() -> EmptyCalendarState("No upcoming events.")
            else -> {
                val grouped = groupByDay(events)
                LazyColumn {
                    grouped.forEach { (day, dayEvents) ->
                        item { DayHeader(day) }
                        items(dayEvents, key = { it.id to it.startMillis }) { event ->
                            EventRow(event)
                            HairlineDivider()
                        }
                    }
                }
            }
        }
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
private fun EventRow(event: CalendarEvent, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

// All-day instances are stored by CalendarContract in UTC regardless of the
// device's zone, so they need UTC to land on the calendar date Riley expects
// instead of shifting a day near midnight.
private fun eventZone(event: CalendarEvent): ZoneId = if (event.allDay) ZoneOffset.UTC else ZoneId.systemDefault()

private fun groupByDay(events: List<CalendarEvent>): List<Pair<LocalDate, List<CalendarEvent>>> =
    events
        .groupBy { Instant.ofEpochMilli(it.startMillis).atZone(eventZone(it)).toLocalDate() }
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

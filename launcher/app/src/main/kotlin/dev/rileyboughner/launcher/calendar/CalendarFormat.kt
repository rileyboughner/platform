package dev.rileyboughner.launcher.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

// All-day instances are stored by CalendarContract in UTC regardless of the
// device's zone, so they need UTC to land on the calendar date Riley expects
// instead of shifting a day near midnight.
fun eventZone(event: CalendarEvent): ZoneId = if (event.allDay) ZoneOffset.UTC else ZoneId.systemDefault()

fun eventDay(event: CalendarEvent): LocalDate =
    Instant.ofEpochMilli(event.startMillis).atZone(eventZone(event)).toLocalDate()

/** "Today" / "Tomorrow" / "EEE, MMM d" — the day label the Calendar page uses. */
fun formatDayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when {
        day.isEqual(today) -> "Today"
        day.isEqual(today.plusDays(1)) -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

/** The time text the Calendar page shows for an event ("All day" or "h:mm a – h:mm a"). */
fun formatEventTime(event: CalendarEvent): String {
    if (event.allDay) return "All day"
    val start = Instant.ofEpochMilli(event.startMillis).atZone(ZoneId.systemDefault())
    val end = Instant.ofEpochMilli(event.endMillis).atZone(ZoneId.systemDefault())
    return "${timeFormatter.format(start)} – ${timeFormatter.format(end)}"
}

package dev.rileyboughner.launcher.calendar

/**
 * One calendar instance in the upcoming window — read-only, no editing.
 * [calendarColor] is the ARGB color of the calendar the event belongs to
 * (from [android.provider.CalendarContract.Calendars.CALENDAR_COLOR], which
 * DAVx5 syncs from Nextcloud), or null when the provider has none.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarColor: Int? = null,
)

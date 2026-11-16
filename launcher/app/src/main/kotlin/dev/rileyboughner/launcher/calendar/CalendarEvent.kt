package dev.rileyboughner.launcher.calendar

/** One calendar instance in the upcoming window — read-only, no editing. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

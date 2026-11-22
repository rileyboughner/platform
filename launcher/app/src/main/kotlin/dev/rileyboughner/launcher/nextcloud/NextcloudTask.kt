package dev.rileyboughner.launcher.nextcloud

import java.time.LocalDate

/**
 * One Nextcloud Tasks (CalDAV VTODO) task, trimmed to the fields the Todo
 * page actually shows. [status] is the raw VTODO STATUS value
 * ("NEEDS-ACTION", "COMPLETED", ...); the Todo page only ever displays
 * non-completed tasks. [isRecurring] means the VTODO carries an RRULE —
 * the task is shown as-is and its recurrences are not expanded.
 */
data class NextcloudTask(
    val uid: String,
    /**
     * The CalDAV object path for this task (e.g.
     * `/remote.php/dav/calendars/rileyboughner/Tasks/abc.ics`), as
     * returned by the REPORT response — used to PUT the modified VTODO
     * back when the task is completed.
     */
    val href: String,
    val title: String,
    val due: LocalDate?,
    val description: String?,
    val isRecurring: Boolean,
    val status: String,
    /** The full VCALENDAR text as fetched, kept so completing the task can PUT the modified copy back. */
    val rawIcs: String,
)

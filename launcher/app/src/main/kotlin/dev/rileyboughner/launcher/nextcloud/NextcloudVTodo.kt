package dev.rileyboughner.launcher.nextcloud

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Parses one VTODO's VCALENDAR text into a [NextcloudTask], or null when
 * the text has no usable VTODO (missing UID or SUMMARY). Only the fields
 * the Todo page uses are read: SUMMARY, DUE, DESCRIPTION, STATUS, RRULE.
 * Recurrences are not expanded — a VTODO with an RRULE is returned as a
 * single task with [NextcloudTask.isRecurring] set.
 */
internal fun parseVTodo(calendarData: String, href: String): NextcloudTask? {
    // Unfold: a line starting with a space/tab continues the previous line.
    val unfolded = calendarData
        .replace(Regex("\r\n[ \t]"), "")
        .replace(Regex("\n[ \t]"), "")
    val vtodoBlock = unfolded
        .substringAfter("BEGIN:VTODO", "")
        .substringBefore("END:VTODO", "")
        .takeIf { it.isNotBlank() } ?: return null
    val props = mutableMapOf<String, String>()
    var hasRrule = false
    for (rawLine in vtodoBlock.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        val colon = line.indexOf(':')
        if (colon < 0) continue
        val name = line.substring(0, colon).substringBefore(';').uppercase()
        val value = line.substring(colon + 1)
        if (name == "RRULE") hasRrule = true
        props[name] = value
    }
    val uid = props["UID"] ?: return null
    val title = props["SUMMARY"]?.takeIf { it.isNotBlank() } ?: return null
    return NextcloudTask(
        uid = uid,
        href = href,
        title = title,
        due = props["DUE"]?.let { parseDueDate(it) },
        description = props["DESCRIPTION"]?.takeIf { it.isNotBlank() },
        isRecurring = hasRrule,
        status = props["STATUS"] ?: "NEEDS-ACTION",
        rawIcs = calendarData,
    )
}

/**
 * DUE is either a DATE (YYYYMMDD) or a DATETIME (YYYYMMDDTHHMMSS, with an
 * optional TZID or Z suffix); the Todo page only shows the date either
 * way, so the first eight digits are the date. Returns null when the
 * value isn't a parseable date.
 */
private fun parseDueDate(value: String): LocalDate? {
    val datePart = value.take(8)
    if (datePart.length != 8 || !datePart.all { it.isDigit() }) return null
    return runCatching {
        LocalDate.of(
            datePart.substring(0, 4).toInt(),
            datePart.substring(4, 6).toInt(),
            datePart.substring(6, 8).toInt(),
        )
    }.getOrNull()
}

private val utcTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/**
 * Returns a copy of [task]'s .ics with the VTODO marked completed:
 * STATUS becomes COMPLETED, a COMPLETED timestamp is added, and
 * LAST-MODIFIED / DTSTAMP are refreshed to now. Every other VTODO
 * property (UID, SUMMARY, DUE, RRULE, ...) is left untouched, and folded
 * continuation lines are passed through unchanged.
 */
internal fun markCompletedIcs(task: NextcloudTask): String {
    val now = utcTimestampFormatter.format(Instant.now())
    val out = mutableListOf<String>()
    var inVtodo = false
    var hasStatus = false
    var hasCompleted = false
    var hasLastModified = false
    for (line in task.rawIcs.lines()) {
        // A continuation line (leading space/tab) belongs to the previous
        // property — never treat it as a property of its own.
        if (line.startsWith(" ") || line.startsWith("\t")) {
            out.add(line)
            continue
        }
        val trimmed = line.trim()
        when {
            trimmed == "BEGIN:VTODO" -> {
                inVtodo = true
                out.add(line)
            }
            trimmed == "END:VTODO" -> {
                // Insert any missing completion properties just before END:VTODO.
                if (!hasStatus) out.add("STATUS:COMPLETED")
                if (!hasCompleted) out.add("COMPLETED:$now")
                if (!hasLastModified) out.add("LAST-MODIFIED:$now")
                inVtodo = false
                out.add(line)
            }
            inVtodo && trimmed.startsWith("STATUS:") -> {
                hasStatus = true
                out.add("STATUS:COMPLETED")
            }
            inVtodo && trimmed.startsWith("COMPLETED:") -> {
                hasCompleted = true
                out.add("COMPLETED:$now")
            }
            inVtodo && trimmed.startsWith("LAST-MODIFIED:") -> {
                hasLastModified = true
                out.add("LAST-MODIFIED:$now")
            }
            inVtodo && trimmed.startsWith("DTSTAMP:") -> out.add("DTSTAMP:$now")
            else -> out.add(line)
        }
    }
    return out.joinToString("\r\n")
}

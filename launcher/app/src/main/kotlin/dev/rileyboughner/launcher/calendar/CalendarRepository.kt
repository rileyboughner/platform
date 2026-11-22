package dev.rileyboughner.launcher.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Read-only query against the device calendar via [CalendarContract]. Caller
 * is responsible for holding `READ_CALENDAR` before calling — see
 * [dev.rileyboughner.launcher.ui.CalendarScreen].
 */
class CalendarRepository(private val context: Context) {

    suspend fun queryUpcomingEvents(windowDays: Int = 14): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.DAYS.toMillis(windowDays.toLong())

        // Calendar colors, keyed by calendar id. DAVx5 syncs the Nextcloud
        // color into CALENDAR_COLOR, so this is where the color-coding comes
        // from. Queried once per call (only a handful of calendars exist).
        val colorByCalendarId = mutableMapOf<Long, Int>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_COLOR,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            while (cursor.moveToNext()) {
                if (!cursor.isNull(colorCol)) {
                    colorByCalendarId[cursor.getLong(calIdCol)] = cursor.getInt(colorCol)
                }
            }
        }

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { builder ->
                ContentUris.appendId(builder, now)
                ContentUris.appendId(builder, end)
            }
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
        )

        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            while (cursor.moveToNext()) {
                events += CalendarEvent(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: "(No title)",
                    startMillis = cursor.getLong(beginCol),
                    endMillis = cursor.getLong(endCol),
                    allDay = cursor.getInt(allDayCol) != 0,
                    calendarColor = colorByCalendarId[cursor.getLong(calIdCol)],
                )
            }
        }
        events
    }

    /**
     * The next upcoming event (the first in the 14-day window, which starts
     * at now and is sorted by start ascending — so this is today's next
     * event when there is one, else the soonest future one), or null when
     * there is nothing upcoming.
     */
    suspend fun queryNextUpcomingEvent(): CalendarEvent? =
        queryUpcomingEvents().firstOrNull()
}

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
            while (cursor.moveToNext()) {
                events += CalendarEvent(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: "(No title)",
                    startMillis = cursor.getLong(beginCol),
                    endMillis = cursor.getLong(endCol),
                    allDay = cursor.getInt(allDayCol) != 0,
                )
            }
        }
        events
    }
}

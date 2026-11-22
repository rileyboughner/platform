package dev.rileyboughner.launcher.nextcloud

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Minimal CalDAV client for the Nextcloud Tasks calendar — just enough for
 * the Todo page: a calendar-query REPORT that returns every VTODO's
 * calendar-data in one round trip, and a PUT to write a modified VTODO
 * back (completing a task). Blocking I/O — callers (see
 * [NextcloudTasksRepository]) run this on [kotlinx.coroutines.Dispatchers.IO].
 *
 * Credentials are only ever placed in the Authorization header, never
 * logged, and never included in any exception message thrown from here.
 */
internal object NextcloudCalDav {

    private const val BASE_URL = "https://cloud.rileyboughner.dev"
    private const val TASKS_CALENDAR_PATH = "/remote.php/dav/calendars/rileyboughner/Tasks/"
    private const val TIMEOUT_MS = 10_000

    /** The full CalDAV collection URL for the Nextcloud Tasks calendar. */
    fun tasksCalendarUrl(): String = BASE_URL + TASKS_CALENDAR_PATH

    /**
     * Calendar-query REPORT for every VTODO in the Tasks calendar.
     * Returns the raw multistatus XML body.
     */
    fun reportTasks(username: String, appPassword: String): String {
        val body = """<?xml version="1.0" encoding="utf-8" ?>
<C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:prop>
    <D:getetag />
    <C:calendar-data />
  </D:prop>
  <C:filter>
    <C:comp-filter name="VCALENDAR">
      <C:comp-filter name="VTODO" />
    </C:comp-filter>
  </C:filter>
</C:calendar-query>"""
        val connection = openConnection(tasksCalendarUrl(), username, appPassword, method = "REPORT")
        connection.setRequestProperty("Depth", "1")
        connection.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
        connection.doOutput = true
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            checkSuccess(connection)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * PUT a (modified) VTODO's .ics back to its object URL. [href] is the
     * object path from the REPORT response (e.g.
     * `/remote.php/dav/calendars/rileyboughner/Tasks/abc.ics`); a full URL
     * is accepted as-is.
     */
    fun putTask(username: String, appPassword: String, href: String, ics: String) {
        val url = if (href.startsWith("http://") || href.startsWith("https://")) href else BASE_URL + href
        val connection = openConnection(url, username, appPassword, method = "PUT")
        connection.setRequestProperty("Content-Type", "text/calendar; charset=utf-8")
        connection.doOutput = true
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(ics) }
            checkSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extracts every (object href, calendar-data) pair from a REPORT
     * multistatus response, in document order. A response with no
     * calendar-data (or no href) is skipped.
     */
    fun extractCalendarData(multistatusXml: String): List<Pair<String, String>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(multistatusXml))
        val out = mutableListOf<Pair<String, String>>()
        var inResponse = false
        var href: String? = null
        var ics: String? = null
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "response" -> {
                            inResponse = true
                            href = null
                            ics = null
                        }
                        "href" -> if (inResponse && href == null) href = parser.nextText()
                        "calendar-data" -> if (inResponse && ics == null) ics = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "response") {
                        if (href != null && ics != null) out.add(href to ics)
                        inResponse = false
                    }
                }
            }
            eventType = parser.next()
        }
        return out
    }

    private fun openConnection(url: String, username: String, appPassword: String, method: String): HttpsURLConnection {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.requestMethod = method
        val credentials = Base64.encodeToString(
            "$username:$appPassword".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        connection.setRequestProperty("Authorization", "Basic $credentials")
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        return connection
    }

    private fun checkSuccess(connection: HttpsURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IOException("Nextcloud CalDAV request failed with HTTP $code")
        }
    }
}

package dev.rileyboughner.launcher.dashboard

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Raw client for the homelab web dashboard's public `/api/all` route (no
 * auth — see `dashboard/server.py`). Blocking I/O — callers (see
 * [DashboardRepository]) run this on [kotlinx.coroutines.Dispatchers.IO].
 * Only the "server" section of the response is parsed; the rest (seerr,
 * homeassistant, links) belongs to the web dashboard, not this launcher page.
 */
internal object DashboardApi {

    private const val METRICS_URL = "https://dashboard.rileyboughner.dev/api/all"
    private const val TIMEOUT_MS = 10_000

    fun fetchServerMetrics(): ServerMetrics {
        val connection = URL(METRICS_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val body = try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Dashboard request failed with HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val server = JSONObject(body).optJSONObject("server") ?: JSONObject()
        val host = server.optJSONObject("host")
        val disk = server.optJSONObject("disk")
        val containers = server.optJSONArray("containers")
        val containersTotal = containers?.length()
        val containersRunning = containers?.let { array ->
            (0 until array.length()).count { array.getJSONObject(it).optString("state") == "running" }
        }

        return ServerMetrics(
            cpuPercent = host.optDoubleOrNull("cpu_percent"),
            memoryUsedBytes = host.optLongOrNull("memory_used_bytes"),
            memoryTotalBytes = host.optLongOrNull("memory_total_bytes"),
            diskUsedBytes = disk.optLongOrNull("used"),
            diskTotalBytes = disk.optLongOrNull("total"),
            uptimeSeconds = server.optDoubleOrNull("uptime_seconds"),
            containersRunning = containersRunning,
            containersTotal = containersTotal,
        )
    }

    private fun JSONObject?.optDoubleOrNull(key: String): Double? {
        val value = this?.optDouble(key, Double.NaN) ?: return null
        return value.takeIf { !it.isNaN() }
    }

    private fun JSONObject?.optLongOrNull(key: String): Long? {
        val value = this?.optLong(key, Long.MIN_VALUE) ?: return null
        return value.takeIf { it != Long.MIN_VALUE }
    }
}

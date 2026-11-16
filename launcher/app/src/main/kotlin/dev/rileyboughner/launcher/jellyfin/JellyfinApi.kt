package dev.rileyboughner.launcher.jellyfin

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Raw Jellyfin API client. Blocking I/O — callers (see [JellyfinRepository])
 * run this on [kotlinx.coroutines.Dispatchers.IO].
 *
 * The API key is only ever placed in the `api_key` query parameter Jellyfin
 * itself expects, never logged, and never included in any exception message
 * thrown from here.
 */
internal object JellyfinApi {

    private const val TIMEOUT_MS = 10_000
    private const val RESUME_LIMIT = 12

    fun fetchResumeItems(serverUrl: String, apiKey: String): List<JellyfinResumeItem> {
        val userId = fetchFirstUserId(serverUrl, apiKey) ?: return emptyList()
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val url = "$serverUrl/Users/$userId/Items/Resume" +
            "?api_key=$encodedKey&Limit=$RESUME_LIMIT&Fields=PrimaryImageAspectRatio&MediaTypes=Video"
        val body = get(url)
        val items = JSONObject(body).optJSONArray("Items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                add(parseResumeItem(item, serverUrl, apiKey))
            }
        }
    }

    private fun fetchFirstUserId(serverUrl: String, apiKey: String): String? {
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val body = get("$serverUrl/Users?api_key=$encodedKey")
        val users = JSONArray(body)
        if (users.length() == 0) return null
        return users.getJSONObject(0).optString("Id").takeIf { it.isNotBlank() }
    }

    private fun parseResumeItem(item: JSONObject, serverUrl: String, apiKey: String): JellyfinResumeItem {
        val id = item.getString("Id")
        val title = item.optString("Name", "Untitled")
        val runtimeTicks = item.optLong("RunTimeTicks", 0L)
        val positionTicks = item.optJSONObject("UserData")?.optLong("PlaybackPositionTicks", 0L) ?: 0L
        val progress = if (runtimeTicks > 0) (positionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f) else 0f
        val hasPrimaryImage = item.optJSONObject("ImageTags")?.has("Primary") == true
        val posterUrl = if (hasPrimaryImage) {
            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            "$serverUrl/Items/$id/Images/Primary?api_key=$encodedKey&maxHeight=320"
        } else {
            null
        }
        return JellyfinResumeItem(id = id, title = title, posterUrl = posterUrl, progressFraction = progress)
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Jellyfin request failed with HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

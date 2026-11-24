package dev.rileyboughner.tvlauncher.jellyfin

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
 *
 * Adapted from the phone launcher's JellyfinApi: same endpoints, but the
 * resume list is resolved for the shared `house` account by name (not the
 * first user in the list), and each item also carries a subtitle (series +
 * episode for TV) and remaining-time text for the TV row.
 */
internal object JellyfinApi {

    private const val TIMEOUT_MS = 10_000
    private const val RESUME_LIMIT = 12
    private const val HOUSE_USER_NAME = "house"
    private const val TICKS_PER_MINUTE = 10_000_000L * 60L

    fun fetchResumeItems(serverUrl: String, apiKey: String): List<JellyfinResumeItem> {
        val userId = fetchHouseUserId(serverUrl, apiKey) ?: return emptyList()
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val url = "$serverUrl/Users/$userId/Items/Resume" +
            "?api_key=<redacted>&Limit=$RESUME_LIMIT&Fields=PrimaryImageAspectRatio&MediaTypes=Video"
        val body = get(url)
        val items = JSONObject(body).optJSONArray("Items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                add(parseResumeItem(item, serverUrl, apiKey))
            }
        }
    }

    /**
     * The user id of the shared house account. An API key minted for a
     * non-admin user only sees itself in the user list, so prefer the user
     * named "house" and fall back to the first (only) user.
     */
    private fun fetchHouseUserId(serverUrl: String, apiKey: String): String? {
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val body = get("$serverUrl/Users?api_key=<redacted>")
        val users = JSONArray(body)
        if (users.length() == 0) return null
        for (i in 0 until users.length()) {
            val user = users.getJSONObject(i)
            if (user.optString("Name", "").equals(HOUSE_USER_NAME, ignoreCase = true)) {
                return user.optString("Id").takeIf { it.isNotBlank() }
            }
        }
        return users.getJSONObject(0).optString("Id").takeIf { it.isNotBlank() }
    }

    private fun parseResumeItem(item: JSONObject, serverUrl: String, apiKey: String): JellyfinResumeItem {
        val id = item.getString("Id")
        val type = item.optString("Type", "")
        val name = item.optString("Name", "Untitled")
        val seriesName = item.optString("SeriesName", "").takeIf { it.isNotBlank() }
        val season = item.optInt("ParentIndexNumber", -1).takeIf { it >= 0 }
        val episode = item.optInt("IndexNumber", -1).takeIf { it >= 0 }
        val title = if (type == "Episode" && seriesName != null) seriesName else name
        val subtitle = if (type == "Episode") {
            val episodeLabel = listOfNotNull(
                season?.let { "S$it" },
                episode?.let { "E$it" },
            ).joinToString(" ").takeIf { it.isNotBlank() }
            listOfNotNull(episodeLabel, name).joinToString(" · ").takeIf { it.isNotBlank() }
        } else {
            null
        }
        val runtimeTicks = item.optLong("RunTimeTicks", 0L)
        val positionTicks = item.optJSONObject("UserData")?.optLong("PlaybackPositionTicks", 0L) ?: 0L
        val progress = if (runtimeTicks > 0) (positionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f) else 0f
        val remainingText = if (runtimeTicks > 0 && positionTicks in 0 until runtimeTicks) {
            formatRemaining(runtimeTicks - positionTicks)
        } else {
            null
        }
        val hasPrimaryImage = item.optJSONObject("ImageTags")?.has("Primary") == true
        val posterUrl = if (hasPrimaryImage) {
            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            "$serverUrl/Items/$id/Images/Primary?api_key=<redacted>&maxHeight=360"
        } else {
            null
        }
        return JellyfinResumeItem(
            id = id,
            title = title,
            subtitle = subtitle,
            posterUrl = posterUrl,
            progressFraction = progress,
            remainingText = remainingText,
        )
    }

    private fun formatRemaining(remainingTicks: Long): String {
        val totalMinutes = remainingTicks / TICKS_PER_MINUTE
        return when {
            totalMinutes <= 0 -> "<1m left"
            totalMinutes < 60 -> "${totalMinutes}m left"
            else -> {
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (minutes == 0L) "${hours}h left" else "${hours}h ${minutes}m left"
            }
        }
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

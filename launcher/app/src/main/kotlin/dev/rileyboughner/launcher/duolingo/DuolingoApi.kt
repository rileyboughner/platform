package dev.rileyboughner.launcher.duolingo

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Raw client for Duolingo's unofficial, unauthenticated public-profile
 * endpoint (`GET /2017-06-30/users?username=`) — there is no official public
 * Duolingo API. This endpoint can change shape or disappear at any time; see
 * [DuolingoRepository] for how every failure here is turned into a cached
 * fallback or a subtle "unavailable" state instead of reaching the UI.
 */
internal object DuolingoApi {

    private const val BASE_URL = "https://www.duolingo.com/2017-06-30/users"
    private const val TIMEOUT_MS = 10_000

    data class Stats(val streakDays: Int, val totalXp: Int?)

    fun fetchStats(username: String): Stats {
        val encodedUsername = URLEncoder.encode(username, "UTF-8")
        val connection = URL("$BASE_URL?username=$encodedUsername").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val body = try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Duolingo request failed with HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val user = JSONObject(body).optJSONArray("users")?.optJSONObject(0)
            ?: throw IOException("No Duolingo user in response (profile may be private or username is wrong)")

        // Defensive parsing: this unofficial endpoint has been observed to
        // expose the current streak in two different places — a top-level
        // `streak` count, and a nested `streakData.currentStreak.length`.
        // Try the top-level field first, then fall back to the nested one.
        val streakDays = user.optIntOrNull("streak")
            ?: user.optJSONObject("streakData")?.optJSONObject("currentStreak")?.optIntOrNull("length")
            ?: throw IOException("No recognizable streak field in Duolingo response")

        // Same defensive-parsing approach for total XP: top-level `totalXp`
        // first, falling back to the shorter `xp` field some responses use.
        // Null (rather than a thrown error) when neither is present — XP is
        // a bonus display field, not required for a usable streak result.
        val totalXp = user.optIntOrNull("totalXp") ?: user.optIntOrNull("xp")

        return Stats(streakDays = streakDays, totalXp = totalXp)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val value = optInt(key, Int.MIN_VALUE)
        return value.takeIf { it != Int.MIN_VALUE }
    }
}

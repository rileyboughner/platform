package dev.rileyboughner.launcher.moviewheel

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Raw movie-wheel API client (https://movies.rileyboughner.dev). No
 * authentication, no server-side spin endpoint — the random pick happens in
 * [MovieWheelRepository]/the UI, same as the web app does client-side.
 * Blocking I/O — callers run this on [kotlinx.coroutines.Dispatchers.IO].
 */
internal object MovieWheelApi {

    private const val BASE_URL = "https://movies.rileyboughner.dev"
    private const val TIMEOUT_MS = 10_000

    fun fetchMovies(): List<Movie> {
        val body = get("$BASE_URL/api/movies")
        val array = JSONArray(body)
        return buildList {
            for (i in 0 until array.length()) {
                add(parseMovie(array.getJSONObject(i)))
            }
        }
    }

    fun posterUrl(id: String): String = "$BASE_URL/api/poster/$id"

    private fun parseMovie(obj: JSONObject): Movie {
        val id = obj.opt("id")?.toString() ?: ""
        val title = obj.optString("title", "Untitled")
        val year = if (obj.isNull("year")) null else obj.optInt("year")
        val genresArray = obj.optJSONArray("genres") ?: JSONArray()
        val genres = buildList {
            for (i in 0 until genresArray.length()) {
                add(genresArray.getString(i))
            }
        }
        return Movie(id = id, title = title, year = year, genres = genres)
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Movie wheel request failed with HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

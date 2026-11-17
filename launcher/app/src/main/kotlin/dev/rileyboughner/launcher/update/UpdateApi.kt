package dev.rileyboughner.launcher.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Raw client for `launcher/version.json`'s raw-GitHub URL (see
 * `launcher/UPDATES.md`). Blocking I/O — [UpdateRepository] runs this on
 * `Dispatchers.IO` with a short timeout and swallows any failure (offline,
 * 404, malformed JSON), same pattern as
 * [dev.rileyboughner.launcher.dashboard.DashboardApi].
 */
internal object UpdateApi {

    private const val VERSION_URL =
        "https://raw.githubusercontent.com/rileyboughner/platform/main/launcher/version.json"
    private const val TIMEOUT_MS = 5_000

    fun fetchVersionInfo(): UpdateInfo {
        val connection = URL(VERSION_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val body = try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Version check failed with HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        return parseUpdateInfo(body)
    }

    /** Split out from [fetchVersionInfo] so it's exercised directly by a JVM unit test, no network involved. */
    internal fun parseUpdateInfo(body: String): UpdateInfo {
        val json = JSONObject(body)
        return UpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            apkUrl = json.getString("apkUrl"),
            notes = json.optString("notes", ""),
        )
    }
}

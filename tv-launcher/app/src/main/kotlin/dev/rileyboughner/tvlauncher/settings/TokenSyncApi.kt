package dev.rileyboughner.tvlauncher.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Raw stdlib-HTTP client for the launcher token server
 * (`dotfiles/scripts/launcher-tokens/token-server.py`): `GET /tokens`
 * returns the tokens the server holds (Home Assistant, Todoist, Jellyfin
 * — only the files actually present), so the TV never needs a hand-pasted
 * token. Plain HTTP on the private networks only, same posture as the
 * theme-sync hub.
 *
 * This dispatches itself onto `Dispatchers.IO`: the launch-time call site
 * runs on the main thread, and a blocking fetch there would freeze the UI.
 *
 * Candidates mirror the hub's: LAN address first (home), tailnet
 * MagicDNS name second (anywhere else). Any failure (server down, off the
 * LAN and off the tailnet, malformed response) yields null: the caller
 * keeps whatever token it already has. The token value is never logged.
 */
object TokenSyncApi {

    private val SERVER_CANDIDATES = listOf(
        "http://192.168.1.2:8766",
        "http://server.taila93a39.ts.net:8766",
    )
    private const val REQUEST_TIMEOUT_MS = 5_000

    /** The tokens the server holds right now — a field is null when that token file isn't on the server (yet). */
    data class Tokens(
        val homeAssistantToken: String?,
        val todoistToken: String?,
        val jellyfinApiKey: String?,
    )

    /** First candidate that answers `GET /tokens`, in LAN-then-tailnet order; null if neither does. */
    suspend fun fetchTokens(): Tokens? = withContext(Dispatchers.IO) {
        for (candidate in SERVER_CANDIDATES) {
            fetchFrom(candidate)?.let { return@withContext it }
        }
        null
    }

    private fun fetchFrom(baseUrl: String): Tokens? = runCatching {
        val connection = URL("$baseUrl/tokens").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = REQUEST_TIMEOUT_MS
        connection.readTimeout = REQUEST_TIMEOUT_MS
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("token server request failed: GET /tokens -> HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            Tokens(
                homeAssistantToken = json.optString("homeAssistantToken", "").takeIf { it.isNotBlank() },
                todoistToken = json.optString("todoistToken", "").takeIf { it.isNotBlank() },
                jellyfinApiKey = json.optString("jellyfinApiKey", "").takeIf { it.isNotBlank() },
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

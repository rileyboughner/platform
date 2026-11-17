package dev.rileyboughner.launcher.settings

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
 * — only the files actually present), so Settings never needs a
 * hand-pasted token per device. Plain HTTP on the private networks only,
 * same posture as the theme-sync hub.
 *
 * Unlike [dev.rileyboughner.launcher.theme.ThemeSyncApi], this one
 * dispatches itself onto `Dispatchers.IO`: both call sites (the Settings
 * "Sync tokens from server" tap, and the TV's launch-time fetch) run on
 * the main thread, and a blocking fetch there would freeze the UI.
 *
 * Candidates mirror the hub's: LAN address first (home), tailnet
 * MagicDNS name second (anywhere else) — the token server binds to the
 * same two private addresses (`launcher-token-server.nix`). Any failure
 * (server down, off the LAN and off the tailnet, malformed response)
 * yields null: the caller keeps whatever tokens it already has.
 */
internal object TokenSyncApi {

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

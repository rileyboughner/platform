package dev.rileyboughner.tvlauncher.theme

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Raw stdlib-HTTP client for the theme-sync hub (`dotfiles/scripts/theme-sync/hub.py`,
 * protocol in that directory's README.md). Read-only from this app's side:
 * it only ever GETs state and wallpaper blobs, never POSTs. Blocking I/O —
 * callers ([ThemeSyncRepository]) run everything here on `Dispatchers.IO`
 * and swallow failures (hub down, off the LAN and off the tailnet,
 * malformed response): the hub being unreachable is an expected, graceful
 * state, never a crash.
 *
 * Candidates mirror `THEME_SYNC_HUB_URLS` in
 * `dotfiles/scripts/lib/theme-sync-common.sh` exactly: LAN address first
 * (home), tailnet MagicDNS name second (anywhere else) — same hub, same
 * two private-network addresses it binds to (`theme-sync-hub.nix`).
 */
internal object ThemeSyncApi {

    private val HUB_CANDIDATES = listOf(
        "http://192.168.1.2:8765",
        "http://server.taila93a39.ts.net:8765",
    )
    private const val PROBE_TIMEOUT_MS = 2_000
    private const val REQUEST_TIMEOUT_MS = 5_000
    private const val BLOB_TIMEOUT_MS = 30_000

    /** First candidate that answers `GET /mode`, in LAN-then-tailnet order; null if neither does. */
    fun findHubBaseUrl(): String? {
        for (candidate in HUB_CANDIDATES) {
            if (getMode(candidate, timeoutMs = PROBE_TIMEOUT_MS) != null) return candidate
        }
        return null
    }

    fun getMode(baseUrl: String, timeoutMs: Int = REQUEST_TIMEOUT_MS): String? = runCatching {
        val body = request(baseUrl, "/mode", "GET", timeoutMs = timeoutMs)
        JSONObject(body).optString("mode", "").takeIf { it == "unified" || it == "split" }
    }.getOrNull()

    fun getState(baseUrl: String, path: String): JSONObject? = runCatching {
        JSONObject(request(baseUrl, "/$path", "GET"))
    }.getOrNull()

    fun getBlob(baseUrl: String, sha256: String): ByteArray? = runCatching {
        val connection = openConnection(baseUrl, "/wallpaper/$sha256", "GET", BLOB_TIMEOUT_MS)
        try {
            if (connection.responseCode != 200) return@runCatching null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun openConnection(baseUrl: String, path: String, method: String, timeoutMs: Int): HttpURLConnection {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        return connection
    }

    private fun request(baseUrl: String, path: String, method: String, timeoutMs: Int = REQUEST_TIMEOUT_MS): String {
        val connection = openConnection(baseUrl, path, method, timeoutMs)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("theme-sync hub request failed: $method $path -> HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

package dev.rileyboughner.tvlauncher.jellyfin

import dev.rileyboughner.tvlauncher.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface JellyfinResult<out T> {
    data class Success<T>(val value: T) : JellyfinResult<T>
    data class Error(val message: String) : JellyfinResult<Nothing>
    data object NotConfigured : JellyfinResult<Nothing>
}

/**
 * Orchestrates Jellyfin calls for the TV home screen's Continue Watching
 * row: resolves the saved API key, runs network I/O off the main thread,
 * and turns every failure into a [JellyfinResult.Error] — this must never
 * let an exception reach the UI and crash the launcher.
 *
 * The server URL is a fixed default ([SettingsStore.DEFAULT_JELLYFIN_SERVER_URL]):
 * the TV has no settings screen, same as the Home Assistant URL. The API
 * key arrives from the launcher token server on every launch (see
 * MainActivity) and is stored encrypted; until Riley mints a Jellyfin API
 * key for the house account and places it in the token server's secrets,
 * every fetch is [JellyfinResult.NotConfigured] and the row stays hidden.
 */
class JellyfinRepository(private val settingsStore: SettingsStore) {

    fun serverUrl(): String = SettingsStore.DEFAULT_JELLYFIN_SERVER_URL

    suspend fun fetchResumeItems(): JellyfinResult<List<JellyfinResumeItem>> = withContext(Dispatchers.IO) {
        val apiKey = settingsStore.getJellyfinApiKey() ?: return@withContext JellyfinResult.NotConfigured
        try {
            JellyfinResult.Success(
                JellyfinApi.fetchResumeItems(SettingsStore.DEFAULT_JELLYFIN_SERVER_URL, apiKey),
            )
        } catch (e: IOException) {
            JellyfinResult.Error("Couldn't reach Jellyfin. Check your connection and try again.")
        } catch (e: Exception) {
            JellyfinResult.Error("Couldn't load continue watching from Jellyfin.")
        }
    }
}

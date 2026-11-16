package dev.rileyboughner.launcher.jellyfin

import dev.rileyboughner.launcher.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface JellyfinResult<out T> {
    data class Success<T>(val value: T) : JellyfinResult<T>
    data class Error(val message: String) : JellyfinResult<Nothing>
    data object NotConfigured : JellyfinResult<Nothing>
}

/**
 * Orchestrates Jellyfin calls for the homelab dashboard page: resolves the
 * saved server URL + API key, runs network I/O off the main thread, and
 * turns every failure into a [JellyfinResult.Error] — this must never let
 * an exception reach the UI and crash the launcher.
 */
class JellyfinRepository(private val settingsRepository: SettingsRepository) {

    fun serverUrl(): String = settingsRepository.getJellyfinServerUrl()

    suspend fun fetchResumeItems(): JellyfinResult<List<JellyfinResumeItem>> = withContext(Dispatchers.IO) {
        val serverUrl = settingsRepository.getJellyfinServerUrl()
        val apiKey = settingsRepository.getJellyfinApiKey()
        if (apiKey == null) {
            return@withContext JellyfinResult.NotConfigured
        }
        try {
            JellyfinResult.Success(JellyfinApi.fetchResumeItems(serverUrl, apiKey))
        } catch (e: IOException) {
            JellyfinResult.Error("Couldn't reach Jellyfin. Check your connection and try again.")
        } catch (e: Exception) {
            JellyfinResult.Error("Couldn't load continue watching from Jellyfin.")
        }
    }
}

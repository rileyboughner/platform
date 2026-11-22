package dev.rileyboughner.tvlauncher.homeassistant

import dev.rileyboughner.tvlauncher.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface HomeAssistantResult<out T> {
    data class Success<T>(val value: T) : HomeAssistantResult<T>
    data class Error(val message: String) : HomeAssistantResult<Nothing>
    data object NotConfigured : HomeAssistantResult<Nothing>
}

/**
 * Orchestrates Home Assistant calls for the TV home screen: resolves the
 * saved server URL + long-lived token, runs network I/O off the main
 * thread, and turns every failure into a [HomeAssistantResult.Error] — this
 * must never let an exception reach the UI and crash the launcher.
 */
class HomeAssistantRepository(private val settingsStore: SettingsStore) {

    suspend fun fetchSnapshot(): HomeAssistantResult<HomeAssistantSnapshot> = withContext(Dispatchers.IO) {
        val (serverUrl, token) = credentials() ?: return@withContext HomeAssistantResult.NotConfigured
        try {
            HomeAssistantResult.Success(HomeAssistantApi.fetchSnapshot(serverUrl, token))
        } catch (e: IOException) {
            HomeAssistantResult.Error("Couldn't reach Home Assistant. Check your connection and try again.")
        } catch (e: Exception) {
            HomeAssistantResult.Error("Couldn't load Home Assistant state.")
        }
    }

    suspend fun toggleLight(entityId: String): HomeAssistantResult<Unit> = withContext(Dispatchers.IO) {
        val (serverUrl, token) = credentials() ?: return@withContext HomeAssistantResult.NotConfigured
        try {
            HomeAssistantApi.toggleLight(serverUrl, token, entityId)
            HomeAssistantResult.Success(Unit)
        } catch (e: IOException) {
            HomeAssistantResult.Error("Couldn't reach Home Assistant.")
        } catch (e: Exception) {
            HomeAssistantResult.Error("Couldn't toggle that light.")
        }
    }

    suspend fun setLightBrightness(entityId: String, brightnessPercent: Int): HomeAssistantResult<Unit> =
        withContext(Dispatchers.IO) {
            val (serverUrl, token) = credentials() ?: return@withContext HomeAssistantResult.NotConfigured
            try {
                HomeAssistantApi.setLightBrightness(serverUrl, token, entityId, brightnessPercent)
                HomeAssistantResult.Success(Unit)
            } catch (e: IOException) {
                HomeAssistantResult.Error("Couldn't reach Home Assistant.")
            } catch (e: Exception) {
                HomeAssistantResult.Error("Couldn't set brightness.")
            }
        }

    suspend fun activateScene(entityId: String): HomeAssistantResult<Unit> = withContext(Dispatchers.IO) {
        val (serverUrl, token) = credentials() ?: return@withContext HomeAssistantResult.NotConfigured
        try {
            HomeAssistantApi.activateScene(serverUrl, token, entityId)
            HomeAssistantResult.Success(Unit)
        } catch (e: IOException) {
            HomeAssistantResult.Error("Couldn't reach Home Assistant.")
        } catch (e: Exception) {
            HomeAssistantResult.Error("Couldn't activate that scene.")
        }
    }

    private fun credentials(): Pair<String, String>? {
        val serverUrl = settingsStore.getHomeAssistantServerUrl()
        val token = settingsStore.getHomeAssistantToken() ?: return null
        return serverUrl to token
    }
}

package dev.rileyboughner.launcher.weather

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface WeatherResult {
    data class Success(val temperatureF: Double, val condition: String) : WeatherResult
    data object Unavailable : WeatherResult
}

/**
 * Orchestrates the current-weather lookup for the main page's glance
 * block, mirroring [dev.rileyboughner.launcher.duolingo.DuolingoRepository]:
 * serves a cached result when it's still fresh (under [STALE_AFTER_MILLIS]
 * old) instead of hitting the network on every page view, and turns every
 * failure (network, unexpected response shape) into either the last cached
 * value or [WeatherResult.Unavailable] — this must never let an exception
 * reach the UI and crash the launcher.
 *
 * Location is fixed to Riley's home (Cincinnati, OH) — no location
 * permission is requested and no location is ever read from the device.
 * The cache lives in a plain (non-encrypted) SharedPreferences file since
 * a temperature reading isn't sensitive.
 */
class WeatherRepository(context: Context) {

    private val cachePrefs = context.getSharedPreferences(CACHE_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Synchronous, no-network peek at the last cached result, for seeding
     * Compose state with a cached value before the real (possibly stale)
     * check in [fetch] runs — so the glance row never shows blank on a
     * page revisit just because the async fetch hasn't resolved yet.
     */
    fun peekCached(): WeatherResult.Success? {
        if (!cachePrefs.contains(KEY_TEMPERATURE_F)) return null
        val condition = cachePrefs.getString(KEY_CONDITION, null) ?: return null
        return WeatherResult.Success(
            temperatureF = cachePrefs.getFloat(KEY_TEMPERATURE_F, 0f).toDouble(),
            condition = condition,
        )
    }

    suspend fun fetch(forceRefresh: Boolean = false): WeatherResult = withContext(Dispatchers.IO) {
        val cached = peekCached()
        val cachedAtMillis = cachePrefs.getLong(KEY_FETCHED_AT_MILLIS, 0L)
        val isCacheFresh = cached != null && (System.currentTimeMillis() - cachedAtMillis) < STALE_AFTER_MILLIS

        if (isCacheFresh && !forceRefresh) {
            return@withContext cached!!
        }

        try {
            val current = WeatherApi.fetchCurrent(HOME_LATITUDE, HOME_LONGITUDE)
            cachePrefs.edit()
                .putFloat(KEY_TEMPERATURE_F, current.temperatureF.toFloat())
                .putString(KEY_CONDITION, current.condition)
                .putLong(KEY_FETCHED_AT_MILLIS, System.currentTimeMillis())
                .apply()
            WeatherResult.Success(current.temperatureF, current.condition)
        } catch (e: Exception) {
            cached ?: WeatherResult.Unavailable
        }
    }

    companion object {
        // Cincinnati, OH — Riley's home. Fixed default; no location permission.
        private const val HOME_LATITUDE = 39.10
        private const val HOME_LONGITUDE = -84.51

        private const val CACHE_FILE_NAME = "weather_cache"
        private const val KEY_TEMPERATURE_F = "temperature_f"
        private const val KEY_CONDITION = "condition"
        private const val KEY_FETCHED_AT_MILLIS = "fetched_at_millis"
        private const val STALE_AFTER_MILLIS = 30 * 60 * 1000L
    }
}

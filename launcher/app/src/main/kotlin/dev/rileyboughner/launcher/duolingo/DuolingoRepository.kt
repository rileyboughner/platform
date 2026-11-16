package dev.rileyboughner.launcher.duolingo

import android.content.Context
import dev.rileyboughner.launcher.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DuolingoResult {
    data class Success(val streakDays: Int) : DuolingoResult
    data object NotConfigured : DuolingoResult
    data object Unavailable : DuolingoResult
}

/**
 * Orchestrates the Duolingo streak lookup for the dashboard page: resolves
 * the saved username, serves a cached streak when it's still fresh (under
 * [STALE_AFTER_MILLIS] old) instead of hitting the network on every page
 * view, and turns every failure (network, private profile, unrecognized
 * response shape) into either the last cached value or [DuolingoResult.Unavailable]
 * — this must never let an exception reach the UI and crash the launcher.
 *
 * The cache lives in a plain (non-encrypted) SharedPreferences file since a
 * streak count isn't sensitive, separate from [SettingsRepository]'s
 * encrypted token/key storage.
 */
class DuolingoRepository(context: Context, private val settingsRepository: SettingsRepository) {

    private val cachePrefs = context.getSharedPreferences(CACHE_FILE_NAME, Context.MODE_PRIVATE)

    suspend fun fetchStreak(forceRefresh: Boolean = false): DuolingoResult = withContext(Dispatchers.IO) {
        val username = settingsRepository.getDuolingoUsername()
            ?: return@withContext DuolingoResult.NotConfigured

        val cachedDays = cachePrefs.getInt(KEY_STREAK_DAYS, -1).takeIf { it >= 0 }
        val cachedAtMillis = cachePrefs.getLong(KEY_FETCHED_AT_MILLIS, 0L)
        val isCacheFresh = cachedDays != null && (System.currentTimeMillis() - cachedAtMillis) < STALE_AFTER_MILLIS

        if (isCacheFresh && !forceRefresh) {
            return@withContext DuolingoResult.Success(cachedDays)
        }

        try {
            val streakDays = DuolingoApi.fetchStreakDays(username)
            cachePrefs.edit()
                .putInt(KEY_STREAK_DAYS, streakDays)
                .putLong(KEY_FETCHED_AT_MILLIS, System.currentTimeMillis())
                .apply()
            DuolingoResult.Success(streakDays)
        } catch (e: Exception) {
            cachedDays?.let { DuolingoResult.Success(it) } ?: DuolingoResult.Unavailable
        }
    }

    companion object {
        private const val CACHE_FILE_NAME = "duolingo_cache"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_FETCHED_AT_MILLIS = "fetched_at_millis"
        private const val STALE_AFTER_MILLIS = 30 * 60 * 1000L
    }
}

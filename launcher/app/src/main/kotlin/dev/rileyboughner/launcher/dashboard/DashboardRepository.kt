package dev.rileyboughner.launcher.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface DashboardResult<out T> {
    data class Success<T>(val value: T) : DashboardResult<T>
    data class Error(val message: String) : DashboardResult<Nothing>
}

/**
 * Orchestrates the dashboard metrics call for the Homelab dashboard page:
 * runs network I/O off the main thread and turns every failure into a
 * [DashboardResult.Error] — this must never let an exception reach the UI
 * and crash the launcher, or block the rest of the dashboard page from
 * showing when the fetch fails.
 */
class DashboardRepository {

    suspend fun fetchServerMetrics(): DashboardResult<ServerMetrics> = withContext(Dispatchers.IO) {
        try {
            DashboardResult.Success(DashboardApi.fetchServerMetrics())
        } catch (e: IOException) {
            DashboardResult.Error("Couldn't reach the dashboard.")
        } catch (e: Exception) {
            DashboardResult.Error("Couldn't load server metrics.")
        }
    }
}

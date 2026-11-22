package dev.rileyboughner.launcher.nextcloud

import dev.rileyboughner.launcher.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface NextcloudTasksResult<out T> {
    data class Success<T>(val value: T) : NextcloudTasksResult<T>

    /** [detail] is the real failure (HTTP status or exception message) shown alongside [message]. */
    data class Error(val message: String, val detail: String? = null) : NextcloudTasksResult<Nothing>
}

/**
 * Orchestrates Nextcloud Tasks (CalDAV VTODO) calls for the Todo page:
 * resolves the saved Nextcloud username + app password, runs network I/O
 * off the main thread, and turns every failure into a
 * [NextcloudTasksResult.Error] — this must never let an exception reach
 * the UI and crash the launcher.
 */
class NextcloudTasksRepository(private val settingsRepository: SettingsRepository) {

    fun hasCredentials(): Boolean =
        settingsRepository.getNextcloudUsername() != null &&
            settingsRepository.getNextcloudAppPassword() != null

    /**
     * Fetches every VTODO from the Nextcloud Tasks calendar, keeping only
     * the ones that are not completed. Recurring tasks are returned as a
     * single entry each (their recurrences are not expanded).
     */
    suspend fun fetchTasks(): NextcloudTasksResult<List<NextcloudTask>> = withContext(Dispatchers.IO) {
        val username = settingsRepository.getNextcloudUsername()
        val appPassword = settingsRepository.getNextcloudAppPassword()
        if (username == null || appPassword == null) {
            return@withContext NextcloudTasksResult.Error(
                "No Nextcloud credentials saved. Add your Nextcloud username + app password in Settings.",
            )
        }
        try {
            val xml = NextcloudCalDav.reportTasks(username, appPassword)
            val tasks = NextcloudCalDav.extractCalendarData(xml)
                .mapNotNull { (href, ics) -> parseVTodo(ics, href) }
                .filter { it.status != "COMPLETED" }
            NextcloudTasksResult.Success(tasks)
        } catch (e: IOException) {
            NextcloudTasksResult.Error(
                "Couldn't reach Nextcloud. Check your connection and try again.",
                detail = e.message,
            )
        } catch (e: Exception) {
            NextcloudTasksResult.Error(
                "Couldn't load tasks from Nextcloud.",
                detail = e.message ?: e::class.java.simpleName,
            )
        }
    }

    /** Marks a task completed by PUT-ing its VTODO back with STATUS:COMPLETED. */
    suspend fun completeTask(task: NextcloudTask): NextcloudTasksResult<Unit> = withContext(Dispatchers.IO) {
        val username = settingsRepository.getNextcloudUsername()
        val appPassword = settingsRepository.getNextcloudAppPassword()
        if (username == null || appPassword == null) {
            return@withContext NextcloudTasksResult.Error("No Nextcloud credentials saved.")
        }
        try {
            NextcloudCalDav.putTask(username, appPassword, task.href, markCompletedIcs(task))
            NextcloudTasksResult.Success(Unit)
        } catch (e: IOException) {
            NextcloudTasksResult.Error(
                "Couldn't reach Nextcloud. Check your connection and try again.",
                detail = e.message,
            )
        } catch (e: Exception) {
            NextcloudTasksResult.Error(
                "Couldn't complete that task.",
                detail = e.message ?: e::class.java.simpleName,
            )
        }
    }
}

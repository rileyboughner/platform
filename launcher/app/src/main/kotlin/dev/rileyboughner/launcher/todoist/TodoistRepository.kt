package dev.rileyboughner.launcher.todoist

import dev.rileyboughner.launcher.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface TodoistResult<out T> {
    data class Success<T>(val value: T) : TodoistResult<T>

    /** [detail] is the real failure (HTTP status or exception message) shown alongside [message]. */
    data class Error(val message: String, val detail: String? = null) : TodoistResult<Nothing>
}

/**
 * Orchestrates Todoist calls for the Todo page: resolves the saved token,
 * runs network I/O off the main thread, and turns every failure into a
 * [TodoistResult.Error] — this must never let an exception reach the UI
 * and crash the launcher.
 *
 * Retained as a fallback only: the Todo page now reads from Nextcloud
 * Tasks (see nextcloud/NextcloudTasksRepository) and this repository is
 * no longer wired into the pager. Kept — with its API client and Settings
 * token field — so switching back is a wiring change, not a rewrite.
 */
class TodoistRepository(private val settingsRepository: SettingsRepository) {

    fun hasToken(): Boolean = settingsRepository.getTodoistToken() != null

    suspend fun fetchTasks(): TodoistResult<List<TodoistTask>> = withContext(Dispatchers.IO) {
        val token = settingsRepository.getTodoistToken()
            ?: return@withContext TodoistResult.Error("No Todoist API token saved. Add one in Settings.")
        try {
            TodoistResult.Success(TodoistApi.fetchTasks(token))
        } catch (e: IOException) {
            TodoistResult.Error("Couldn't reach Todoist. Check your connection and try again.", detail = e.message)
        } catch (e: Exception) {
            TodoistResult.Error("Couldn't load tasks from Todoist.", detail = e.message ?: e::class.java.simpleName)
        }
    }

    suspend fun closeTask(taskId: String): TodoistResult<Unit> = withContext(Dispatchers.IO) {
        val token = settingsRepository.getTodoistToken()
            ?: return@withContext TodoistResult.Error("No Todoist API token saved.")
        try {
            TodoistApi.closeTask(token, taskId)
            TodoistResult.Success(Unit)
        } catch (e: IOException) {
            TodoistResult.Error("Couldn't reach Todoist. Check your connection and try again.", detail = e.message)
        } catch (e: Exception) {
            TodoistResult.Error("Couldn't complete that task.", detail = e.message ?: e::class.java.simpleName)
        }
    }
}

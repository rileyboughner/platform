package dev.rileyboughner.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.nextcloud.NextcloudTask
import dev.rileyboughner.launcher.nextcloud.NextcloudTasksRepository
import dev.rileyboughner.launcher.nextcloud.NextcloudTasksResult
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Todo page (left of Apps in the pager). Reads tasks from Nextcloud
 * Tasks (CalDAV VTODO) — see [NextcloudTasksRepository]. Fetches on first
 * show and on manual refresh; never crashes on a network error.
 *
 * Todoist is retained as a fallback (see the todoist/ package — its
 * repository, API client, and Settings token field are all still there),
 * but the Todo page no longer reads from it.
 */
@Composable
fun TodoScreen(nextcloudTasksRepository: NextcloudTasksRepository, modifier: Modifier = Modifier) {
    var hasCredentials by remember { mutableStateOf(nextcloudTasksRepository.hasCredentials()) }
    var tasks by remember { mutableStateOf<List<NextcloudTask>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        hasCredentials = nextcloudTasksRepository.hasCredentials()
        if (!hasCredentials) return
        scope.launch {
            isLoading = true
            when (val result = nextcloudTasksRepository.fetchTasks()) {
                is NextcloudTasksResult.Success -> {
                    tasks = sortTasks(result.value)
                    errorMessage = null
                }
                is NextcloudTasksResult.Error -> errorMessage = formatError(result)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        AppTextRow(label = "↻ Refresh", onClick = { refresh() })
        HairlineDivider()

        when {
            !hasCredentials -> EmptyState("Add your Nextcloud username + app password in Settings to see tasks here.")
            errorMessage != null -> EmptyState(errorMessage!!)
            !isLoading && tasks.isEmpty() -> EmptyState("No tasks. Nice.")
            else -> LazyColumn {
                items(tasks, key = { it.uid }) { task ->
                    TodoRow(
                        task = task,
                        onComplete = {
                            scope.launch {
                                when (val result = nextcloudTasksRepository.completeTask(task)) {
                                    is NextcloudTasksResult.Success -> tasks = tasks.filterNot { it.uid == task.uid }
                                    is NextcloudTasksResult.Error -> errorMessage = formatError(result)
                                }
                            }
                        },
                    )
                    HairlineDivider()
                }
            }
        }
    }
}

@Composable
private fun TodoRow(task: NextcloudTask, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = { onComplete() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.onBackground,
                uncheckedColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = dueLabel(task.due),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Friendly message plus the real failure (HTTP status or exception detail), so it's diagnosable. */
private fun formatError(error: NextcloudTasksResult.Error): String =
    if (error.detail != null) "${error.message}\n(${error.detail})" else error.message

/** Due date first (earliest first, no due date last), then title. */
private fun sortTasks(tasks: List<NextcloudTask>): List<NextcloudTask> =
    tasks.sortedWith(
        compareBy(
            { it.due ?: LocalDate.MAX },
            { it.title.lowercase() },
        ),
    )

private fun dueLabel(due: LocalDate?): String {
    if (due == null) return "No due date"
    val today = LocalDate.now()
    return when {
        due.isBefore(today) -> "Overdue · $due"
        due.isEqual(today) -> "Today"
        due.isEqual(today.plusDays(1)) -> "Tomorrow"
        else -> due.toString()
    }
}

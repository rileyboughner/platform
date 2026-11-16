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
import dev.rileyboughner.launcher.todoist.TodoistDue
import dev.rileyboughner.launcher.todoist.TodoistRepository
import dev.rileyboughner.launcher.todoist.TodoistResult
import dev.rileyboughner.launcher.todoist.TodoistTask
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Todo page (left of Apps in the pager). Fetches on first show and on
 * manual refresh; never crashes on a network error — see
 * [TodoistRepository.fetchTasks].
 */
@Composable
fun TodoScreen(todoistRepository: TodoistRepository, modifier: Modifier = Modifier) {
    var hasToken by remember { mutableStateOf(todoistRepository.hasToken()) }
    var tasks by remember { mutableStateOf<List<TodoistTask>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        hasToken = todoistRepository.hasToken()
        if (!hasToken) return
        scope.launch {
            isLoading = true
            when (val result = todoistRepository.fetchTasks()) {
                is TodoistResult.Success -> {
                    tasks = sortTasks(result.value)
                    errorMessage = null
                }
                is TodoistResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        AppTextRow(label = "↻ Refresh", onClick = { refresh() })
        HairlineDivider()

        when {
            !hasToken -> EmptyState("Add your Todoist API token in Settings to see tasks here.")
            errorMessage != null -> EmptyState(errorMessage!!)
            !isLoading && tasks.isEmpty() -> EmptyState("No tasks. Nice.")
            else -> LazyColumn {
                items(tasks, key = { it.id }) { task ->
                    TodoRow(
                        task = task,
                        onComplete = {
                            scope.launch {
                                when (todoistRepository.closeTask(task.id)) {
                                    is TodoistResult.Success -> tasks = tasks.filterNot { it.id == task.id }
                                    is TodoistResult.Error -> errorMessage = "Couldn't complete that task."
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
private fun TodoRow(task: TodoistTask, onComplete: () -> Unit, modifier: Modifier = Modifier) {
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
                text = task.content,
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

/** Overdue first, then due today, then upcoming, then no due date — each bucket ordered by date. */
private fun sortTasks(tasks: List<TodoistTask>): List<TodoistTask> {
    val today = LocalDate.now()

    fun dueDate(task: TodoistTask): LocalDate? = task.due?.date?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    fun bucket(task: TodoistTask): Int {
        val due = dueDate(task) ?: return 2
        return when {
            due.isBefore(today) -> -1
            due.isEqual(today) -> 0
            else -> 1
        }
    }

    return tasks.sortedWith(
        compareBy(
            { bucket(it) },
            { dueDate(it) ?: LocalDate.MAX },
        ),
    )
}

private fun dueLabel(due: TodoistDue?): String {
    if (due == null) return "No due date"
    val date = runCatching { LocalDate.parse(due.date) }.getOrNull() ?: return due.date
    val today = LocalDate.now()
    return when {
        date.isBefore(today) -> "Overdue · $date"
        date.isEqual(today) -> "Today"
        date.isEqual(today.plusDays(1)) -> "Tomorrow"
        else -> date.toString()
    }
}

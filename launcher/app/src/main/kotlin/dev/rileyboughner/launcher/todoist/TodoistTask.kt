package dev.rileyboughner.launcher.todoist

/** One Todoist task, trimmed to the fields the Todo page actually shows. */
data class TodoistTask(
    val id: String,
    val content: String,
    val due: TodoistDue?,
)

/** `due.date` is always `YYYY-MM-DD`; `datetime` is set only for time-specific tasks. */
data class TodoistDue(
    val date: String,
    val datetime: String?,
)

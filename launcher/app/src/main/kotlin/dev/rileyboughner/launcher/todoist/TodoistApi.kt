package dev.rileyboughner.launcher.todoist

import org.json.JSONArray
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Raw Todoist REST API v2 client (https://api.todoist.com/rest/v2). Blocking
 * I/O — callers (see [TodoistRepository]) run this on [kotlinx.coroutines.Dispatchers.IO].
 *
 * The API token is only ever placed in the Authorization header, never
 * logged, and never included in any exception message thrown from here.
 */
internal object TodoistApi {

    private const val BASE_URL = "https://api.todoist.com/rest/v2"
    private const val TIMEOUT_MS = 10_000

    fun fetchTasks(token: String): List<TodoistTask> {
        val connection = openConnection("$BASE_URL/tasks", token, method = "GET")
        try {
            checkSuccess(connection)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseTasks(body)
        } finally {
            connection.disconnect()
        }
    }

    fun closeTask(token: String, taskId: String) {
        val connection = openConnection("$BASE_URL/tasks/$taskId/close", token, method = "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Length", "0")
        try {
            connection.outputStream.close()
            checkSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, token: String, method: String): HttpsURLConnection {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        return connection
    }

    private fun checkSuccess(connection: HttpsURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IOException("Todoist request failed with HTTP $code")
        }
    }

    private fun parseTasks(body: String): List<TodoistTask> {
        val array = JSONArray(body)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dueObj = obj.optJSONObject("due")
                val due = dueObj?.let {
                    TodoistDue(
                        date = it.optString("date"),
                        datetime = it.optString("datetime", "").takeIf { s -> s.isNotBlank() },
                    )
                }
                add(
                    TodoistTask(
                        id = obj.getString("id"),
                        content = obj.getString("content"),
                        due = due,
                    ),
                )
            }
        }
    }
}

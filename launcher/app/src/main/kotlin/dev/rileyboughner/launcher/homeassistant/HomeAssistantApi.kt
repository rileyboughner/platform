package dev.rileyboughner.launcher.homeassistant

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Raw Home Assistant REST API client (https://developers.home-assistant.io/docs/api/rest/).
 * Blocking I/O — callers (see [HomeAssistantRepository]) run this on
 * [kotlinx.coroutines.Dispatchers.IO].
 *
 * The long-lived access token is only ever placed in the Authorization
 * header, never logged, and never included in any exception message thrown
 * from here.
 */
internal object HomeAssistantApi {

    private const val TIMEOUT_MS = 10_000

    fun fetchSnapshot(serverUrl: String, token: String): HomeAssistantSnapshot {
        val body = request(serverUrl, token, "/api/states", method = "GET", jsonBody = null)
        val states = JSONArray(body)
        val byEntityId = buildMap {
            for (i in 0 until states.length()) {
                val state = states.getJSONObject(i)
                put(state.getString("entity_id"), state)
            }
        }

        val presence = when (byEntityId[HomeAssistantEntities.PRESENCE_ENTITY_ID]?.optString("state")) {
            "home" -> Presence.HOME
            "not_home" -> Presence.AWAY
            else -> Presence.UNKNOWN
        }

        val lights = HomeAssistantEntities.LIGHT_ENTITY_IDS.mapNotNull { entityId ->
            val state = byEntityId[entityId] ?: return@mapNotNull null
            val attributes = state.optJSONObject("attributes") ?: JSONObject()
            val rawBrightness = if (attributes.has("brightness")) attributes.optInt("brightness", -1) else -1
            HomeAssistantLight(
                entityId = entityId,
                friendlyName = attributes.optString("friendly_name", humanize(entityId)),
                isOn = state.optString("state") == "on",
                brightnessPercent = if (rawBrightness >= 0) ((rawBrightness * 100) / 255) else null,
            )
        }

        val scenes = HomeAssistantEntities.SCENE_ENTITY_IDS.map { entityId ->
            val attributes = byEntityId[entityId]?.optJSONObject("attributes")
            HomeAssistantScene(
                entityId = entityId,
                friendlyName = attributes?.optString("friendly_name", humanize(entityId)) ?: humanize(entityId),
            )
        }

        return HomeAssistantSnapshot(presence = presence, lights = lights, scenes = scenes)
    }

    fun toggleLight(serverUrl: String, token: String, entityId: String) {
        callService(serverUrl, token, domain = "light", service = "toggle", entityId = entityId)
    }

    fun setLightBrightness(serverUrl: String, token: String, entityId: String, brightnessPercent: Int) {
        val extra = JSONObject().put("brightness_pct", brightnessPercent)
        callService(serverUrl, token, domain = "light", service = "turn_on", entityId = entityId, extra = extra)
    }

    fun activateScene(serverUrl: String, token: String, entityId: String) {
        callService(serverUrl, token, domain = "scene", service = "turn_on", entityId = entityId)
    }

    private fun callService(
        serverUrl: String,
        token: String,
        domain: String,
        service: String,
        entityId: String,
        extra: JSONObject? = null,
    ) {
        val body = (extra ?: JSONObject()).put("entity_id", entityId)
        request(serverUrl, token, "/api/services/$domain/$service", method = "POST", jsonBody = body)
    }

    private fun request(serverUrl: String, token: String, path: String, method: String, jsonBody: JSONObject?): String {
        val connection = URL("$serverUrl$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (jsonBody != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(jsonBody.toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Home Assistant request failed with HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun humanize(entityId: String): String =
        entityId.substringAfter('.').split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

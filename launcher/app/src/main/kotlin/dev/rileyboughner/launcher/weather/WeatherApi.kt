package dev.rileyboughner.launcher.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Raw client for the Open-Meteo forecast API (`GET /v1/forecast`), no API
 * key needed. Returns the current temperature (Fahrenheit) and the WMO
 * weather code for the given coordinates. See [WeatherRepository] for how
 * every failure here is turned into a cached fallback or a subtle
 * "unavailable" state instead of reaching the UI.
 */
internal object WeatherApi {

    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
    private const val TIMEOUT_MS = 10_000

    data class Current(val temperatureF: Double, val condition: String)

    fun fetchCurrent(latitude: Double, longitude: Double): Current {
        val url = "$BASE_URL?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code&temperature_unit=fahrenheit"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val body = try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Open-Meteo request failed with HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val current = JSONObject(body).optJSONObject("current")
            ?: throw IOException("No current weather in Open-Meteo response")
        if (!current.has("temperature_2m") || !current.has("weather_code")) {
            throw IOException("Open-Meteo response is missing temperature or weather code")
        }
        return Current(
            temperatureF = current.getDouble("temperature_2m"),
            condition = conditionForCode(current.getInt("weather_code")),
        )
    }

    /** Short one-or-two-word label for a WMO weather code, for the one-line glance. */
    fun conditionForCode(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95 -> "Storm"
        96, 99 -> "Storm w/ hail"
        else -> "—"
    }
}

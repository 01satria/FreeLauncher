package com.flowlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherResult(val tempC: Double, val code: Int)

object WeatherHelper {

    suspend fun fetch(lat: Double, lon: Double): WeatherResult? =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon&current_weather=true"
                )
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout    = 6000
                val text = conn.inputStream.bufferedReader().readText()
                val cw   = JSONObject(text).getJSONObject("current_weather")
                WeatherResult(
                    tempC = cw.getDouble("temperature"),
                    code  = cw.getInt("weathercode")
                )
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    /** WMO weather code → single display character */
    fun codeToIcon(code: Int): String = when (code) {
        0            -> "☀"
        1, 2         -> "⛅"
        3            -> "☁"
        45, 48       -> "≋"
        51, 53, 55,
        61, 63, 65,
        80, 81, 82   -> "🌧"
        71, 73, 75,
        77, 85, 86   -> "❄"
        95, 96, 99   -> "⛈"
        else         -> "~"
    }

    fun formatTemp(tempC: Double): String = "${tempC.toInt()}°"
}

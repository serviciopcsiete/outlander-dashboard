package com.davidg.outlander

import android.app.Activity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class WeatherData(val temp: Int, val cond: String, val rain: Int, val sunrise: Long, val sunset: Long)

/** Clima de Open-Meteo (sin llave de API). Necesita internet y una ubicación. */
object Weather {
    fun condition(code: Int): String = when (code) {
        0 -> "Despejado"
        1, 2 -> "Parcial"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        in 51..57 -> "Llovizna"
        in 61..67, in 80..82 -> "Lluvia"
        in 71..77, 85, 86 -> "Nieve"
        in 95..99 -> "Tormenta"
        else -> "—"
    }

    private fun parseTime(s: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(s)?.time ?: 0L
    } catch (e: Exception) { 0L }

    fun fetch(activity: Activity, lat: Double, lon: Double, done: (WeatherData) -> Unit) {
        thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&hourly=precipitation_probability" +
                    "&daily=sunrise,sunset&forecast_days=1&timezone=auto")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(body)
                val cur = root.getJSONObject("current")
                val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
                val code = cur.getInt("weather_code")
                val hour = cur.getString("time").substring(11, 13).toInt()
                val hourly = root.getJSONObject("hourly").getJSONArray("precipitation_probability")
                val rain = hourly.optInt(minOf(hour + 1, hourly.length() - 1), 0)
                val daily = root.getJSONObject("daily")
                val sunrise = parseTime(daily.getJSONArray("sunrise").getString(0))
                val sunset = parseTime(daily.getJSONArray("sunset").getString(0))
                val w = WeatherData(temp, condition(code), rain, sunrise, sunset)
                activity.runOnUiThread { done(w) }
            } catch (e: Exception) { /* sin internet: se queda el último dato */ }
        }
    }
}

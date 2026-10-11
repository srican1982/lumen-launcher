package com.lumen.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherRepository(private val context: android.content.Context) {

    suspend fun load(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        val permitted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val position = if (permitted) {
            val cancellation = com.google.android.gms.tasks.CancellationTokenSource()
            try {
                kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                        .getCurrentLocation(com.google.android.gms.location.CurrentLocationRequest.Builder()
                            .setPriority(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                            .setGranularity(com.google.android.gms.location.Granularity.GRANULARITY_COARSE)
                            .setDurationMillis(5_000L).build(), cancellation.token).await()
                }
            } finally { cancellation.cancel() }
        } else null
        // Permission granted: never silently fall back to an IP-location service.
        val geo = if (!permitted) get("https://ipwho.is/")?.let { JSONObject(it) } else null
        val lat = position?.latitude ?: geo?.optDouble("latitude", Double.NaN) ?: Double.NaN
        val lon = position?.longitude ?: geo?.optDouble("longitude", Double.NaN) ?: Double.NaN
        val city = if (position != null) "Local" else geo?.optString("city").orEmpty()
        if (lat.isNaN() || lon.isNaN()) return@withContext null
        val forecast = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&hourly=temperature_2m,weather_code" +
                "&forecast_days=2&timezone=auto"
        ) ?: return@withContext null
        val json = JSONObject(forecast)
        val current = json.optJSONObject("current") ?: return@withContext null
        val daily = json.optJSONObject("daily")
        val high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.toInt()
        val low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.toInt()
        WeatherSnapshot(
            temperatureC = current.optDouble("temperature_2m").toInt(),
            summary = WeatherCodes.summary(current.optInt("weather_code")),
            place = city.ifBlank { "Local" },
            highC = high,
            lowC = low,
            hours = parseHours(json.optJSONObject("hourly"))
        )
    }

    private fun parseHours(hourly: JSONObject?): List<WeatherHour> {
        if (hourly == null) return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code") ?: return emptyList()
        val now = System.currentTimeMillis()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val zone = ZoneId.systemDefault()
        val upcoming = ArrayList<WeatherHour>(times.length())
        for (i in 0 until times.length()) {
            val stamp = runCatching {
                LocalDateTime.parse(times.optString(i), formatter).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull() ?: continue
            if (stamp + 45 * 60 * 1000 < now) continue
            upcoming += WeatherHour(
                epochMs = stamp,
                temperatureC = temps.optDouble(i).toInt(),
                code = codes.optInt(i)
            )
        }
        if (upcoming.isEmpty()) return emptyList()
        val picked = ArrayList<WeatherHour>(3)
        var nextAt = upcoming.first().epochMs
        for (hour in upcoming) {
            if (hour.epochMs + 20 * 60 * 1000 < nextAt) continue
            if (hour.epochMs >= nextAt - 20 * 60 * 1000) {
                picked += hour
                nextAt = hour.epochMs + 3L * 60 * 60 * 1000
            }
            if (picked.size == 3) break
        }
        return picked.ifEmpty { upcoming.take(3) }
    }

    private fun get(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

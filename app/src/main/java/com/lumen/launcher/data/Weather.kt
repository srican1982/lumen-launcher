package com.lumen.launcher.data

import java.util.Locale

data class WeatherHour(
    val epochMs: Long,
    val temperatureC: Int,
    val code: Int
) {
    fun temperatureLabel(locale: Locale = Locale.getDefault()): String =
        "${temperatureC.toDisplayTemp(locale)}°"
}

data class WeatherSnapshot(
    val temperatureC: Int,
    val summary: String,
    val place: String,
    val highC: Int? = null,
    val lowC: Int? = null,
    val hours: List<WeatherHour> = emptyList()
) {
    fun temperatureLabel(locale: Locale = Locale.getDefault()): String =
        "${temperatureC.toDisplayTemp(locale)}°"

    fun highLabel(locale: Locale = Locale.getDefault()): String? =
        highC?.let { "${it.toDisplayTemp(locale)}°" }

    fun lowLabel(locale: Locale = Locale.getDefault()): String? =
        lowC?.let { "${it.toDisplayTemp(locale)}°" }
}

fun Int.toDisplayTemp(locale: Locale = Locale.getDefault()): Int {
    val fahrenheit = locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
    return if (fahrenheit) this * 9 / 5 + 32 else this
}

object WeatherCodes {
    fun summary(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Mostly clear"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Storms"
        else -> "Local weather"
    }
}

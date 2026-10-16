package com.lumen.launcher.widgets

import android.content.Context
import java.util.TimeZone

data class WorldCity(val id: String, val name: String, val zoneId: String) {
    val zone: TimeZone get() = TimeZone.getTimeZone(zoneId)
}

/** Curated cities users can pick for the World Time widget (4 slots). */
object WidgetWorldCities {
    val catalog = listOf(
        WorldCity("la", "Los Angeles", "America/Los_Angeles"),
        WorldCity("nyc", "New York", "America/New_York"),
        WorldCity("chi", "Chicago", "America/Chicago"),
        WorldCity("den", "Denver", "America/Denver"),
        WorldCity("hon", "Honolulu", "Pacific/Honolulu"),
        WorldCity("van", "Vancouver", "America/Vancouver"),
        WorldCity("tor", "Toronto", "America/Toronto"),
        WorldCity("mex", "Mexico City", "America/Mexico_City"),
        WorldCity("sao", "São Paulo", "America/Sao_Paulo"),
        WorldCity("lon", "London", "Europe/London"),
        WorldCity("par", "Paris", "Europe/Paris"),
        WorldCity("ber", "Berlin", "Europe/Berlin"),
        WorldCity("rom", "Rome", "Europe/Rome"),
        WorldCity("mad", "Madrid", "Europe/Madrid"),
        WorldCity("ist", "Istanbul", "Europe/Istanbul"),
        WorldCity("cai", "Cairo", "Africa/Cairo"),
        WorldCity("dxb", "Dubai", "Asia/Dubai"),
        WorldCity("mum", "Mumbai", "Asia/Kolkata"),
        WorldCity("bkk", "Bangkok", "Asia/Bangkok"),
        WorldCity("sin", "Singapore", "Asia/Singapore"),
        WorldCity("hkg", "Hong Kong", "Asia/Hong_Kong"),
        WorldCity("sha", "Shanghai", "Asia/Shanghai"),
        WorldCity("sel", "Seoul", "Asia/Seoul"),
        WorldCity("tyo", "Tokyo", "Asia/Tokyo"),
        WorldCity("syd", "Sydney", "Australia/Sydney"),
        WorldCity("akl", "Auckland", "Pacific/Auckland")
    )

    private val defaults = listOf("la", "nyc", "lon", "tyo")
    private fun prefs(context: Context) = context.getSharedPreferences("lumen_widget_world_cities", Context.MODE_PRIVATE)

    fun read(context: Context, widgetId: Int): List<WorldCity> {
        val raw = prefs(context).getString("cities_$widgetId", null)
        val ids = raw?.split(',')?.filter { it.isNotBlank() }?.take(4)
            ?: defaults
        return ids.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            .ifEmpty { defaults.mapNotNull { id -> catalog.firstOrNull { it.id == id } } }
            .take(4)
    }

    fun set(context: Context, widgetId: Int, slot: Int, city: WorldCity?) {
        val current = read(context, widgetId).toMutableList()
        while (current.size < 4) current += catalog.first { it.id == defaults[current.size] }
        if (city == null) {
            // Keep four rows; replace with next unused default if cleared.
            val unused = defaults.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
                .firstOrNull { candidate -> current.none { it.id == candidate.id } }
                ?: catalog.first { c -> current.none { it.id == c.id } }
            current[slot.coerceIn(0, 3)] = unused
        } else {
            current[slot.coerceIn(0, 3)] = city
        }
        prefs(context).edit().putString("cities_$widgetId", current.joinToString(",") { it.id }).apply()
    }

    fun setAll(context: Context, widgetId: Int, cities: List<WorldCity>) {
        val ids = cities.take(4).map { it.id }.ifEmpty { defaults }
        prefs(context).edit().putString("cities_$widgetId", ids.joinToString(",")).apply()
    }

    fun remove(context: Context, widgetIds: IntArray) {
        val edit = prefs(context).edit()
        widgetIds.forEach { edit.remove("cities_$it") }
        edit.apply()
    }
}

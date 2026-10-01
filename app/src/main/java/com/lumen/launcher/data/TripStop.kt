package com.lumen.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class TripStopCategory(val title: String) {
    Airport("Airport"),
    Hotel("Hotel"),
    Restaurant("Restaurant"),
    Attraction("Attraction"),
    Saved("Saved place"),
    Other("Other")
}

/**
 * A planned place in the Travel journey. Navigation uses these — not a generic Maps shortcut.
 */
data class TripStop(
    val id: String = UUID.randomUUID().toString(),
    val place: String,
    /** Maps query / address (defaults to [place]). */
    val query: String = place,
    val category: TripStopCategory = TripStopCategory.Other,
    /** Epoch ms for scheduled time; null = unscheduled saved place. */
    val at: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** User pinned this as the next stop. */
    val markedNext: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object TripStops {
    fun decode(raw: String?): List<TripStop> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val place = item.optString("place").trim()
                if (place.isBlank()) return@mapNotNull null
                TripStop(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    place = place,
                    query = item.optString("query").ifBlank { place },
                    category = TripStopCategory.entries.firstOrNull {
                        it.name == item.optString("category")
                    } ?: TripStopCategory.Other,
                    at = item.optLong("at").takeIf { item.has("at") && it > 0L },
                    latitude = item.optDouble("lat").takeIf { item.has("lat") && !it.isNaN() },
                    longitude = item.optDouble("lng").takeIf { item.has("lng") && !it.isNaN() },
                    markedNext = item.optBoolean("markedNext"),
                    createdAt = item.optLong("createdAt").takeIf { it > 0L } ?: 0L
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encode(items: List<TripStop>): String = JSONArray().apply {
        items.forEach { stop ->
            val obj = JSONObject()
                .put("id", stop.id)
                .put("place", stop.place)
                .put("query", stop.query)
                .put("category", stop.category.name)
                .put("at", stop.at ?: 0L)
                .put("markedNext", stop.markedNext)
                .put("createdAt", stop.createdAt)
            if (stop.latitude != null) obj.put("lat", stop.latitude)
            if (stop.longitude != null) obj.put("lng", stop.longitude)
            put(obj)
        }
    }.toString()

    fun merge(existing: List<TripStop>, add: List<TripStop>, removeId: String? = null): List<TripStop> {
        val base = existing.filterNot { it.id == removeId }
        val byId = base.associateBy { it.id }.toMutableMap()
        add.forEach { byId[it.id] = it }
        return byId.values.sortedWith(
            compareBy<TripStop> { it.at ?: Long.MAX_VALUE }.thenBy { it.createdAt }
        )
    }
}

object TripStopNavigator {
    private const val GRACE_MS = 20L * 60_000L

    /**
     * Priority:
     * 1. Upcoming timed stop (soonest at ≥ now − grace)
     * 2. Marked next
     * 3. Today's hotel / airport / attraction
     * 4. Last manually selected id
     * 5. null → ask the user
     */
    fun nextStop(
        now: Long = System.currentTimeMillis(),
        stops: List<TripStop>,
        lastSelectedId: String? = null
    ): TripStop? {
        if (stops.isEmpty()) return null

        val upcoming = stops
            .filter { it.at != null && it.at >= now - GRACE_MS }
            .minByOrNull { it.at!! }
        if (upcoming != null) return upcoming

        stops.firstOrNull { it.markedNext }?.let { return it }

        val todayCats = setOf(
            TripStopCategory.Hotel,
            TripStopCategory.Airport,
            TripStopCategory.Attraction
        )
        val todayPlace = stops
            .filter { it.category in todayCats && isSameDay(it.at ?: it.createdAt, now) }
            .minByOrNull { it.at ?: it.createdAt }
        if (todayPlace != null) return todayPlace

        lastSelectedId?.let { id -> stops.firstOrNull { it.id == id } }?.let { return it }

        return stops.firstOrNull { isSameDay(it.at ?: it.createdAt, now) }
            ?: stops.minByOrNull { it.at ?: Long.MAX_VALUE }
    }

    fun todaysRoute(now: Long, stops: List<TripStop>): List<TripStop> {
        val day = stops.filter { stop ->
            val t = stop.at ?: stop.createdAt
            isSameDay(t, now) || (stop.at == null && stop.markedNext)
        }
        return day.sortedWith(
            compareBy<TripStop> { it.at ?: Long.MAX_VALUE }.thenBy { it.createdAt }
        )
    }

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}

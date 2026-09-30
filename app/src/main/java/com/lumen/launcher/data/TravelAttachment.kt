package com.lumen.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

enum class TravelCategory(val title: String, val description: String) {
    Flights("Flights / Boarding passes", "Boarding passes and check-in details"),
    Hotels("Hotels", "Bookings and reservation details"),
    Tickets("Tickets", "Rail, events and attractions"),
    Itinerary("Itinerary", "Trip plans and travel schedules"),
    Other("Other documents", "Visas, insurance and confirmations")
}

data class TravelAttachment(val uri: String, val title: String, val mime: String, val itinerary: Boolean = false,
    val category: TravelCategory = TravelCategory.Flights) {
    val travelCategory: TravelCategory get() = if (itinerary) TravelCategory.Itinerary else category
}

object TravelAttachments {
    fun decode(raw: String?, legacyTicket: String = ""): List<TravelAttachment> {
        if (raw == null) return if (legacyTicket.isBlank()) emptyList() else listOf(TravelAttachment(legacyTicket, "Saved ticket", ""))
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                TravelAttachment(item.getString("uri"), item.getString("title"), item.optString("mime"), item.optBoolean("itinerary"),
                    TravelCategory.entries.firstOrNull { it.name == item.optString("category") } ?: TravelCategory.Flights)
            }
        }.getOrDefault(emptyList())
    }
    fun encode(items: List<TravelAttachment>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().put("uri", item.uri).put("title", item.title).put("mime", item.mime).put("itinerary", item.itinerary).put("category", item.category.name)) }
    }.toString()
    fun webLink(input: String): String? = runCatching {
        val value = input.trim().let { if ("://" in it) it else "https://$it" }
        val uri = URI(value)
        value.takeIf { uri.scheme.lowercase() in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null }
    }.getOrNull()
    fun merge(existing: List<TravelAttachment>, added: List<TravelAttachment>) =
        (existing + added).distinctBy { it.travelCategory to it.uri }
}

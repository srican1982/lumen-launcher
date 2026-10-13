package com.lumen.launcher.travel.data

import org.json.JSONArray
import org.json.JSONObject

/** Versioned metadata only. Local MediaStore IDs and URIs are deliberately never exported. */
object TripArchiveCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    data class Album(val trip: TripEntity, val photos: List<TripPhotoEntity>)
    data class Archive(val exportedAt: Long, val albums: List<Album>)

    fun restoredTrip(album: Album, exportedAt: Long): TripEntity {
        val trip = album.trip
        val end = trip.endTime ?: maxOf(trip.startTime, exportedAt,
            album.photos.maxOfOrNull { it.dateTaken } ?: trip.startTime)
        return trip.copy(id = 0, isActive = false, endTime = end)
    }

    fun encode(archive: Archive): String = JSONObject().apply {
        put("format", "lumen-trip")
        put("version", 1)
        put("exportedAt", archive.exportedAt)
        put("trips", JSONArray().apply { archive.albums.forEach { album ->
            put(JSONObject().apply {
                val t = album.trip
                put("startTime", t.startTime); put("endTime", t.endTime)
                put("createdAt", t.createdAt); put("title", t.title); put("wasActive", t.isActive)
                put("countryCode", t.countryCode); put("countryName", t.countryName); put("primaryCity", t.primaryCity)
                put("startLatitude", t.startLatitude); put("startLongitude", t.startLongitude)
                put("photos", JSONArray().apply { album.photos.forEach { p ->
                    put(JSONObject().apply {
                        put("displayName", p.displayName); put("dateTaken", p.dateTaken); put("addedAt", p.addedAt)
                        put("relativePath", p.relativePath); put("removed", p.removed)
                        put("latitude", p.latitude); put("longitude", p.longitude)
                        put("countryCode", p.countryCode); put("countryName", p.countryName); put("city", p.city)
                        put("locality", p.locality); put("subAdminArea", p.subAdminArea); put("adminArea", p.adminArea)
                    })
                } })
            })
        } })
    }.toString(2)

    fun decode(text: String): Archive {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Trip backup is too large" }
        val root = JSONObject(text)
        require(root.getString("format") == "lumen-trip" && root.getInt("version") == 1) { "Unsupported trip backup" }
        val exportedAt = root.getLong("exportedAt")
        require(exportedAt > 0)
        val albums = root.getJSONArray("trips")
        require(albums.length() <= 5000)
        var photoCount = 0
        return Archive(exportedAt, (0 until albums.length()).map { i ->
            val t = albums.getJSONObject(i)
            val start = t.getLong("startTime")
            val end = t.number("endTime")?.toLong()
            require(start > 0 && (end == null || end >= start)) { "Invalid trip dates" }
            val title = t.getString("title")
            require(title.isNotBlank() && title.length <= 1000)
            val photos = t.getJSONArray("photos")
            photoCount += photos.length(); require(photoCount <= 100000)
            Album(TripEntity(startTime = start, endTime = end, createdAt = t.getLong("createdAt"),
                title = title, isActive = t.optBoolean("wasActive"), countryCode = t.string("countryCode"),
                countryName = t.string("countryName"), primaryCity = t.string("primaryCity"),
                startLatitude = t.number("startLatitude"), startLongitude = t.number("startLongitude")),
                (0 until photos.length()).map { j ->
                    val p = photos.getJSONObject(j)
                    val taken = p.getLong("dateTaken"); require(taken > 0)
                    TripPhotoEntity(tripId = 0, mediaStoreId = 0, contentUri = "", displayName = p.string("displayName"),
                        dateTaken = taken, addedAt = p.getLong("addedAt"), relativePath = p.string("relativePath"), removed = p.optBoolean("removed"),
                        latitude = p.number("latitude"), longitude = p.number("longitude"), countryCode = p.string("countryCode"),
                        countryName = p.string("countryName"), city = p.string("city"), locality = p.string("locality"),
                        subAdminArea = p.string("subAdminArea"), adminArea = p.string("adminArea"))
                })
        })
    }
    private fun JSONObject.string(key: String): String? = if (isNull(key)) null else getString(key).also { require(it.length <= 4096) }
    private fun JSONObject.number(key: String): Double? = if (isNull(key)) null else getDouble(key).also { require(it.isFinite()) }
}

/** Ambiguous matches are kept missing rather than connecting to an unrelated memory. */
object TripPhotoReconnect {
    fun <T> match(key: Pair<String, Long>?, index: Map<Pair<String, Long>?, List<T>>): T? =
        key?.let { index[it]?.singleOrNull() }
    fun key(name: String?, taken: Long): Pair<String, Long>? = name?.takeIf { it.isNotBlank() }?.let { it to (taken / 1000L) }
}

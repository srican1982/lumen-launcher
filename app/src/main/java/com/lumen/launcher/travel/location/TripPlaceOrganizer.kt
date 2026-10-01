package com.lumen.launcher.travel.location

import com.lumen.launcher.travel.model.PlaceSection
import com.lumen.launcher.travel.model.TripPhoto

/**
 * Trip owns photos. Location only forms **optional place sections** after evidence.
 *
 * Rules:
 * - Start-zone grace: photos near where Trip Mode turned on never become a place.
 * - Metro snap: suburbs collapse into one major destination (Chula Vista → San Diego).
 * - Significance: ≥[minPhotos] in a metro, or dwell ≥[minDwellMs].
 */
object TripPlaceOrganizer {
    /** ~20 miles — departure / drive-out area, not a destination. */
    const val START_ZONE_KM = 32.0
    /** Minimum photos in a metro before it becomes a place section. */
    const val MIN_PHOTOS = 3
    /** Or remain in that metro this long (ms). */
    const val MIN_DWELL_MS = 25L * 60_000L
    /** Separate destination sections when metro centers are this far apart. */
    const val DEST_SEPARATION_KM = 40.0

    data class Result(
        val sections: List<PlaceSection>,
        /** Best auto title, e.g. "San Diego Trip", or null to keep Current Trip. */
        val suggestedTitle: String?,
        val dominantPlace: String?,
        val countryCode: String?,
        val countryName: String?
    )

    private data class Tagged(
        val photo: TripPhoto,
        val lat: Double,
        val lng: Double,
        val metro: String,
        val inStartZone: Boolean
    )

    fun organize(
        photos: List<TripPhoto>,
        startLat: Double?,
        startLng: Double?
    ): Result {
        if (photos.isEmpty()) {
            return Result(emptyList(), null, null, null, null)
        }

        val tagged = photos.mapNotNull { photo ->
            val lat = photo.latitude ?: return@mapNotNull null
            val lng = photo.longitude ?: return@mapNotNull null
            val metro = MajorCities.nearest(lat, lng, photo.countryCode)?.name
                ?: photo.city?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val inStart = startLat != null && startLng != null &&
                MajorCities.distanceKm(lat, lng, startLat, startLng) <= START_ZONE_KM
            Tagged(photo, lat, lng, metro, inStart)
        }

        val byMetro = tagged
            .filter { !it.inStartZone }
            .groupBy { it.metro }

        val rawSections = mutableListOf<PlaceSection>()
        for ((metro, group) in byMetro) {
            if (!isSignificant(group)) continue
            val centerLat = group.map { it.lat }.average()
            val centerLng = group.map { it.lng }.average()
            if (startLat != null && startLng != null &&
                MajorCities.distanceKm(centerLat, centerLng, startLat, startLng) <= START_ZONE_KM
            ) {
                continue
            }
            val ordered = group.sortedBy { it.photo.dateTaken }
            rawSections += PlaceSection(
                label = metro,
                count = group.size,
                photoIds = ordered.map { it.photo.id },
                centerLat = centerLat,
                centerLng = centerLng,
                firstTaken = ordered.first().photo.dateTaken,
                lastTaken = ordered.last().photo.dateTaken
            )
        }

        val merged = mergeNearby(rawSections)
        val claimed = merged.flatMap { it.photoIds }.toSet()
        val unclustered = photos.filter { it.id !in claimed }
        val other = if (unclustered.isNotEmpty()) {
            listOf(
                PlaceSection(
                    label = "Other trip photos",
                    count = unclustered.size,
                    photoIds = unclustered.sortedByDescending { it.dateTaken }.map { it.id },
                    isOther = true
                )
            )
        } else {
            emptyList()
        }

        val destinations = merged.sortedWith(
            compareByDescending<PlaceSection> { it.count }.thenBy { it.firstTaken ?: 0L }
        )
        val countryCode = photos.mapNotNull { it.countryCode }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        val countryName = photos.mapNotNull { it.countryName }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

        return Result(
            sections = destinations + other,
            suggestedTitle = suggestTitle(destinations),
            dominantPlace = destinations.firstOrNull()?.label,
            countryCode = countryCode,
            countryName = countryName
        )
    }

    private fun isSignificant(group: List<Tagged>): Boolean {
        if (group.size >= MIN_PHOTOS) return true
        if (group.isEmpty()) return false
        val minT = group.minOf { it.photo.dateTaken }
        val maxT = group.maxOf { it.photo.dateTaken }
        return (maxT - minT) >= MIN_DWELL_MS
    }

    private fun mergeNearby(sections: List<PlaceSection>): List<PlaceSection> {
        if (sections.size <= 1) return sections
        val remaining = sections.sortedByDescending { it.count }.toMutableList()
        val out = mutableListOf<PlaceSection>()
        while (remaining.isNotEmpty()) {
            val base = remaining.removeAt(0)
            val bLat = base.centerLat
            val bLng = base.centerLng
            val absorbed = mutableListOf<PlaceSection>()
            if (bLat != null && bLng != null) {
                val it = remaining.iterator()
                while (it.hasNext()) {
                    val other = it.next()
                    val oLat = other.centerLat ?: continue
                    val oLng = other.centerLng ?: continue
                    if (MajorCities.distanceKm(bLat, bLng, oLat, oLng) <= DEST_SEPARATION_KM) {
                        absorbed += other
                        it.remove()
                    }
                }
            }
            if (absorbed.isEmpty()) {
                out += base
            } else {
                val all = listOf(base) + absorbed
                val ids = all.flatMap { it.photoIds }
                out += PlaceSection(
                    label = base.label,
                    count = ids.size,
                    photoIds = ids,
                    centerLat = all.mapNotNull { it.centerLat }.average().takeIf { !it.isNaN() },
                    centerLng = all.mapNotNull { it.centerLng }.average().takeIf { !it.isNaN() },
                    firstTaken = all.mapNotNull { it.firstTaken }.minOrNull(),
                    lastTaken = all.mapNotNull { it.lastTaken }.maxOrNull()
                )
            }
        }
        return out
    }

    private fun suggestTitle(destinations: List<PlaceSection>): String? {
        if (destinations.isEmpty()) return null
        return when (destinations.size) {
            1 -> "${destinations[0].label} Trip"
            2 -> "${destinations[0].label} + ${destinations[1].label}"
            else -> destinations.take(2).joinToString(" + ") { it.label } + " Trip"
        }
    }

    /** Whether an auto title may still be overwritten by inference. */
    fun isAutoTitle(title: String): Boolean {
        val t = title.trim()
        if (t.equals("Current Trip", ignoreCase = true)) return true
        if (t.endsWith(" Trip", ignoreCase = true)) return true
        if (t.contains(" + ")) return true
        return false
    }
}

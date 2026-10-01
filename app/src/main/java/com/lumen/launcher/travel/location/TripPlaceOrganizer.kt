package com.lumen.launcher.travel.location

import com.lumen.launcher.travel.model.PlaceSection
import com.lumen.launcher.travel.model.TripPhoto

/**
 * Trip owns photos. Location forms optional place sections via:
 * 1) distance clustering on GPS
 * 2) admin hierarchy labels from reverse geocoding (locality → district → province → country)
 *
 * No worldwide city hardcoding required.
 */
object TripPlaceOrganizer {
    /** ~20 miles — departure area where Trip Mode was turned on. */
    const val START_ZONE_KM = 32.0
    /** Photos within this distance can join the same destination cluster. */
    const val CLUSTER_KM = 35.0
    const val MIN_PHOTOS = 3
    const val MIN_DWELL_MS = 25L * 60_000L

    data class Result(
        val sections: List<PlaceSection>,
        val suggestedTitle: String?,
        val dominantPlace: String?,
        val countryCode: String?,
        val countryName: String?
    )

    private data class Point(
        val photo: TripPhoto,
        val lat: Double,
        val lng: Double,
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

        val points = photos.mapNotNull { photo ->
            val lat = photo.latitude ?: return@mapNotNull null
            val lng = photo.longitude ?: return@mapNotNull null
            val inStart = startLat != null && startLng != null &&
                GeoMath.distanceKm(lat, lng, startLat, startLng) <= START_ZONE_KM
            Point(photo, lat, lng, inStart)
        }

        val candidates = points.filter { !it.inStartZone }
        val clusters = cluster(candidates).filter { isSignificant(it) }.mapNotNull { group ->
            val centerLat = group.map { it.lat }.average()
            val centerLng = group.map { it.lng }.average()
            if (startLat != null && startLng != null &&
                GeoMath.distanceKm(centerLat, centerLng, startLat, startLng) <= START_ZONE_KM
            ) {
                return@mapNotNull null
            }
            val label = labelFor(group) ?: return@mapNotNull null
            val ordered = group.sortedBy { it.photo.dateTaken }
            PlaceSection(
                label = label,
                count = group.size,
                photoIds = ordered.map { it.photo.id },
                centerLat = centerLat,
                centerLng = centerLng,
                firstTaken = ordered.first().photo.dateTaken,
                lastTaken = ordered.last().photo.dateTaken
            )
        }

        // Merge same-label sections (e.g. two clusters both "Colombo area").
        val destinations = mergeSameLabels(clusters).sortedWith(
            compareByDescending<PlaceSection> { it.count }.thenBy { it.firstTaken ?: 0L }
        )

        val claimed = destinations.flatMap { it.photoIds }.toSet()
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

        val countryCode = photos.mapNotNull { it.countryCode }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        val countryName = photos.mapNotNull { it.countryName }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        val countries = destinations.mapNotNull { section ->
            photos.filter { it.id in section.photoIds.toSet() }
                .mapNotNull { it.countryName?.takeIf(String::isNotBlank) }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }.distinct()

        return Result(
            sections = destinations + other,
            suggestedTitle = suggestTitle(destinations, countries, countryName),
            dominantPlace = destinations.firstOrNull()?.let { AdminPlaceLabeler.titlePlace(it.label) },
            countryCode = countryCode,
            countryName = countryName
        )
    }

    private fun cluster(points: List<Point>): List<List<Point>> {
        if (points.isEmpty()) return emptyList()
        val parent = IntArray(points.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x]
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                if (GeoMath.distanceKm(
                        points[i].lat, points[i].lng,
                        points[j].lat, points[j].lng
                    ) <= CLUSTER_KM
                ) {
                    union(i, j)
                }
            }
        }
        return points.indices.groupBy { find(it) }.values.map { idxs -> idxs.map { points[it] } }
    }

    private fun isSignificant(group: List<Point>): Boolean {
        if (group.size >= MIN_PHOTOS) return true
        if (group.isEmpty()) return false
        return group.maxOf { it.photo.dateTaken } - group.minOf { it.photo.dateTaken } >= MIN_DWELL_MS
    }

    private fun labelFor(group: List<Point>): String? {
        val levels = group.map {
            AdminPlaceLabeler.AdminLevels(
                locality = it.photo.locality,
                subAdminArea = it.photo.subAdminArea,
                adminArea = it.photo.adminArea,
                countryName = it.photo.countryName,
                countryCode = it.photo.countryCode
            )
        }
        AdminPlaceLabeler.labelCluster(levels)?.let { return it }
        // Last resort: majority city tag from geocoder / legacy field.
        return group.mapNotNull { it.photo.city?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    private fun mergeSameLabels(sections: List<PlaceSection>): List<PlaceSection> {
        if (sections.size <= 1) return sections
        return sections.groupBy { it.label.lowercase() }.map { (_, group) ->
            if (group.size == 1) group.first()
            else {
                val ids = group.flatMap { it.photoIds }
                PlaceSection(
                    label = group.maxBy { it.count }.label,
                    count = ids.size,
                    photoIds = ids,
                    centerLat = group.mapNotNull { it.centerLat }.average().takeIf { !it.isNaN() },
                    centerLng = group.mapNotNull { it.centerLng }.average().takeIf { !it.isNaN() },
                    firstTaken = group.mapNotNull { it.firstTaken }.minOrNull(),
                    lastTaken = group.mapNotNull { it.lastTaken }.maxOrNull()
                )
            }
        }
    }

    private fun suggestTitle(
        destinations: List<PlaceSection>,
        clusterCountries: List<String>,
        fallbackCountry: String?
    ): String? {
        if (destinations.isEmpty()) return null
        if (destinations.size == 1) {
            return "${AdminPlaceLabeler.titlePlace(destinations[0].label)} Trip"
        }
        val countries = clusterCountries.ifEmpty {
            listOfNotNull(fallbackCountry?.takeIf { it.isNotBlank() })
        }
        return when {
            countries.size == 1 -> "${countries[0]} Trip"
            countries.size == 2 -> "${countries[0]} + ${countries[1]}"
            countries.size > 2 -> countries.take(2).joinToString(" + ")
            else -> destinations.take(2).joinToString(" + ") { AdminPlaceLabeler.titlePlace(it.label) }
        }
    }

    fun isAutoTitle(title: String): Boolean {
        val t = title.trim()
        if (t.equals("Current Trip", ignoreCase = true)) return true
        if (t.endsWith(" Trip", ignoreCase = true)) return true
        if (t.contains(" + ")) return true
        return false
    }
}

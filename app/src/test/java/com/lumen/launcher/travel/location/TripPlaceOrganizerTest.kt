package com.lumen.launcher.travel.location

import com.lumen.launcher.travel.model.TripPhoto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TripPlaceOrganizerTest {

    private fun photo(
        id: Long,
        lat: Double,
        lng: Double,
        city: String,
        takenOffsetMin: Long,
        country: String = "US"
    ) = TripPhoto(
        id = id,
        tripId = 1,
        mediaStoreId = id,
        contentUri = "content://$id",
        dateTaken = 1_700_000_000_000L + takenOffsetMin * 60_000L,
        latitude = lat,
        longitude = lng,
        countryCode = country,
        countryName = "United States",
        city = city,
        addedAt = 1_700_000_000_000L
    )

    @Test
    fun startZonePhotosDoNotBecomePlaceSections() {
        // Trip started in Cerritos
        val startLat = 33.8583
        val startLng = -118.0648
        val photos = listOf(
            photo(1, 33.8583, -118.0648, "Cerritos", 0),
            photo(2, 33.9022, -118.0817, "Norwalk", 2),
            photo(3, 33.9400, -118.1330, "Downey", 5)
        )
        val result = TripPlaceOrganizer.organize(photos, startLat, startLng)
        assertThat(result.sections.filterNot { it.isOther }).isEmpty()
        assertThat(result.suggestedTitle).isNull()
        assertThat(result.sections.single { it.isOther }.count).isEqualTo(3)
    }

    @Test
    fun sanDiegoSuburbsCollapseToOneDestination() {
        val startLat = 33.8583 // Cerritos
        val startLng = -118.0648
        val photos = listOf(
            photo(1, 33.8583, -118.0648, "Cerritos", 0),
            photo(2, 32.7157, -117.1611, "San Diego", 120),
            photo(3, 32.6401, -117.0842, "Chula Vista", 130),
            photo(4, 32.8328, -117.2713, "La Jolla", 140),
            photo(5, 32.6859, -117.1831, "Coronado", 150)
        )
        val result = TripPlaceOrganizer.organize(photos, startLat, startLng)
        val dest = result.sections.filterNot { it.isOther }
        assertThat(dest).hasSize(1)
        assertThat(dest[0].label).isEqualTo("San Diego")
        assertThat(dest[0].count).isEqualTo(4)
        assertThat(result.suggestedTitle).isEqualTo("San Diego Trip")
    }

    @Test
    fun farSecondMetroBecomesSeparateSection() {
        val startLat = 33.8583
        val startLng = -118.0648
        val photos = (1..4).map {
            photo(it.toLong(), 32.7157 + it * 0.01, -117.1611, "San Diego", 60L + it)
        } + (5..8).map {
            photo(it.toLong(), 34.0522 + it * 0.01, -118.2437, "Los Angeles", 400L + it)
        }
        val result = TripPlaceOrganizer.organize(photos, startLat, startLng)
        val dest = result.sections.filterNot { it.isOther }.map { it.label }
        assertThat(dest).containsAtLeast("San Diego", "Los Angeles")
    }
}

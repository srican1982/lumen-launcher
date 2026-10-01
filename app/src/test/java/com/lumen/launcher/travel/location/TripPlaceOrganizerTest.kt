package com.lumen.launcher.travel.location

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.travel.model.TripPhoto
import org.junit.Test

class TripPlaceOrganizerTest {

    private fun photo(
        id: Long,
        lat: Double,
        lng: Double,
        locality: String?,
        subAdmin: String?,
        admin: String?,
        country: String = "Sri Lanka",
        countryCode: String = "LK",
        takenOffsetMin: Long = 0
    ) = TripPhoto(
        id = id,
        tripId = 1,
        mediaStoreId = id,
        contentUri = "content://$id",
        dateTaken = 1_700_000_000_000L + takenOffsetMin * 60_000L,
        latitude = lat,
        longitude = lng,
        countryCode = countryCode,
        countryName = country,
        city = locality,
        locality = locality,
        subAdminArea = subAdmin,
        adminArea = admin,
        addedAt = 1_700_000_000_000L
    )

    @Test
    fun sriLankaClustersBecomeCountryTripWithRegionalSections() {
        // Start far from both regions (e.g. abroad / other city).
        val startLat = 1.35
        val startLng = 103.8
        val photos = (1..10).map {
            photo(
                id = it.toLong(),
                lat = 6.2330 + it * 0.001,
                lng = 81.3310,
                locality = "Kirinda",
                subAdmin = "Hambantota District",
                admin = "Southern Province",
                takenOffsetMin = it.toLong()
            )
        } + (11..20).map {
            photo(
                id = it.toLong(),
                lat = 6.1244 + (it - 10) * 0.001,
                lng = 81.1185,
                locality = "Hambantota",
                subAdmin = "Hambantota District",
                admin = "Southern Province",
                takenOffsetMin = 60L + it
            )
        } + (21..30).map {
            photo(
                id = it.toLong(),
                lat = 6.8900 + (it - 20) * 0.001,
                lng = 79.9800,
                locality = "Hokandara",
                subAdmin = "Colombo District",
                admin = "Western Province",
                takenOffsetMin = 200L + it
            )
        } + (31..40).map {
            photo(
                id = it.toLong(),
                lat = 6.9147 + (it - 30) * 0.001,
                lng = 79.9735,
                locality = "Malabe",
                subAdmin = "Colombo District",
                admin = "Western Province",
                takenOffsetMin = 260L + it
            )
        }

        val result = TripPlaceOrganizer.organize(photos, startLat, startLng)
        val dest = result.sections.filterNot { it.isOther }

        assertThat(result.suggestedTitle).isEqualTo("Sri Lanka Trip")
        assertThat(dest.map { it.label }).containsAtLeast("Hambantota area", "Colombo area")
        assertThat(dest).hasSize(2)
        assertThat(dest.sumOf { it.count }).isEqualTo(40)
    }

    @Test
    fun singleRegionBecomesRegionTrip() {
        val photos = (1..5).map {
            photo(
                id = it.toLong(),
                lat = 32.7157 + it * 0.01,
                lng = -117.1611,
                locality = if (it % 2 == 0) "La Jolla" else "Chula Vista",
                subAdmin = "San Diego County",
                admin = "California",
                country = "United States",
                countryCode = "US",
                takenOffsetMin = it.toLong() * 10
            )
        }
        val result = TripPlaceOrganizer.organize(photos, startLat = 34.0, startLng = -118.0)
        val dest = result.sections.filterNot { it.isOther }
        assertThat(dest).hasSize(1)
        assertThat(dest[0].label).isEqualTo("San Diego area")
        assertThat(result.suggestedTitle).isEqualTo("San Diego Trip")
    }

    @Test
    fun startZonePhotosStayUnsectioned() {
        val startLat = 33.8583
        val startLng = -118.0648
        val photos = listOf(
            photo(1, 33.8583, -118.0648, "Cerritos", "Los Angeles County", "California", "United States", "US", 0),
            photo(2, 33.9022, -118.0817, "Norwalk", "Los Angeles County", "California", "United States", "US", 2),
            photo(3, 33.9400, -118.1330, "Downey", "Los Angeles County", "California", "United States", "US", 5)
        )
        val result = TripPlaceOrganizer.organize(photos, startLat, startLng)
        assertThat(result.sections.filterNot { it.isOther }).isEmpty()
        assertThat(result.suggestedTitle).isNull()
    }
}

class AdminPlaceLabelerTest {
    @Test
    fun choosesSharedDistrictOverLocalities() {
        val label = AdminPlaceLabeler.labelCluster(
            listOf(
                AdminPlaceLabeler.AdminLevels("Malabe", "Colombo District", "Western Province", "Sri Lanka", "LK"),
                AdminPlaceLabeler.AdminLevels("Hokandara", "Colombo District", "Western Province", "Sri Lanka", "LK")
            )
        )
        assertThat(label).isEqualTo("Colombo area")
    }
}

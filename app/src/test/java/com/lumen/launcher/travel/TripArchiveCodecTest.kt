package com.lumen.launcher.travel

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.travel.data.*
import org.json.JSONObject
import org.junit.Test

class TripArchiveCodecTest {
    private val trip = TripEntity(id = 40, startTime = 1000, endTime = 9000, title = "Japan 日本", isActive = false,
        createdAt = 1000, countryName = "Japan", startLatitude = 35.0, startLongitude = 139.0)
    private val photo = TripPhotoEntity(id = 32, tripId = 40, mediaStoreId = 4321, contentUri = "content://old-device/4321",
        displayName = "IMG_001.jpg", dateTaken = 2000, city = "Tokyo", locality = "Shibuya", latitude = 35.0,
        longitude = 139.0, addedAt = 2000, removed = true)
    private fun encode() = TripArchiveCodec.encode(TripArchiveCodec.Archive(10000, listOf(TripArchiveCodec.Album(trip, listOf(photo)))))

    @Test fun metadataRoundTripPreservesPlacesAndExclusionsWithoutLocalIds() {
        val json = encode()
        assertThat(json).doesNotContain("content://")
        assertThat(json).doesNotContain("mediaStoreId")
        val album = TripArchiveCodec.decode(json).albums.single()
        assertThat(album.trip.title).isEqualTo("Japan 日本")
        assertThat(album.trip.id).isEqualTo(0)
        assertThat(album.photos.single().locality).isEqualTo("Shibuya")
        assertThat(album.photos.single().removed).isTrue()
        assertThat(album.photos.single().contentUri).isEmpty()
        assertThat(album.photos.single().dateTaken).isEqualTo(2000)
    }
    @Test fun ongoingTripRestoresAsHistoricalWithoutStartingPhotoCapture() {
        val album = TripArchiveCodec.Album(trip.copy(isActive = true, endTime = null), listOf(photo))
        val restored = TripArchiveCodec.restoredTrip(album, 10000)
        assertThat(restored.isActive).isFalse()
        assertThat(restored.id).isEqualTo(0)
        assertThat(restored.endTime).isEqualTo(10000)
        assertThat(restored.createdAt).isEqualTo(trip.createdAt)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsFutureSchema() {
        TripArchiveCodec.decode(JSONObject(encode()).put("version", 2).toString())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidDatesBeforeRestore() {
        val json = JSONObject(encode())
        json.getJSONArray("trips").getJSONObject(0).put("endTime", 500)
        TripArchiveCodec.decode(json.toString())
    }
    @Test fun reconnectHandlesCaptureTimesRoundedToWholeSeconds() {
        val key = TripPhotoReconnect.key("IMG_001.jpg", 2345)
        val index = mapOf(TripPhotoReconnect.key("IMG_001.jpg", 2000) to listOf("photo"))
        assertThat(TripPhotoReconnect.match(key, index)).isEqualTo("photo")
        assertThat(TripPhotoReconnect.match(key, mapOf(key to listOf("first", "second")))).isNull()
    }
    @Test fun reconnectDoesNotUseRecycledIdsOrAmbiguousMatches() {
        val key = TripPhotoReconnect.key("IMG_001.jpg", 2000)
        assertThat(TripPhotoReconnect.match(key, mapOf(key to listOf("new-uri")))).isEqualTo("new-uri")
        assertThat(TripPhotoReconnect.match(key, mapOf(key to listOf("one", "two")))).isNull()
        assertThat(TripPhotoReconnect.match(TripPhotoReconnect.key(null, 2000), mapOf(key to listOf("new-uri")))).isNull()
        assertThat(TripPhotoReconnect.match(TripPhotoReconnect.key("IMG_001.jpg", 3000), mapOf(key to listOf("wrong-uri")))).isNull()
    }
}

package com.lumen.launcher.travel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CityCountRow(val city: String?, val count: Int)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    fun observeActiveTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE isActive = 0 ORDER BY COALESCE(endTime, startTime) DESC")
    fun observePastTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: Long): Flow<TripEntity?>

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Query(
        """
        UPDATE trips SET endTime = :endTime, isActive = 0 WHERE id = :tripId
        """
    )
    suspend fun endTrip(tripId: Long, endTime: Long)

    @Query(
        """
        UPDATE trips SET countryCode = :countryCode, countryName = :countryName,
        primaryCity = :primaryCity, title = :title WHERE id = :tripId
        """
    )
    suspend fun updateTripLocation(
        tripId: Long,
        countryCode: String?,
        countryName: String?,
        primaryCity: String?,
        title: String
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTripPhoto(photo: TripPhotoEntity): Long

    @Query("""UPDATE trip_photos SET latitude = :latitude, longitude = :longitude,
        countryCode = :countryCode, countryName = :countryName, city = :city,
        locality = :locality, subAdminArea = :subAdminArea, adminArea = :adminArea
        WHERE id = :id""")
    suspend fun updatePhotoLocation(
        id: Long,
        latitude: Double?,
        longitude: Double?,
        countryCode: String?,
        countryName: String?,
        city: String?,
        locality: String? = null,
        subAdminArea: String? = null,
        adminArea: String? = null
    )

    @Query("SELECT COUNT(*) FROM trip_photos WHERE mediaStoreId = :mediaStoreId")
    suspend fun countByMediaStoreId(mediaStoreId: Long): Int

    @Query("SELECT COUNT(*) FROM trip_photos WHERE tripId = :tripId AND removed = 0")
    suspend fun getTripPhotoCount(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM trip_photos WHERE tripId = :tripId AND removed = 0")
    fun observeTripPhotoCount(tripId: Long): Flow<Int>

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId AND removed = 0 ORDER BY dateTaken DESC")
    suspend fun getTripPhotos(tripId: Long): List<TripPhotoEntity>

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId AND removed = 0 ORDER BY dateTaken DESC")
    fun observeTripPhotos(tripId: Long): Flow<List<TripPhotoEntity>>

    @Query(
        """
        SELECT contentUri FROM trip_photos
        WHERE tripId = :tripId AND removed = 0
        ORDER BY dateTaken DESC
        LIMIT :limit
        """
    )
    suspend fun latestPhotoUris(tripId: Long, limit: Int): List<String>

    @Query(
        """
        SELECT contentUri FROM trip_photos
        WHERE tripId = :tripId AND removed = 0
        ORDER BY dateTaken DESC
        LIMIT :limit
        """
    )
    fun observeLatestPhotoUris(tripId: Long, limit: Int): Flow<List<String>>

    @Query(
        """
        SELECT NULLIF(TRIM(city), '') AS city, COUNT(*) AS count FROM trip_photos
        WHERE tripId = :tripId AND removed = 0
        GROUP BY NULLIF(TRIM(city), '')
        ORDER BY count DESC
        """
    )
    fun observeCityCounts(tripId: Long): Flow<List<CityCountRow>>

    @Query(
        """
        SELECT NULLIF(TRIM(city), '') AS city, COUNT(*) AS count FROM trip_photos
        WHERE tripId = :tripId AND removed = 0
        GROUP BY NULLIF(TRIM(city), '')
        ORDER BY count DESC
        """
    )
    suspend fun getCityCounts(tripId: Long): List<CityCountRow>

    @Query("SELECT MAX(dateTaken) FROM trip_photos WHERE tripId = :tripId AND removed = 0")
    suspend fun lastProcessedTimestamp(tripId: Long): Long?

    @Query("UPDATE trip_photos SET removed = 1 WHERE id IN (:ids)")
    suspend fun deletePhotosByIds(ids: List<Long>): Int

    @Query("SELECT * FROM trip_photos WHERE removed = 0 AND id IN (:ids)")
    suspend fun getPhotosByIds(ids: List<Long>): List<TripPhotoEntity>
}

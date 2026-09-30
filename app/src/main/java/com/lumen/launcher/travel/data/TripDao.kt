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

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

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

    @Query("SELECT COUNT(*) FROM trip_photos WHERE mediaStoreId = :mediaStoreId")
    suspend fun countByMediaStoreId(mediaStoreId: Long): Int

    @Query("SELECT COUNT(*) FROM trip_photos WHERE tripId = :tripId")
    suspend fun getTripPhotoCount(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM trip_photos WHERE tripId = :tripId")
    fun observeTripPhotoCount(tripId: Long): Flow<Int>

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId ORDER BY dateTaken DESC")
    suspend fun getTripPhotos(tripId: Long): List<TripPhotoEntity>

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId ORDER BY dateTaken DESC")
    fun observeTripPhotos(tripId: Long): Flow<List<TripPhotoEntity>>

    @Query(
        """
        SELECT city AS city, COUNT(*) AS count FROM trip_photos
        WHERE tripId = :tripId
        GROUP BY city
        ORDER BY count DESC
        """
    )
    fun observeCityCounts(tripId: Long): Flow<List<CityCountRow>>

    @Query("SELECT MAX(dateTaken) FROM trip_photos WHERE tripId = :tripId")
    suspend fun lastProcessedTimestamp(tripId: Long): Long?
}

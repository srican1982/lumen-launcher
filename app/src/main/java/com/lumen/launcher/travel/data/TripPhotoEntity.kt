package com.lumen.launcher.travel.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lumen.launcher.travel.model.TripPhoto

@Entity(
    tableName = "trip_photos",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["tripId"]),
        Index(value = ["city"])
    ]
)
data class TripPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String? = null,
    val dateTaken: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val city: String? = null,
    val relativePath: String? = null,
    val addedAt: Long
) {
    fun toModel() = TripPhoto(
        id = id,
        tripId = tripId,
        mediaStoreId = mediaStoreId,
        contentUri = contentUri,
        displayName = displayName,
        dateTaken = dateTaken,
        latitude = latitude,
        longitude = longitude,
        countryCode = countryCode,
        countryName = countryName,
        city = city,
        relativePath = relativePath,
        addedAt = addedAt
    )
}

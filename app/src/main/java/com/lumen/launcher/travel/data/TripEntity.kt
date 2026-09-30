package com.lumen.launcher.travel.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lumen.launcher.travel.model.Trip

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val primaryCity: String? = null,
    val title: String,
    val isActive: Boolean,
    val createdAt: Long
) {
    fun toModel(
        photoCount: Int = 0,
        coverUri: String? = null,
        previewUris: List<String> = emptyList(),
        citiesLabel: String? = null
    ) = Trip(
        id = id,
        startTime = startTime,
        endTime = endTime,
        countryCode = countryCode,
        countryName = countryName,
        primaryCity = primaryCity,
        title = title,
        isActive = isActive,
        createdAt = createdAt,
        photoCount = photoCount,
        coverUri = coverUri,
        previewUris = previewUris,
        citiesLabel = citiesLabel
    )
}

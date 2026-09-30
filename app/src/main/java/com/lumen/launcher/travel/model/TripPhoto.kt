package com.lumen.launcher.travel.model

data class TripPhoto(
    val id: Long,
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
    val cityOrOther: String get() = city?.takeIf { it.isNotBlank() } ?: "Other"
}

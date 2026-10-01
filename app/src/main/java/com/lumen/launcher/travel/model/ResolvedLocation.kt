package com.lumen.launcher.travel.model

data class ResolvedLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    /** Best short place tag (usually locality). */
    val city: String? = null,
    val locality: String? = null,
    val subAdminArea: String? = null,
    val adminArea: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

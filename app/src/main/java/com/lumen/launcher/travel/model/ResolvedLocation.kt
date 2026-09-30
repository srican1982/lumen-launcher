package com.lumen.launcher.travel.model

data class ResolvedLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val city: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

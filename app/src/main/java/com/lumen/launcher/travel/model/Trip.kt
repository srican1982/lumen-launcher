package com.lumen.launcher.travel.model

data class Trip(
    val id: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val primaryCity: String? = null,
    val title: String,
    val isActive: Boolean,
    val createdAt: Long,
    val photoCount: Int = 0
) {
    val displayTitle: String
        get() = title.ifBlank {
            countryName?.let { "$it Trip" } ?: "Current Trip"
        }
}

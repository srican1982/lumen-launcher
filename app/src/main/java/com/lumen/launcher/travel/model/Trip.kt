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
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val photoCount: Int = 0,
    val coverUri: String? = null,
    val previewUris: List<String> = emptyList(),
    val citiesLabel: String? = null
) {
    val displayTitle: String
        get() = title.ifBlank {
            countryName?.takeIf { it.isNotBlank() } ?: "Current Trip"
        }

    val flagEmoji: String
        get() = countryFlagEmoji(countryCode)
}

/** Regional indicator emoji from ISO 3166-1 alpha-2, e.g. US → 🇺🇸 */
fun countryFlagEmoji(code: String?): String {
    val c = code?.trim()?.uppercase().orEmpty()
    if (c.length != 2 || !c.all { it in 'A'..'Z' }) return "✈️"
    val first = 0x1F1E6 + (c[0] - 'A')
    val second = 0x1F1E6 + (c[1] - 'A')
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

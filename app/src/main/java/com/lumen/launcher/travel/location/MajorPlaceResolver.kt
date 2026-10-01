package com.lumen.launcher.travel.location

import android.location.Address

/**
 * Picks a travel-scale place name: major cities / metros, not every suburb or ward.
 *
 * Priority:
 * 1. Nearest known major city within its metro radius (Cerritos → Los Angeles)
 * 2. Country-aware admin fields (JP prefecture, US county → metro)
 * 3. Locality only as last resort
 */
object MajorPlaceResolver {

    fun resolve(
        address: Address?,
        latitude: Double,
        longitude: Double
    ): String? {
        val countryCode = address?.countryCode?.trim()?.uppercase()
        MajorCities.nearest(latitude, longitude, countryCode)?.let { return it.name }

        if (address == null) return null

        countryAware(address, countryCode)?.let { return it }

        cleanAdmin(address.subAdminArea)?.let { return it }
        cleanAdmin(address.adminArea)?.let { return it }
        return address.locality?.trim()?.takeIf { it.isNotBlank() }
            ?: address.subLocality?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun countryAware(address: Address, countryCode: String?): String? {
        return when (countryCode) {
            // Prefectures / metro hubs are the right trip folder grain.
            "JP", "KR", "CN", "TW" -> normalizeAsianAdmin(address.adminArea)
                ?: cleanAdmin(address.locality)
            "US", "CA" -> {
                // "Los Angeles County" → "Los Angeles"
                stripCounty(address.subAdminArea)
                    ?: cleanAdmin(address.locality)
            }
            else -> null
        }
    }

    private fun normalizeAsianAdmin(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val map = mapOf(
            "東京都" to "Tokyo",
            "東京" to "Tokyo",
            "Tokyo" to "Tokyo",
            "大阪府" to "Osaka",
            "大阪" to "Osaka",
            "Osaka" to "Osaka",
            "京都府" to "Kyoto",
            "京都" to "Kyoto",
            "Kyoto" to "Kyoto",
            "神奈川県" to "Yokohama",
            "愛知県" to "Nagoya",
            "北海道" to "Sapporo",
            "福岡県" to "Fukuoka",
            "兵庫県" to "Kobe",
            "広島県" to "Hiroshima",
            "奈良県" to "Nara",
            "沖縄県" to "Okinawa",
            "서울특별시" to "Seoul",
            "부산광역시" to "Busan",
            "Seoul" to "Seoul",
            "Busan" to "Busan"
        )
        map.entries.firstOrNull { s.equals(it.key, ignoreCase = true) || s.contains(it.key) }?.let {
            return it.value
        }
        return cleanAdmin(s)
            ?.removeSuffix("都")?.removeSuffix("府")?.removeSuffix("県")
            ?.removeSuffix("시")?.removeSuffix("특별시")?.removeSuffix("광역시")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun stripCounty(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val cleaned = s
            .replace(Regex("""\s*County$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Parish$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Borough$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.takeIf { it.isNotBlank() && !it.equals("County", true) }
    }

    private fun cleanAdmin(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() && it.length <= 48 }
}

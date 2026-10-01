package com.lumen.launcher.travel.location

/**
 * Picks a place-section label from generic admin levels returned by reverse geocoding.
 * Levels (fine → coarse): locality → sub-admin (district/county) → admin (province/state) → country.
 *
 * Chooses the highest shared parent that still distinguishes the cluster.
 */
object AdminPlaceLabeler {

    data class AdminLevels(
        val locality: String? = null,
        val subAdminArea: String? = null,
        val adminArea: String? = null,
        val countryName: String? = null,
        val countryCode: String? = null
    )

    fun labelCluster(members: List<AdminLevels>): String? {
        if (members.isEmpty()) return null

        val localities = distinct(members.map { it.locality })
        if (localities.size == 1) return displayLocality(localities.single())

        val subAdmins = distinct(members.map { it.subAdminArea })
        if (subAdmins.size == 1) return areaLabel(subAdmins.single())

        val admins = distinct(members.map { it.adminArea })
        if (admins.size == 1) return areaLabel(admins.single())

        // Partial agreement: majority sub-admin / admin if ≥60% share it.
        majority(members.map { it.subAdminArea }, members.size)?.let { return areaLabel(it) }
        majority(members.map { it.adminArea }, members.size)?.let { return areaLabel(it) }

        // Fall back to most common locality, or country.
        majority(members.map { it.locality }, members.size)?.let { return displayLocality(it) }
        return members.mapNotNull { it.countryName?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /** Prefer a short display name for a single photo's "city" metadata. */
    fun displayForPhoto(levels: AdminLevels): String? =
        clean(levels.locality)
            ?: clean(levels.subAdminArea)?.let { areaLabel(it) }
            ?: clean(levels.adminArea)?.let { areaLabel(it) }

    fun areaLabel(raw: String): String {
        val base = cleanAdminName(raw) ?: return raw.trim()
        return if (base.endsWith(" area", ignoreCase = true)) base else "$base area"
    }

    fun displayLocality(raw: String): String = clean(raw) ?: raw.trim()

    /** "Colombo area" → "Colombo" for trip titles. */
    fun titlePlace(label: String): String =
        label.trim()
            .replace(Regex("""\s+area$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { label.trim() }

    private fun distinct(values: List<String?>): List<String> =
        values.mapNotNull { clean(it) }.distinctBy { it.lowercase() }

    private fun majority(values: List<String?>, total: Int): String? {
        if (total <= 0) return null
        val counts = values.mapNotNull { clean(it) }.groupingBy { it }.eachCount()
        val best = counts.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value * 5 >= total * 3 } // ≥60%
    }

    private fun clean(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() && it.length <= 64 }

    private fun cleanAdminName(raw: String): String? {
        var s = clean(raw) ?: return null
        s = s
            .replace(Regex("""\s+District$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Province$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+County$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Parish$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Prefecture$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Region$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+State$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Municipality$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return s.takeIf { it.isNotBlank() }
    }
}

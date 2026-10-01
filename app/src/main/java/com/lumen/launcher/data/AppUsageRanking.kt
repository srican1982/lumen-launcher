package com.lumen.launcher.data

/** Frequency within the saved launch window, with latest launch breaking ties. */
object AppUsageRanking {
    fun rank(visibleApps: List<AppInfo>, launches: List<SpaceSense.LaunchHour>): List<AppInfo> {
        val byKey = visibleApps.associateBy { it.key }
        val byPackage = visibleApps.groupBy { it.packageName }
        // Default dock shortcuts can use an alias activity instead of the launcher activity.
        val keys = launches.mapNotNull { launch ->
            byKey[launch.key]?.key
                ?: byPackage[launch.key.substringBefore('/') ]?.singleOrNull()?.key
        }
        val counts = keys.groupingBy { it }.eachCount()
        return visibleApps.filter { it.key in counts }.sortedWith(
            compareByDescending<AppInfo> { counts[it.key] ?: 0 }
                .thenBy { keys.indexOf(it.key) }
                .thenBy { it.label.lowercase() }
        )
    }
}

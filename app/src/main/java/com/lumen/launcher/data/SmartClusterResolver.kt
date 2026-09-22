package com.lumen.launcher.data

import com.lumen.launcher.search.Routine

object SmartClusterResolver {
    fun apps(
        space: SpaceKind,
        focusing: Boolean,
        focusApps: List<AppInfo>,
        visible: List<AppInfo>,
        recents: List<String>,
        needNow: List<AppInfo>,
        limit: Int = 8
    ): List<AppInfo> {
        if (space == SpaceKind.Private && !focusing) return emptyList()
        val recentApps = recents.mapNotNull { key -> visible.find { it.key == key } }
        val cap = if (focusing) 4 else limit.coerceIn(6, 8)
        val preferred = spaceApps(space, visible, recents)
        return (
            (if (focusing) focusApps else emptyList()) +
                needNow +
                recentApps.filter { app -> preferred.any { it.key == app.key } } +
                preferred +
                recentApps +
                visible
            )
            .distinctBy { it.key }
            .take(cap)
    }

    private fun spaceApps(space: SpaceKind, visible: List<AppInfo>, recents: List<String>): List<AppInfo> {
        val preferred = Routine.preferredCategories(space)
        return when (space) {
            SpaceKind.Travel -> visible.filter { travelish(it) || it.category in preferred }
            SpaceKind.Work -> visible.filter { it.category == AppCategory.Work }
            SpaceKind.Personal -> visible.filter { it.category in preferred }
            SpaceKind.Focus -> visible.filter { it.category in preferred }
            SpaceKind.Home -> Routine.likelyNext(visible, recents, space)
            SpaceKind.Private -> emptyList()
        }
    }

    private fun travelish(app: AppInfo): Boolean {
        val hay = "${app.label} ${app.packageName}".lowercase()
        return listOf(
            "maps", "waze", "uber", "lyft", "airline", "hotel", "booking",
            "kayak", "expedia", "wallet", "camera", "transit", "train"
        ).any { hay.contains(it) }
    }
}

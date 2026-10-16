package com.lumen.launcher.data

/** Explicit selections win; Focus never fills its allowlist with inferred apps. */
object ModeAppsResolver {
    const val MAX_APPS = 12

    fun resolve(space: SpaceKind, visible: List<AppInfo>, selected: List<String>?, favorites: List<AppInfo>, recents: List<AppInfo>, frequent: List<AppInfo> = emptyList(), dockPackages: Set<String> = emptySet(), homeCapacity: Int = MAX_APPS): List<AppInfo> {
        val eligible = if (space == SpaceKind.Home) visible.filterNot { it.packageName in dockPackages } else visible
        val byKey = eligible.associateBy { it.key }
        if (selected != null) {
            val picks = selected.distinct().mapNotNull(byKey::get)
            return if (space == SpaceKind.Home) picks else picks.take(homeCapacity)
        }
        if (space == SpaceKind.Focus) return emptyList()
        if (space == SpaceKind.Home) {
            // Cold-start suggestions alternate categories rather than filling with one alphabetic cluster.
            val groups = eligible.sortedBy { it.label.lowercase() }.groupBy { it.category }.values.toList()
            val balanced = (0 until (groups.maxOfOrNull { it.size } ?: 0)).flatMap { index -> groups.mapNotNull { it.getOrNull(index) } }
            return (favorites + frequent + recents + balanced).distinctBy { it.key }
                .filter { it.key in byKey }.take(homeCapacity)
        }
        if (space in setOf(SpaceKind.Work, SpaceKind.Personal, SpaceKind.Travel)) return SpaceStarterApps.picks(space, visible, dockPackages)
        val preferred = when (space) {
            SpaceKind.Work -> setOf(AppCategory.Work)
            SpaceKind.Personal -> setOf(AppCategory.Social)
            SpaceKind.Travel -> setOf(AppCategory.Travel)
            else -> emptySet()
        }
        val ranked = (favorites + recents + visible).distinctBy { it.key }.filter { it.key in byKey }
        return (if (preferred.isEmpty()) ranked else ranked.filter { it.category in preferred }).take(homeCapacity)
    }
}

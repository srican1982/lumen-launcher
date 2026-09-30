package com.lumen.launcher.data

/** Explicit selections win; Focus never fills its allowlist with inferred apps. */
object ModeAppsResolver {
    fun resolve(space: SpaceKind, visible: List<AppInfo>, selected: List<String>?, favorites: List<AppInfo>, recents: List<AppInfo>): List<AppInfo> {
        val byKey = visible.associateBy { it.key }
        if (selected != null) return selected.distinct().mapNotNull(byKey::get)
        if (space == SpaceKind.Focus) return emptyList()
        val preferred = when (space) {
            SpaceKind.Work -> setOf(AppCategory.Work)
            SpaceKind.Personal -> setOf(AppCategory.Social)
            SpaceKind.Travel -> setOf(AppCategory.Travel)
            else -> emptySet()
        }
        val ranked = (favorites + recents + visible).distinctBy { it.key }.filter { it.key in byKey }
        return (if (preferred.isEmpty()) ranked else ranked.filter { it.category in preferred }).take(24)
    }
}

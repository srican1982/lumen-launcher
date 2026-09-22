package com.lumen.launcher.social

import com.lumen.launcher.data.AppCategory
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.SpaceKind

object SocialShareTargets {
    fun prioritize(apps: List<AppInfo>, space: SpaceKind): List<String> {
        if (space != SpaceKind.Personal) return emptyList()
        val socialCategories = setOf(
            AppCategory.Social,
            AppCategory.Entertainment
        )
        return apps
            .filter { it.category in socialCategories }
            .sortedBy { it.label }
            .map { it.packageName }
            .distinct()
            .take(8)
    }
}

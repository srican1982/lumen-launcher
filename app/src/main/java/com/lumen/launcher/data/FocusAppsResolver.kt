package com.lumen.launcher.data

import com.lumen.launcher.search.Routine

enum class FocusWhy {
    Task, Pin, Space, Recent, HomePin, Other
}

data class FocusPick(
    val app: AppInfo,
    val why: FocusWhy
) {
    fun reason(space: SpaceKind): String? = when (why) {
        FocusWhy.Task -> "Needed for this task"
        FocusWhy.Pin -> "Always keep in ${space.title} Focus"
        FocusWhy.Space -> "Often used in ${space.title}"
        FocusWhy.Recent -> "Used recently"
        FocusWhy.HomePin -> "Pinned to Home"
        FocusWhy.Other -> null
    }
}

object FocusAppsResolver {
    fun apps(
        task: TodoItem?,
        apps: List<AppInfo>,
        recents: List<String>,
        space: SpaceKind,
        focusPins: List<String> = emptyList(),
        favorites: List<String> = emptyList(),
        limit: Int = 4
    ): List<AppInfo> = picks(task, apps, recents, space, focusPins, favorites, limit).map { it.app }

    fun picks(
        task: TodoItem?,
        apps: List<AppInfo>,
        recents: List<String>,
        space: SpaceKind,
        focusPins: List<String> = emptyList(),
        favorites: List<String> = emptyList(),
        limit: Int = 4
    ): List<FocusPick> {
        fun resolve(keys: List<String>) = keys.mapNotNull { key -> apps.find { it.key == key } }
        val taskApps = task?.let { NeedNowResolver.matchTaskApps(it, apps) }.orEmpty()
        val pins = resolve(focusPins)
        val pinnedHome = resolve(favorites)
        val preferred = Routine.preferredCategories(space)
        val spaceUsed = Routine.likelyNext(apps, recents, space)
            .filter { preferred.isEmpty() || it.category in preferred }
        val recentApps = resolve(recents)
        val ranked = buildList {
            taskApps.forEach { add(FocusPick(it, FocusWhy.Task)) }
            pins.forEach { add(FocusPick(it, FocusWhy.Pin)) }
            if (taskApps.isEmpty()) {
                pinnedHome.forEach { add(FocusPick(it, FocusWhy.HomePin)) }
            }
            spaceUsed.forEach { add(FocusPick(it, FocusWhy.Space)) }
            recentApps.forEach { add(FocusPick(it, FocusWhy.Recent)) }
            apps.forEach { add(FocusPick(it, FocusWhy.Other)) }
        }
        return ranked.distinctBy { it.app.key }.take(limit)
    }
}

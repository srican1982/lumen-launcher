package com.lumen.launcher.search

import com.lumen.launcher.data.*
import com.lumen.launcher.vm.LauncherUiState

object FocusContext {
    fun apps(s: LauncherUiState): List<AppInfo> {
        val task = s.focusTask
        val scope = task?.let { "FocusTask:${it.id}" } ?: SpaceKind.Focus.name
        val blocked = s.focusPins["Exclude:$scope"].orEmpty().toSet()
        val visible = s.visibleApps.filterNot { it.key in blocked }
        val ai = if (s.focusSuggestionTask == task?.text) s.focusSuggestedKeys else emptyList()
        val taskMatches = task?.let { NeedNowResolver.matchTaskApps(it, visible) }.orEmpty().map { it.key }
        val allowedFallback = visible.filter { it.key in taskMatches || it.key in s.focusPins[SpaceKind.Focus.name].orEmpty() || it.category in setOf(AppCategory.Work, AppCategory.Utilities) }
        val fallback = FocusAppsResolver.apps(task, allowedFallback, s.recents, SpaceKind.Focus,
            s.focusPins[SpaceKind.Focus.name].orEmpty(), s.favorites)
        return (s.focusPins[scope].orEmpty() + ai + fallback.map { it.key })
            .distinct().mapNotNull { key -> visible.find { it.key == key } }.take(4)
    }

    fun actions(s: LauncherUiState): List<SearchHit.Action> {
        val task = s.focusTask ?: return emptyList()
        fun relevant(text: String) = FocusLearning.related(task.text, text)
        val actions = mutableListOf<SearchHit.Action>()
        if (s.focusSuggestionTask == task.text) actions += s.focusPeopleActions
        s.notes.filter { it.space != SpaceKind.Private && relevant(it.text) }.take(2).forEach {
            actions += SearchHit.Action("note:${it.id}", "Review ${it.text.take(60)}", "Saved note", task.text)
        }
        s.upcomingEvents.filter { it.end > System.currentTimeMillis() && relevant(it.title) }.take(2).forEach {
            actions += SearchHit.Action("event:${it.id}", "Open ${it.title}", "Calendar · View meeting details", task.text)
        }
        s.travelAttachments.filter { relevant(it.title) }.take(2).forEach {
            actions += SearchHit.Action("document:${it.uri}", "Open ${it.title}", "Saved document", task.text)
        }
        return actions.distinctBy { it.id }.take(4)
    }
}

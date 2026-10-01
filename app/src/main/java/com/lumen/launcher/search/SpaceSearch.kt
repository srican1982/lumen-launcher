package com.lumen.launcher.search

import com.lumen.launcher.data.*
import com.lumen.launcher.vm.LauncherUiState

/** One result pipeline; Space changes relevance, never the searchable app inventory. */
object SpaceSearch {
    fun hint(space: SpaceKind) = when (space) {
        SpaceKind.Work -> "Search work…"
        SpaceKind.Personal -> "Search people, apps, create…"
        SpaceKind.Focus -> "Search what you need…"
        SpaceKind.Travel -> "Search this trip…"
        else -> "Search apps, people, anything…"
    }

    fun results(s: LauncherUiState, people: List<SearchHit.Action> = emptyList()): List<SearchHit> {
        val q = s.query.trim()
        val space = s.activeSpace
        val focusKeys = s.focusPins[SpaceKind.Focus.name].orEmpty() + s.modeApps[SpaceKind.Focus.name].orEmpty()
        val focusCandidates = s.visibleApps.filter { it.key in focusKeys || it.category in setOf(AppCategory.Work, AppCategory.Utilities) }
        val focusApps = FocusAppsResolver.apps(s.focusTask, focusCandidates, s.recents, SpaceKind.Focus, focusKeys, limit = 4)
        val preferredKeys = (if (space == SpaceKind.Focus) focusApps else s.homeApps).map { it.key }.toSet()
        fun boost(app: AppInfo): Int = when {
            space == SpaceKind.Home -> 0
            app.key in preferredKeys -> 100
            app.category in Routine.preferredCategories(space) -> 60
            else -> 0
        }
        val scored = mutableListOf<Pair<SearchHit, Int>>()
        if (q.isBlank()) {
            val suggestions = if (space == SpaceKind.Focus) focusApps else
                (s.homeApps + s.recentApps).distinctBy { it.key }.take(8)
            suggestions.forEach { scored += SearchHit.App(it, 0) to (400 + boost(it)) }
        } else {
            SearchInterpreter.interpret(q, s.visibleApps, s.recents, s.aliases, limit = s.visibleApps.size).forEach { hit ->
                val score = when (hit) {
                    is SearchHit.App -> hit.score + boost(hit.app)
                    is SearchHit.Math -> 1300
                    is SearchHit.Action -> 900
                    is SearchHit.IntentGroup, is SearchHit.Discovery -> 750
                    is SearchHit.Web -> -100
                }
                scored += hit to score
            }
        }
        fun add(id: String, title: String, detail: String, terms: String = title,
                owner: SpaceKind? = null, suggest: Boolean = false) {
            if (q.isBlank() && !suggest) return
            val match = if (q.isBlank()) 600 else listOf(title, terms).maxOf { text ->
                if (FuzzySearch.normalize(text).contains(FuzzySearch.normalize(q))) 930 else FuzzySearch.score(q, text)
            }
            if (q.isNotBlank() && match < 600) return
            val context = if (owner == space && space != SpaceKind.Home) 140 else 0
            scored += SearchHit.Action(id, title, detail, q) to (match + context)
        }
        val now = System.currentTimeMillis()
        s.todos.filter { !it.done && it.space != SpaceKind.Private }.forEach { task ->
            val current = space == SpaceKind.Focus && task.id == s.focusTask?.id
            add("task:${task.id}", task.text, "${task.space.title} · Task", owner = if(current) space else task.space, suggest = current)
        }
        s.notes.filter { it.space != SpaceKind.Private }.forEach { note ->
            add("note:${note.id}", note.text, "${note.space.title} · Note", owner = note.space)
        }
        s.upcomingEvents.filter { it.end > now }.forEach { event ->
            add("event:${event.id}", event.title, "Calendar · ${event.calendarName}", "${event.title} ${event.location} meeting calendar", SpaceKind.Work)
        }
        s.inbox.filter { !it.isDigest && s.visibleApps.any { app -> app.packageName == it.packageName } }.take(40).forEach { item ->
            val owner = if (item.packageName.contains("teams") || item.packageName.contains("slack") || item.packageName.contains("outlook")) SpaceKind.Work else SpaceKind.Personal
            add("inbox:${item.key}", item.title, "${item.source.title} · ${item.preview}", "${item.title} ${item.preview}", owner)
        }
        s.travelAttachments.forEach { doc ->
            add("document:${doc.uri}", doc.title, doc.travelCategory.title, "${doc.title} ${doc.travelCategory.title}", SpaceKind.Travel)
        }
        TravelCategory.entries.forEach { category ->
            add("travel:${category.name}", category.title, "Saved travel documents", "${category.title} ${category.description}", SpaceKind.Travel,
                suggest = space == SpaceKind.Travel && s.travelAttachments.any { it.travelCategory == category })
        }
        s.searchTrips.forEach { trip ->
            add("trip:${trip.id}", trip.displayTitle, "Trip album · ${trip.photoCount} photos",
                "${trip.displayTitle} ${trip.countryName.orEmpty()} ${trip.primaryCity.orEmpty()} ${trip.citiesLabel.orEmpty()} photos", SpaceKind.Travel)
        }
        add("trip_albums", "Trip Albums", "Trips and photos", "trip albums photos pictures gallery", SpaceKind.Travel, space == SpaceKind.Travel || space == SpaceKind.Personal)
        add("create:Quote", "Create Quote", "Social Create", "quote write words", SpaceKind.Personal, space == SpaceKind.Personal)
        add("create:Photo", "Photo Markup", "Draw, crop and blur a photo", "photo markup crop edit pictures", SpaceKind.Personal, space == SpaceKind.Personal)
        add("create:Scribble", "Scribble", "Sketch and create stickers", "scribble sketch draw sticker", SpaceKind.Personal)
        add("tasks", "Tasks", "Open your task list", "tasks today tasks todo to do", SpaceKind.Work, space == SpaceKind.Work)
        add("notes", "Notes", "Capture a note", "notes note", SpaceKind.Work)
        add("calendar", "Calendar", "Open calendar", "calendar meetings schedule", SpaceKind.Work, space == SpaceKind.Work)
        add("files", "Files", "Browse device files", "files documents downloads", SpaceKind.Work)
        add("weather", "Weather", "View the forecast", "weather forecast temperature", SpaceKind.Home)
        if (space == SpaceKind.Travel && q.isNotBlank()) {
            scored += SearchHit.Action("places", "Find $q in Maps", "Places and directions", q) to 100
        }
        people.forEach { scored += it to if (PeopleActions.parse(q) != null) 1200 else if (space == SpaceKind.Personal || space == SpaceKind.Work) 1070 else 930 }
        return scored.sortedByDescending { it.second }.map { it.first }.distinctBy {
            when(it) {
                is SearchHit.Action -> "${it.id}:${it.phone}"
                is SearchHit.App -> it.app.key
                else -> it.toString()
            }
        }.take(40)
    }
}

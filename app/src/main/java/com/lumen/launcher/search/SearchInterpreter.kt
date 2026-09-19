package com.lumen.launcher.search

import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.SpaceKind

sealed interface SearchHit {
    data class App(val app: AppInfo, val score: Int) : SearchHit
    data class Math(val calculation: Calculation) : SearchHit
    data class Action(
        val id: String,
        val title: String,
        val subtitle: String,
        val query: String,
        val phone: String = "",
        val message: String = ""
    ) : SearchHit
    data class IntentGroup(
        val title: String,
        val subtitle: String,
        val apps: List<AppInfo>
    ) : SearchHit
    data class Discovery(
        val title: String,
        val subtitle: String,
        val apps: List<AppInfo>
    ) : SearchHit
    data class Web(val query: String) : SearchHit
}

object SearchInterpreter {

    fun interpret(
        query: String,
        apps: List<AppInfo>,
        recents: List<String> = emptyList(),
        aliases: Map<String, String> = emptyMap(),
        limit: Int = 20
    ): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()

        Calculator.interpret(trimmed)?.let { hits += SearchHit.Math(it) }

        val ranked = rankedApps(appQuery(trimmed), apps, aliases, limit)
        val simple = ranked.filter { it.score >= 900 }
        hits += simple
        hits += discovery(trimmed, apps, recents)
        if (simple.isEmpty()) {
            IntentIndex.match(trimmed).forEach { intent ->
                val matched = IntentIndex.appsFor(intent, apps)
                if (matched.isNotEmpty()) {
                    hits += SearchHit.IntentGroup(intent.title, intent.subtitle, matched)
                }
            }
        }
        hits += actions(trimmed).filterNot { it.id == "open" && simple.isNotEmpty() }
        hits += ranked.filter { it.score < 900 }

        val hasStrongApp = hits.any { it is SearchHit.App && it.score >= 700 }
        if (!hasStrongApp || trimmed.length >= 3) {
            hits += SearchHit.Web(trimmed)
        }
        return hits
    }

    private fun appQuery(query: String): String {
        val lower = query.lowercase()
        for (prefix in listOf("open ", "launch ", "start ", "run ", "go to ")) {
            if (lower.startsWith(prefix)) return query.substring(prefix.length).trim()
        }
        return query
    }

    private fun rankedApps(
        query: String,
        apps: List<AppInfo>,
        aliases: Map<String, String>,
        limit: Int
    ): List<SearchHit.App> {
        val normalized = FuzzySearch.normalize(query)
        val aliasKey = aliases[normalized]
        return apps
            .map { app ->
                var score = FuzzySearch.score(query, app.label)
                if (aliasKey == app.key) score = maxOf(score, 990)
                if (score < 0 && aliasKey == app.key) score = 990
                app to score
            }
            .filter { it.second >= 0 }
            .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }.thenBy { it.first.label.lowercase() })
            .take(limit)
            .map { SearchHit.App(it.first, it.second) }
    }

    private fun discovery(query: String, apps: List<AppInfo>, recents: List<String>): List<SearchHit> {
        val q = FuzzySearch.normalize(query)
        val unused = listOf("rarely use", "rarely used", "havent opened", "haven't opened", "six months", "unused apps")
        if (unused.any { q.contains(it) || it.contains(q) && q.length >= 8 }) {
            val stale = apps.filter { app -> recents.none { it == app.key } }.take(12)
            if (stale.isNotEmpty()) {
                return listOf(SearchHit.Discovery("Rarely used", "Apps you haven't opened recently", stale))
            }
        }
        return emptyList()
    }

    private fun actions(query: String): List<SearchHit.Action> {
        val q = FuzzySearch.normalize(query)
        val results = mutableListOf<SearchHit.Action>()

        fun add(id: String, title: String, subtitle: String, aliases: List<String>) {
            if (aliases.any { FuzzySearch.score(q, it) >= 700 || q.contains(it) || it.startsWith(q) && q.length >= 2 }) {
                results += SearchHit.Action(id, title, subtitle, query)
            }
        }

        add("flashlight", "Flashlight", "Turn the torch on or off", listOf("flashlight", "torch", "flash light"))
        add("wifi", "Wi‑Fi", "Open Wi‑Fi settings", listOf("wifi", "wi fi", "wlan"))
        add("bluetooth", "Bluetooth", "Connect devices", listOf("bluetooth"))
        add("airplane", "Airplane mode", "Network settings", listOf("airplane", "flight mode"))
        add("camera", "Camera", "Take a photo", listOf("camera", "take a photo", "selfie"))
        add("photos", "Last photo", "Open photos", listOf("last photo", "open last photo", "photos", "gallery"))
        add("maps_home", "Take me home", "Start navigation", listOf("take me home", "navigate home", "go home"))
        add("settings", "Settings", "System settings", listOf("settings"))
        add("work_mode", "Work space", "Switch to Work", listOf("work mode", "work space"))

        Regex("""^(?:open)\s+(.+)$""", RegexOption.IGNORE_CASE).find(query)?.let { match ->
            results += SearchHit.Action("open", "Open ${match.groupValues[1]}", "Launch a matching app", query)
        }
        return results.distinctBy { it.id }
    }
}

object Routine {
    fun preferredCategories(space: SpaceKind): Set<com.lumen.launcher.data.AppCategory> = when (space) {
        SpaceKind.Work -> setOf(com.lumen.launcher.data.AppCategory.Work)
        SpaceKind.Personal -> setOf(
            com.lumen.launcher.data.AppCategory.Social,
            com.lumen.launcher.data.AppCategory.Entertainment,
            com.lumen.launcher.data.AppCategory.Food
        )
        SpaceKind.Focus -> setOf(
            com.lumen.launcher.data.AppCategory.Work,
            com.lumen.launcher.data.AppCategory.Utilities
        )
        SpaceKind.Travel -> setOf(
            com.lumen.launcher.data.AppCategory.Travel,
            com.lumen.launcher.data.AppCategory.Food
        )
        SpaceKind.Home -> setOf(
            com.lumen.launcher.data.AppCategory.Utilities,
            com.lumen.launcher.data.AppCategory.Travel,
            com.lumen.launcher.data.AppCategory.Work
        )
        SpaceKind.Private -> emptySet()
    }

    fun likelyNext(apps: List<AppInfo>, recents: List<String>, space: SpaceKind): List<AppInfo> {
        val preferred = preferredCategories(space)
        val recentApps = recents.mapNotNull { key -> apps.find { it.key == key } }
        val spaceApps = apps.filter { it.category in preferred }
        return (recentApps.filter { it.category in preferred } + recentApps + spaceApps)
            .distinctBy { it.key }
            .take(8)
    }

    fun actionCards(apps: List<AppInfo>, recents: List<String>, space: SpaceKind): List<com.lumen.launcher.data.ActionCard> {
        fun find(vararg needles: String): AppInfo? {
            return apps.firstOrNull { app ->
                val hay = "${app.label} ${app.packageName}".lowercase()
                needles.any { hay.contains(it) }
            }
        }
        val recentApps = recents.mapNotNull { key -> apps.find { it.key == key } }
        val cards = mutableListOf<com.lumen.launcher.data.ActionCard>()
        recentApps.firstOrNull()?.let { app ->
            cards += com.lumen.launcher.data.ActionCard(
                id = "continue",
                kicker = "CONTINUE",
                title = app.label,
                detail = "Pick up where you left off",
                app = app
            )
        }
        likelyNext(apps, recents, space).firstOrNull { it.key != recentApps.firstOrNull()?.key }?.let { app ->
            cards += com.lumen.launcher.data.ActionCard(
                id = "next",
                kicker = "NEXT",
                title = app.label,
                detail = space.kicker,
                app = app
            )
        }
        find("maps", "waze")?.let { app ->
            cards += com.lumen.launcher.data.ActionCard(
                id = "go",
                kicker = "GO",
                title = "Home",
                detail = app.label,
                app = app,
                actionId = "maps_home"
            )
        }
        (find("whatsapp") ?: find("message", "sms", "messenger"))?.let { app ->
            cards += com.lumen.launcher.data.ActionCard(
                id = "message",
                kicker = "MESSAGE",
                title = app.label,
                detail = "Send a message",
                app = app
            )
        }
        return cards.distinctBy { it.id }.take(4)
    }
}

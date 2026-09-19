package com.lumen.launcher.data

object SpaceSense {

    data class LaunchHour(val key: String, val hour: Int)

    fun infer(
        hour: Int,
        weekday: Int,
        recents: List<String>,
        hourHits: List<LaunchHour>,
        apps: List<AppInfo>,
        nextEvent: CalendarEvent?,
        nowMs: Long = System.currentTimeMillis()
    ): SpaceKind {
        val scores = mutableMapOf(
            SpaceKind.Home to 0,
            SpaceKind.Work to 0,
            SpaceKind.Personal to 0,
            SpaceKind.Focus to 0,
            SpaceKind.Travel to 0
        )
        scores[SpaceKind.infer(hour)] = 3
        val weekend = weekday >= 6
        if (weekend) {
            bump(scores, SpaceKind.Work, -2)
            bump(scores, SpaceKind.Personal, 2)
            bump(scores, SpaceKind.Home, 1)
        }
        nextEvent?.let { event ->
            val soon = event.begin in (nowMs - 15 * 60_000L)..(nowMs + 3 * 60 * 60_000L) ||
                (event.begin <= nowMs && event.end > nowMs)
            if (soon) {
                when {
                    travelEvent(event) -> bump(scores, SpaceKind.Travel, 5)
                    workEvent(event) -> bump(scores, SpaceKind.Work, 4)
                    personalEvent(event) -> bump(scores, SpaceKind.Personal, 3)
                }
            }
        }
        val nearbyHours = setOf(hour, (hour + 23) % 24, (hour + 1) % 24)
        val hits = hourHits.filter { it.hour in nearbyHours }.mapNotNull { hit ->
            apps.find { it.key == hit.key }
        }
        hits.forEach { app -> scoreApp(scores, app, hour) }
        recents.take(6).mapNotNull { key -> apps.find { it.key == key } }
            .forEach { app -> scoreApp(scores, app, hour, weight = 1) }

        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.first()
        if (best.key == SpaceKind.Travel && best.value < 3) {
            return ranked.first { it.key != SpaceKind.Travel }.key
        }
        return best.key
    }

    fun dockKeys(
        space: SpaceKind,
        spaceDocks: Map<String, List<String>>,
        fallback: List<String>?
    ): List<String>? = spaceDocks[space.name]?.takeIf { it.isNotEmpty() } ?: fallback

    fun parseLaunchHour(raw: String): LaunchHour? {
        val at = raw.lastIndexOf('@')
        if (at <= 0) return null
        val hour = raw.substring(at + 1).toIntOrNull() ?: return null
        if (hour !in 0..23) return null
        val key = raw.substring(0, at).trim()
        if (key.isBlank()) return null
        return LaunchHour(key, hour)
    }

    private fun scoreApp(
        scores: MutableMap<SpaceKind, Int>,
        app: AppInfo,
        hour: Int,
        weight: Int = 2
    ) {
        when (app.category) {
            AppCategory.Work -> bump(scores, SpaceKind.Work, weight)
            AppCategory.Travel -> bump(scores, SpaceKind.Travel, weight)
            AppCategory.Social, AppCategory.Entertainment, AppCategory.Food -> {
                if (hour in 17..23 || hour in 0..4) bump(scores, SpaceKind.Personal, weight)
                else bump(scores, SpaceKind.Home, 1)
            }
            AppCategory.Finance -> {
                if (hour in 9..16) bump(scores, SpaceKind.Work, 1) else bump(scores, SpaceKind.Personal, 1)
            }
            AppCategory.Utilities -> if (hour in 21..23 || hour in 0..5) bump(scores, SpaceKind.Focus, 1)
            else -> Unit
        }
    }

    private fun travelEvent(event: CalendarEvent): Boolean {
        val hay = "${event.title} ${event.location}".lowercase()
        return listOf(
            "flight", "gate", "boarding", "airport", "terminal", "hotel",
            "station", "train", "departure", "uber", "lyft"
        ).any { hay.contains(it) }
    }

    private fun workEvent(event: CalendarEvent): Boolean {
        val hay = "${event.title} ${event.calendarName}".lowercase()
        if (
            listOf("standup", "stand-up", "sync", "interview", "1:1", "1-1", "all hands", "sprint", "retro")
                .any { hay.contains(it) }
        ) return true
        return CalendarRole.guess("", "", event.calendarName) == CalendarRole.Work
    }

    private fun personalEvent(event: CalendarEvent): Boolean {
        val hay = "${event.title} ${event.calendarName}".lowercase()
        return listOf("dinner", "birthday", "gym", "doctor", "dentist", "family").any { hay.contains(it) }
    }

    private fun bump(scores: MutableMap<SpaceKind, Int>, space: SpaceKind, by: Int) {
        scores[space] = (scores[space] ?: 0) + by
    }
}

package com.lumen.launcher.data

/** Intersects foreground sessions with exact local-day windows instead of daily buckets. */
object UsageTimeline {
    data class Event(val at: Long, val packageName: String?, val resumed: Boolean = true)

    fun totals(events: List<Event>, start: Long, end: Long, packages: Set<String>): Map<String, Long> {
        if (end <= start) return emptyMap()
        val totals = mutableMapOf<String, Long>()
        var active: String? = null
        var since = start
        fun finish(at: Long) {
            val key = active ?: return
            val duration = (minOf(at, end) - maxOf(since, start)).coerceAtLeast(0)
            if (key in packages && duration > 0) totals[key] = (totals[key] ?: 0) + duration
        }
        for (event in events.sortedBy { it.at }) {
            if (event.at > end) break
            if (event.resumed) {
                finish(event.at)
                active = event.packageName
                since = event.at
            } else if (event.packageName == null || event.packageName == active) {
                finish(event.at)
                active = null
            }
        }
        finish(end)
        return totals
    }
}

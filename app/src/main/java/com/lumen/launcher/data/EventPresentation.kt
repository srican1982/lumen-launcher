package com.lumen.launcher.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared selection and visible date text for every calendar surface. */
object EventPresentation {
    fun next(events: List<CalendarEvent>, now: Long, space: SpaceKind = SpaceKind.Home,
             work: Set<String> = emptySet(), personal: Set<String> = emptySet()): CalendarEvent? =
        events.asSequence().filter { it.end > now }
            .filter { space != SpaceKind.Work || WorkCardEvent.eligible(it, work, personal) }
            .minByOrNull { it.begin }

    fun nextWorkEvent(events: List<CalendarEvent>, now: Long, work: Set<String>, personal: Set<String>): CalendarEvent? =
        next(events, now, SpaceKind.Work, work, personal)

    fun isToday(event: CalendarEvent, now: Long): Boolean {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    }

    fun evening(now: Long): Boolean = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour >= 17

    fun whenText(event: CalendarEvent, now: Long, includeEnd: Boolean = false): String {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(event.begin).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = when (start.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> start.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
        val time = DateTimeFormatter.ofPattern("h:mm a")
        val end = if (includeEnd) " \u2013 ${Instant.ofEpochMilli(event.end).atZone(zone).format(time)}" else ""
        return "$day \u00B7 ${start.format(time)}$end"
    }
}

package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpaceSenseTest {

    private val outlook = app("Outlook", "com.microsoft.office.outlook", AppCategory.Work)
    private val maps = app("Maps", "com.google.android.apps.maps", AppCategory.Travel)
    private val whatsapp = app("WhatsApp", "com.whatsapp", AppCategory.Social)
    private val apps = listOf(outlook, maps, whatsapp)

    @Test
    fun hourAloneStillPicksWork() {
        val space = SpaceSense.infer(
            hour = 11,
            weekday = 3,
            recents = emptyList(),
            hourHits = emptyList(),
            apps = apps,
            nextEvent = null
        )
        assertThat(space).isEqualTo(SpaceKind.Work)
    }

    @Test
    fun standupThisHourPrefersWorkOnWeekend() {
        val event = CalendarEvent(
            id = 1,
            title = "Standup",
            begin = 1_000L,
            end = 3_600_000L,
            location = "",
            calendarName = "Outlook"
        )
        val space = SpaceSense.infer(
            hour = 10,
            weekday = 6,
            recents = listOf(outlook.key),
            hourHits = listOf(SpaceSense.LaunchHour(outlook.key, 10)),
            apps = apps,
            nextEvent = event,
            nowMs = 1_000L
        )
        assertThat(space).isEqualTo(SpaceKind.Work)
    }

    @Test
    fun airportEventSwitchesToTravel() {
        val event = CalendarEvent(
            id = 2,
            title = "Flight to SEA",
            begin = 30 * 60_000L,
            end = 3 * 60 * 60_000L,
            location = "SeaTac Airport"
        )
        val space = SpaceSense.infer(
            hour = 11,
            weekday = 2,
            recents = listOf(maps.key),
            hourHits = listOf(SpaceSense.LaunchHour(maps.key, 11)),
            apps = apps,
            nextEvent = event,
            nowMs = 1_000L
        )
        assertThat(space).isEqualTo(SpaceKind.Travel)
    }

    @Test
    fun spaceDockWinsOverGlobal() {
        val keys = SpaceSense.dockKeys(
            SpaceKind.Work,
            mapOf("Work" to listOf(outlook.key)),
            listOf(whatsapp.key)
        )
        assertThat(keys).containsExactly(outlook.key)
    }

    @Test
    fun parseLaunchHour() {
        val hit = SpaceSense.parseLaunchHour("${outlook.key}@9")
        assertThat(hit?.key).isEqualTo(outlook.key)
        assertThat(hit?.hour).isEqualTo(9)
        assertThat(SpaceSense.parseLaunchHour("bad")).isNull()
    }

    private fun app(label: String, pkg: String, category: AppCategory) =
        AppInfo(label, pkg, "$pkg.Main", 0L, category)
}

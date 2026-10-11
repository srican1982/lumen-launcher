package com.lumen.launcher.data
import com.google.common.truth.Truth.assertThat
import org.junit.Test
class WorkCardEventTest {
    private val concert = CalendarEvent(1, "Concert", 0, 1, "Hollywood Palladium", calendarId = 4)
    @Test fun excludesPersonalConcert() {
        assertThat(WorkCardEvent.eligible(concert, emptySet(), emptySet())).isFalse()
    }
    @Test fun usesExplicitCalendarRolesAndMeetingLinks() {
        assertThat(WorkCardEvent.eligible(concert, setOf("4"), emptySet())).isTrue()
        assertThat(WorkCardEvent.eligible(concert.copy(location = "https://meet.google.com/abc"), emptySet(), emptySet())).isTrue()
        assertThat(WorkCardEvent.eligible(concert.copy(teams = true), emptySet(), setOf("4"))).isFalse()
    }
    @Test fun futureEventIncludesDateAndTime() {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDateTime.of(2026, 10, 8, 16, 0).atZone(zone).toInstant().toEpochMilli()
        val start = java.time.LocalDateTime.of(2026, 10, 21, 19, 0).atZone(zone).toInstant().toEpochMilli()
        assertThat(WorkCardEvent.whenText(concert.copy(begin = start), now)).isEqualTo("Wed, Oct 21 \u00B7 7:00 PM")
    }
}

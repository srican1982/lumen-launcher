package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EventPresentationTest {
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val now = at(8, 17)
    private val concert = CalendarEvent(1, "Concert", at(21, 19), at(21, 22), "Hollywood Palladium", calendarId = 1)
    private val meeting = CalendarEvent(2, "Standup", at(9, 9), at(9, 10), "Zoom", calendarId = 2)

    @Test fun allWorkSurfacesSelectSameNextEligibleEvent() {
        val ended = meeting.copy(id = 3, begin = at(8, 15), end = at(8, 16))
        val events = listOf(concert, ended, meeting)
        assertThat(EventPresentation.nextWorkEvent(events, now, setOf("2"), setOf("1"))).isEqualTo(meeting)
        assertThat(EventPresentation.next(events, now, SpaceKind.Work, setOf("2"), setOf("1"))).isEqualTo(meeting)
        assertThat(EventPresentation.nextWorkEvent(listOf(concert, ended), now, setOf("2"), setOf("1"))).isNull()
    }
    @Test fun datesAndSeparatorsAreConsistentAcrossSurfaces() {
        assertThat(EventPresentation.whenText(meeting, now)).isEqualTo("Tomorrow \u00B7 9:00 AM")
        assertThat(EventPresentation.whenText(concert, now)).isEqualTo("Wed, Oct 21 \u00B7 7:00 PM")
        assertThat(EventPresentation.whenText(meeting, now, includeEnd = true)).isEqualTo("Tomorrow \u00B7 9:00 AM \u2013 10:00 AM")
        assertThat(WorkCardEvent.whenText(meeting, now)).isEqualTo(EventPresentation.whenText(meeting, now))
    }
    @Test fun homeStillIncludesPersonalEventsAndSortsUnorderedInput() {
        assertThat(EventPresentation.next(listOf(concert, meeting), now)).isEqualTo(meeting)
        assertThat(EventPresentation.next(listOf(concert), now)).isEqualTo(concert)
    }
}

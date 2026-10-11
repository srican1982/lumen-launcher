package com.lumen.launcher.alarm

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LumenAlarmTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private fun ms(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()
    @Test fun usesProvidedDateInsteadOfWallClock() {
        val now = ms("2026-10-09T10:00:00-07:00[America/Los_Angeles]")
        assertEquals(ms("2026-10-09T18:00:00-07:00[America/Los_Angeles]"), LumenAlarm(hour=18,minute=0).nextTriggerMs(now,zone))
    }
    @Test fun tomorrowKeepsLocalTimeAcrossSpringForward() {
        val now = ms("2026-03-07T09:00:00-08:00[America/Los_Angeles]")
        assertEquals(ms("2026-03-08T08:00:00-07:00[America/Los_Angeles]"), LumenAlarm(hour=8,minute=0).nextTriggerMs(now,zone))
    }
    @Test fun tomorrowKeepsLocalTimeAcrossFallBack() {
        val now = ms("2026-10-31T09:00:00-07:00[America/Los_Angeles]")
        assertEquals(ms("2026-11-01T08:00:00-08:00[America/Los_Angeles]"), LumenAlarm(hour=8,minute=0).nextTriggerMs(now,zone))
    }
    @Test fun nonexistentLocalTimeResolvesForward() {
        val now = ms("2026-03-08T00:00:00-08:00[America/Los_Angeles]")
        assertEquals(ms("2026-03-08T03:30:00-07:00[America/Los_Angeles]"), LumenAlarm(hour=2,minute=30).nextTriggerMs(now,zone))
    }
    @Test fun countdownKeepsSecondsAndDeadlineAcrossMidnightAndTimeZones() {
        val now = ms("2026-10-09T23:59:48-07:00[America/Los_Angeles]")
        val deadline = now + 10 * 60_000L
        val timer = LumenAlarm(hour = 0, minute = 9, label = "Timer", triggerAtMs = deadline)
        assertEquals(deadline, timer.nextTriggerMs(now, zone))
        assertEquals(deadline, timer.nextTriggerMs(now + 120_000L, ZoneId.of("Asia/Tokyo")))
    }
    @Test fun explicitTomorrowDoesNotBecomeLaterToday() {
        val now = ms("2026-10-09T05:00:00-07:00[America/Los_Angeles]")
        assertEquals(ms("2026-10-10T06:30:00-07:00[America/Los_Angeles]"), AlarmDeadline.forRequest(6, 30, "set an alarm for 6:30 tomorrow morning", now, zone))
        assertNull(AlarmDeadline.forRequest(6, 30, "set alarm for 6:30 am", now, zone))
    }
    @Test fun noImmediatePastTrigger() {
        val now = ms("2026-10-09T08:00:00-07:00[America/Los_Angeles]")
        assertTrue(LumenAlarm(hour=8,minute=0).nextTriggerMs(now,zone) > now)
    }
}

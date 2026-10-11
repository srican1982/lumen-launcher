package com.lumen.launcher.alarm

import org.junit.Assert.*
import org.junit.Test

class TimerDeadlineTest {
    @Test fun secondTimersKeepTheExactDeadlineAcrossMidnightAndDst() {
        val now = java.time.Instant.parse("2026-11-01T08:59:30.321Z").toEpochMilli()
        val deadline = AlarmDeadline.timer(90, now)
        assertEquals(now + 90_000, deadline)
        val alarm = LumenAlarm(hour = 1, minute = 1, triggerAtMs = deadline)
        assertEquals(deadline, alarm.nextTriggerMs(now))
        assertEquals(now + 86_400_000, AlarmDeadline.timer(86400, now))
    }
    @Test fun invalidDurationsCannotSchedule() {
        listOf(-1, 0, 86401, Int.MAX_VALUE).forEach {
            assertThrows(IllegalArgumentException::class.java) { AlarmDeadline.timer(it, 0) }
        }
    }
}

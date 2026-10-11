package com.lumen.launcher.focus
import org.junit.Assert.assertEquals
import org.junit.Test
class FocusCompletionTest {
    @Test fun earlyEndCountsOnlyElapsedTime() { assertEquals(120000L, FocusCompletion.elapsed(600000, 1480000, 0, 1000000)) }
    @Test fun pausedTimeDoesNotAccumulate() { assertEquals(120000L, FocusCompletion.elapsed(600000, 0, 480000, 9000000)) }
    @Test fun extendedTimeIsNotCountedAsFocused() { assertEquals(120000L, FocusCompletion.elapsed(900000, 1780000, 0, 1000000)) }
    @Test fun lateAlarmCapsTimeAtSessionLength() { assertEquals(600000L, FocusCompletion.elapsed(600000, 100, 0, 1000000)) }
}

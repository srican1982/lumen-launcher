package com.lumen.launcher.focus

import org.junit.Assert.*
import org.junit.Test

class FocusSessionRulesTest {
    @Test fun pausedSnapshotPreservesTimeAcrossScreens() {
        val session = FocusSessionSnapshot(pausedRemainingMs = 123000, totalMs = 900000)
        assertTrue(session.focusing)
        assertEquals(123000L, session.remainingMs())
        assertEquals(123000L, session.remainingMs(Long.MAX_VALUE))
    }
    @Test fun savedPeopleReachChoicesRemainDistinct() {
        assertTrue(FocusReach.CallsOnly.allowsCalls)
        assertFalse(FocusReach.CallsOnly.allowsMessages)
        assertFalse(FocusReach.MessagesOnly.allowsCalls)
        assertTrue(FocusReach.MessagesOnly.allowsMessages)
        assertTrue(FocusReach.CallsAndMessages.allowsCalls && FocusReach.CallsAndMessages.allowsMessages)
    }
    @Test fun expiredSnapshotDoesNotClaimActiveQuietMode() {
        assertFalse(FocusSessionSnapshot(until = 1).focusing)
        assertEquals(0L, FocusSessionSnapshot(until = 1).remainingMs())
    }
    @Test fun dueAlarmFinishes() { assertTrue(FocusSessionRules.acceptFinish(100, 100, 100)) }
    @Test fun delayedAlarmFinishes() { assertTrue(FocusSessionRules.acceptFinish(100, 100, 150)) }
    @Test fun oldAlarmCannotStopNewSession() { assertFalse(FocusSessionRules.acceptFinish(200, 100, 250)) }
    @Test fun earlyAlarmCannotStopSession() { assertFalse(FocusSessionRules.acceptFinish(100, 100, 99)) }
    @Test fun duplicateAlarmAfterCleanupIsIgnored() { assertFalse(FocusSessionRules.acceptFinish(0, 100, 200)) }
    @Test fun manualEndCanFinishEarly() { assertTrue(FocusSessionRules.acceptFinish(100, null, 50)) }
    @Test fun manualDndChangeIsPreserved() { assertFalse(FocusSessionRules.restoreLegacy(2, 3)) }
    @Test fun unchangedDndIsRestored() { assertTrue(FocusSessionRules.restoreLegacy(3, 3)) }
}


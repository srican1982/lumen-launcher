package com.lumen.launcher.focus

import org.junit.Assert.*
import org.junit.Test

class FocusSessionRulesTest {
    @Test fun dueAlarmFinishes() { assertTrue(FocusSessionRules.acceptFinish(100, 100, 100)) }
    @Test fun delayedAlarmFinishes() { assertTrue(FocusSessionRules.acceptFinish(100, 100, 150)) }
    @Test fun oldAlarmCannotStopNewSession() { assertFalse(FocusSessionRules.acceptFinish(200, 100, 250)) }
    @Test fun earlyAlarmCannotStopSession() { assertFalse(FocusSessionRules.acceptFinish(100, 100, 99)) }
    @Test fun duplicateAlarmAfterCleanupIsIgnored() { assertFalse(FocusSessionRules.acceptFinish(0, 100, 200)) }
    @Test fun manualEndCanFinishEarly() { assertTrue(FocusSessionRules.acceptFinish(100, null, 50)) }
    @Test fun manualDndChangeIsPreserved() { assertFalse(FocusSessionRules.restoreLegacy(2, 3)) }
    @Test fun unchangedDndIsRestored() { assertTrue(FocusSessionRules.restoreLegacy(3, 3)) }
}


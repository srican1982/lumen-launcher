package com.lumen.launcher.focus

import org.junit.Assert.*
import org.junit.Test

class FocusCallRulesTest {
    @Test fun selectedPersonIsAllowedWithoutRepeatException() {
        assertTrue(FocusCallRules.allow(true, false, false))
    }
    @Test fun unselectedCallerIsSilenced() {
        assertFalse(FocusCallRules.allow(false, false, false))
        assertFalse(FocusCallRules.allow(false, true, false))
    }
    @Test fun repeatedCallerNeedsExplicitException() {
        assertFalse(FocusCallRules.allow(false, false, true))
        assertTrue(FocusCallRules.allow(false, true, true))
    }
}

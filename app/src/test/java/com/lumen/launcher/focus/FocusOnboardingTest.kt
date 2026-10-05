package com.lumen.launcher.focus

import org.junit.Assert.*
import org.junit.Test

class FocusOnboardingTest {
    @Test fun freshInstallShowsGuide() {
        assertTrue(needsFocusGuide(false, false, false))
    }
    @Test fun restoredOrLegacySettingsSkipIntroduction() {
        assertFalse(needsFocusGuide(false, false, true))
        assertFalse(needsFocusGuide(true, false, true))
        assertFalse(needsFocusGuide(true, false, false))
    }
    @Test fun unfinishedGuideResumesEvenAfterDefaultsWereSaved() {
        assertTrue(needsFocusGuide(false, true, true))
    }
    @Test fun completionTakesPriorityOverStaleProgress() {
        assertFalse(needsFocusGuide(true, true, true))
    }
}

package com.lumen.launcher.voice.intent

import org.junit.Assert.*
import org.junit.Test

class DeterministicIntentParserTest {
    @Test fun blankInputHasNoAction() { assertTrue(DeterministicIntentParser.parse("  ").isEmpty()) }
    @Test fun appNameIsParsedLocally() {
        val intent = DeterministicIntentParser.parse("open WhatsApp").first { it.action == VoiceAction.OPEN_APP }
        assertEquals("whatsapp", intent.appName?.lowercase())
    }
    @Test fun reservedPagesDoNotBecomeAppLaunches() {
        val hits = DeterministicIntentParser.parse("open settings")
        assertTrue(hits.any { it.action == VoiceAction.OPEN_SETTINGS })
        assertFalse(hits.any { it.action == VoiceAction.OPEN_APP })
    }
}

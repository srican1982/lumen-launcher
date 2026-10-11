package com.lumen.launcher.voice.intent

import org.junit.Assert.*
import org.junit.Test

class VoiceGapsTest {
    private val apps = listOf("WhatsApp", "Telegram", "Viber", "Messages", "Chrome", "ChatGPT", "Gemini")
    private fun route(text: String) = VoiceQueryRouter.route(listOf(text), apps)

    @Test fun channelIsSeparateAndMessageContentsAreUntouched() {
        val result = route("message Amma via WhatsApp saying Call John on Telegram, please!")
        assertEquals("Amma", result.recipientName)
        assertEquals("WhatsApp", result.messagingApp)
        assertNull(result.appName)
        assertEquals("Call John on Telegram, please!", result.textValue)
        assertEquals("John on Main Street", route("call John on Main Street").recipientName)
        assertEquals("Amma", route("CALL TO Amma USING Whats App PLEASE").recipientName)
        assertEquals("WhatsApp", route("CALL TO Amma USING Whats App PLEASE").messagingApp)
    }

    @Test fun slotsAreNotConfirmations() {
        val call = route("call")
        assertEquals(VoiceSlot.CONTACT, call.missingSlot)
        assertTrue(ClarificationResolver.resolve(listOf("yes"), call, apps) is ClarificationResolution.Ask)
        val filled = ClarificationResolver.resolve(listOf("Amma on WhatsApp"), call, apps) as ClarificationResolution.Execute
        assertEquals("Amma", filled.intent.recipientName)
        assertEquals("WhatsApp", filled.intent.messagingApp)
        assertFalse(filled.intent.confirmed)
        assertTrue(VoiceRisk.requiresConfirmation(filled.intent))
        assertEquals(ClarificationResolution.Cancel, ClarificationResolver.resolve(listOf("cancel"), call, apps))
        listOf("open Chrome", "what time is it", "take me to the airport", "could you show my tasks", "play music").forEach {
            assertEquals(it, ClarificationResolution.NoMatch, ClarificationResolver.resolve(listOf(it), call, apps))
        }
    }

    @Test fun otherMissingSlotsFillLocally() {
        val timer = ClarificationResolver.resolve(listOf("90 seconds"), route("set a timer"), apps) as ClarificationResolution.Execute
        assertEquals(90, timer.intent.durationSeconds)
        val alarm = ClarificationResolver.resolve(listOf("6 AM"), route("set an alarm"), apps) as ClarificationResolution.Execute
        assertEquals(6, alarm.intent.hour)
        assertFalse(alarm.intent.confirmed)
        val app = ClarificationResolver.resolve(listOf("Chrome"), route("open"), apps) as ClarificationResolution.Execute
        assertEquals("chrome", app.intent.appName)
        assertTrue(ClarificationResolver.resolve(listOf("a banana"), route("timer"), apps) is ClarificationResolution.Ask)
    }

    @Test fun durationBoundsAndWholeMinuteCompatibility() {
        assertEquals(90, VoiceDuration.seconds("one minute thirty seconds"))
        assertEquals(9000, VoiceDuration.seconds("two and a half hours"))
        assertEquals(86400, VoiceDuration.seconds("24 hours"))
        listOf("0 seconds", "25 hours", "-5 minutes", "five minutes tomorrow", "five elephants", "999999999999 seconds").forEach { assertNull(it, VoiceDuration.seconds(it)) }
        assertEquals(5, route("timer for five minutes").intValue)
        assertEquals(300, route("timer for five minutes").durationSeconds)
    }

    @Test fun confirmationIncludesRecipientChannelAndDuration() {
        val request = route("message Amma on WhatsApp saying Hi")
        val pending = VoiceIntent(VoiceAction.CLARIFY, 1f, request.originalText, alternatives = listOf(request))
        assertEquals(ClarificationResolution.NoMatch, ClarificationResolver.resolve(listOf("message Amma on Telegram saying Hi"), pending, apps))
        assertEquals(ClarificationResolution.NoMatch, ClarificationResolver.resolve(listOf("message John on WhatsApp saying Hi"), pending, apps))
        assertTrue((ClarificationResolver.resolve(listOf("yes"), pending, apps) as ClarificationResolution.Execute).intent.confirmed)
    }

    @Test fun aiFieldsCannotRefineAContactToAnAppOrConfirmIt() {
        val result = AiIntentFallback.parse("""{"action":"CALL","recipientName":"Amma","messagingApp":"WhatsApp","confirmed":true}""", "call Amma", apps)!!
        assertEquals("Amma", result.recipientName)
        assertEquals("WhatsApp", result.messagingApp)
        assertFalse(result.confirmed)
        val legacy = AiIntentFallback.parse("""{"action":"CALL","appName":"Amma"}""", "call Amma", apps)!!
        assertEquals("Amma", legacy.recipientName)
    }

    @Test fun aiQuestionsAreOnlyAttachedToAnInstalledAiApp() {
        assertEquals("what is gravity", route("ask Chat GPT what is gravity").textValue)
        assertEquals(VoiceAction.CLARIFY, VoiceQueryRouter.route(listOf("ask ChatGPT what is gravity"), listOf("Chrome")).action)
    }
}

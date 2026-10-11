package com.lumen.launcher.voice.intent

import com.lumen.launcher.voice.SpeechContext
import org.junit.Assert.*
import org.junit.Test

class VoiceSafetyTest {
    @Test fun sensitiveActionsNeedExplicitConfirmationEvenAtFullConfidence() {
        listOf(VoiceAction.CALL, VoiceAction.SEND_MESSAGE, VoiceAction.SET_ALARM,
            VoiceAction.DELETE_TASK, VoiceAction.CANCEL_ALARM, VoiceAction.HIDE_APP,
            VoiceAction.MOVE_TO_PRIVATE, VoiceAction.REMOVE_FROM_PRIVATE).forEach {
            val intent = VoiceIntent(it, 1f, "request")
            assertTrue(VoiceRisk.requiresConfirmation(intent))
            assertFalse(intent.confirmed)
            assertTrue(ClarificationResolver.confirmed(intent).confirmed)
        }
        assertFalse(VoiceRisk.requiresConfirmation(VoiceIntent(VoiceAction.OPEN_APP, 1f, "open Docs", appName = "Docs")))
    }
    @Test fun firstConfirmationHypothesisWins() {
        val pending = VoiceIntent(VoiceAction.CLARIFY, .9f, "call Mom", alternatives = listOf(VoiceIntent(VoiceAction.CALL, .99f, "call Mom", appName = "Mom")))
        val yes = ClarificationResolver.resolve(listOf("yes", "no"), pending)
        assertTrue(yes is ClarificationResolution.Execute)
        assertEquals(ClarificationResolution.Cancel, ClarificationResolver.resolve(listOf("no", "yes"), pending))
    }
    @Test fun differentMessageCannotConfirmOldPayload() {
        val choice = VoiceIntent(VoiceAction.SEND_MESSAGE, .99f, "text Mom saying I'm late", appName = "Mom", textValue = "I'm late")
        val pending = VoiceIntent(VoiceAction.CLARIFY, .9f, choice.originalText, alternatives = listOf(choice))
        assertEquals(ClarificationResolution.NoMatch, ClarificationResolver.resolve(listOf("text Mom saying I'm early"), pending))
    }
    @Test fun appLabelsKeepWordsThatLookLikeCommandsOrFillers() {
        val apps = listOf("My App", "The Weather", "Call of Duty", "App", "Lumen Calendar")
        apps.forEach { label ->
            val bare = VoiceQueryRouter.route(listOf(label), apps)
            val open = VoiceQueryRouter.route(listOf("please open $label"), apps)
            assertEquals(label.lowercase(), bare.appName)
            assertEquals(label.lowercase(), open.appName)
            assertEquals(VoiceAction.OPEN_APP, bare.action)
        }
    }
    @Test fun originalMessageAndRecipientArePreserved() {
        val raw = "Could you text Jane Doe saying I'm late; please wait for me."
        val result = VoiceQueryRouter.route(listOf(raw), listOf("Messages", "Jane"))
        assertEquals(VoiceAction.SEND_MESSAGE, result.action)
        assertEquals("Jane Doe", result.recipientName)
        assertEquals("I'm late; please wait for me.", result.textValue)
        assertEquals(raw, result.originalText)
    }
    @Test fun realTopAppWinsOverLowerHypothesesAndRepairs() {
        val result = VoiceQueryRouter.route(listOf("open Docs", "open dock", "open docks"), listOf("Docs", "Dock", "Docks"))
        assertEquals("docs", result.appName)
        assertEquals("open Docs", result.originalText)
    }
    @Test fun unresolvedAppOffersThreeChoicesButQuestionsKeepAiFallback() {
        val apps = listOf("Chrome", "Calendar", "Camera", "ChatGPT")
        val choice = VoiceQueryRouter.route(listOf("open xylophone"), apps)
        assertEquals(VoiceAction.CLARIFY, choice.action)
        assertEquals(3, choice.alternatives.size)
        assertEquals(VoiceAction.UNKNOWN, VoiceQueryRouter.route(listOf("Why is the sky blue?"), apps).action)
    }
    @Test fun recognizerHintsHaveBoundedDistinctContext() {
        assertEquals(listOf("Docs", "Work"), SpeechContext.hints(listOf(" Docs ", "docs", "", "Work")))
        assertEquals(100, SpeechContext.hints((1..250).map { "App $it" }).size)
    }
    @Test fun aiCannotProvideItsOwnConfirmation() {
        val parsed = AiIntentFallback.parse("""{"action":"SEND_MESSAGE","confidence":1,"appName":"Mom","textValue":"Hi","confirmed":true}""", "text Mom saying Hi")!!
        assertFalse(parsed.confirmed)
        assertTrue(VoiceRisk.requiresConfirmation(parsed))
    }
    @Test fun aiOnlyActionsStillDecode() {
        listOf(VoiceAction.ANSWER, VoiceAction.ASK_USER, VoiceAction.NAVIGATE).forEach { action ->
            val intent = AiIntentFallback.parse("""{"action":"${action.name}","confidence":0.95,"textValue":"Home"}""", "a request")!!
            assertEquals(action, intent.action)
        }
    }
    @Test fun explicitTorchStateAndTodayCalendarSurviveRouting() {
        assertEquals(false, VoiceQueryRouter.route(listOf("turn off flashlight")).enabled)
        assertEquals(true, VoiceQueryRouter.route(listOf("turn on flashlight")).enabled)
        assertEquals("today", VoiceQueryRouter.route(listOf("show my calendar today")).textValue)
    }
}

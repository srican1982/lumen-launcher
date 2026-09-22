package com.lumen.launcher.voice.intent

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.FlowModule
import org.junit.Test

class AiIntentFallbackTest {

    @Test
    fun unknownLocalNeedsFallback() {
        val intent = VoiceIntent(VoiceAction.UNKNOWN, 0.25f, "put whatsapp with my main apps")
        assertThat(AiIntentFallback.needsFallback(intent)).isTrue()
    }

    @Test
    fun confidentLocalDoesNotNeedFallback() {
        val intent = VoiceIntent(VoiceAction.OPEN_APP, 0.97f, "open WhatsApp", appName = "whatsapp")
        assertThat(AiIntentFallback.needsFallback(intent)).isFalse()
        assertThat(VoiceConfidence.shouldExecute(intent)).isTrue()
        assertThat(VoiceConfidence.shouldTrustLocal(intent)).isTrue()
    }

    @Test
    fun fuzzyAppMatchAsksGemini() {
        val intent = VoiceIntent(VoiceAction.OPEN_APP, 0.70f, "open sitting", appName = "settings")
        assertThat(VoiceConfidence.shouldTrustLocal(intent)).isFalse()
        assertThat(AiIntentFallback.needsFallback(intent)).isTrue()
    }

    @Test
    fun parseAnswerSpeaksFreeText() {
        val intent = AiIntentFallback.parse(
            """{"action":"ANSWER","confidence":0.9,"textValue":"Paris is the capital of France."}""",
            "what's the capital of france"
        )
        assertThat(intent!!.action).isEqualTo(VoiceAction.ANSWER)
        assertThat(intent.textValue).isEqualTo("Paris is the capital of France.")
        assertThat(VoiceConfidence.shouldExecute(intent)).isTrue()
    }

    @Test
    fun unknownWithTextBecomesAnAnswer() {
        val intent = AiIntentFallback.parse(
            """{"action":"UNKNOWN","textValue":"I can set an alarm if you want."}""",
            "can you help me"
        )
        assertThat(intent!!.action).isEqualTo(VoiceAction.ANSWER)
        assertThat(intent.textValue).contains("alarm")
    }

    @Test
    fun namesKeyAndOfflineErrors() {
        assertThat(AiIntentFallback.spokenError(AiIntentFallback.Error.NO_KEY)).contains("OpenRouter")
        assertThat(AiIntentFallback.spokenError(AiIntentFallback.Error.OFFLINE)).contains("network")
        assertThat(AiIntentFallback.spokenError(AiIntentFallback.Error.UNAUTHORIZED)).contains("key")
    }

    @Test
    fun parseMapsClosedActionsAndInstalledApps() {
        val intent = AiIntentFallback.parse(
            """
            {
              "action": "ADD_TO_DOCK",
              "confidence": 0.94,
              "appName": "WhatsApp",
              "destructive": false
            }
            """.trimIndent(),
            "put WhatsApp down with my other main apps",
            appLabels = listOf("WhatsApp", "Word")
        )
        assertThat(intent).isNotNull()
        assertThat(intent!!.action).isEqualTo(VoiceAction.ADD_TO_DOCK)
        assertThat(intent.appName).isEqualTo("whatsapp")
        assertThat(VoiceConfidence.shouldExecute(intent)).isTrue()
    }

    @Test
    fun parseRejectsInventedActions() {
        val intent = AiIntentFallback.parse(
            """{"action":"LAUNCH_SHELL","confidence":0.99}""",
            "do something dangerous"
        )
        assertThat(intent!!.action).isEqualTo(VoiceAction.UNKNOWN)
    }

    @Test
    fun hideIsAlwaysDestructive() {
        val intent = AiIntentFallback.parse(
            """{"action":"HIDE_APP","confidence":0.99,"appName":"WhatsApp","destructive":false}""",
            "get rid of WhatsApp",
            appLabels = listOf("WhatsApp")
        )
        assertThat(intent!!.action).isEqualTo(VoiceAction.HIDE_APP)
        assertThat(intent.destructive).isTrue()
        assertThat(VoiceConfidence.shouldExecute(intent)).isFalse()
    }

    @Test
    fun parseClarifiesCrowdedHome() {
        val intent = AiIntentFallback.parse(
            """
            {
              "action": "CLARIFY",
              "confidence": 0.8,
              "clarify": ["reduce icon size", "use 5 columns"],
              "options": [
                {"action":"SET_ICON_SIZE","confidence":0.8,"floatValue":-8},
                {"action":"SET_GRID","confidence":0.8,"intValue":5}
              ]
            }
            """.trimIndent(),
            "make home less busy"
        )
        assertThat(intent!!.action).isEqualTo(VoiceAction.CLARIFY)
        assertThat(intent.alternatives).hasSize(2)
        assertThat(intent.alternatives[1].action).isEqualTo(VoiceAction.SET_GRID)
    }

    @Test
    fun parseSpaceAndFlow() {
        val space = AiIntentFallback.parse(
            """{"action":"SET_SPACE","confidence":0.9,"space":"Work"}""",
            "put me in office mode"
        )
        assertThat(space!!.space).isEqualTo(SpaceKind.Work)

        val flow = AiIntentFallback.parse(
            """{"action":"SET_FLOW_MODULE","confidence":0.9,"module":"news","enabled":false}""",
            "I don't want headlines on flow"
        )
        assertThat(flow!!.module).isEqualTo(FlowModule.News)
        assertThat(flow.enabled).isFalse()
    }

    @Test
    fun requestMentionsClosedActionsAndInstalledApps() {
        val body = AiIntentFallback.requestBody("open ChatGPT", listOf("ChatGPT", "WhatsApp"))
        assertThat(body).contains("OPEN_APP")
        assertThat(body).contains("ANSWER")
        assertThat(body).contains("ChatGPT")
        assertThat(AiIntentFallback.MODEL).isEqualTo("gemini-3-flash-preview")
        assertThat(body).doesNotContain("launch this intent")
        assertThat(body).contains("abbreviated")
    }

    @Test
    fun extractTextSkipsThoughtParts() {
        val raw = """
            {
              "candidates": [{
                "content": {
                  "parts": [
                    {"thought": true, "text": "planning"},
                    {"text": "{\"action\":\"ANSWER\",\"textValue\":\"Hello.\"}"}
                  ]
                }
              }]
            }
        """.trimIndent()
        val text = AiIntentFallback.extractText(raw)
        assertThat(text).contains("ANSWER")
        assertThat(text).doesNotContain("planning")
    }

    @Test
    fun parseMapsSpokenBankNameOntoBoaLabel() {
        val intent = AiIntentFallback.parse(
            """{"action":"OPEN_APP","confidence":0.9,"appName":"Bank of America"}""",
            "open bank of america",
            appLabels = listOf("BOA", "WhatsApp")
        )
        assertThat(intent!!.action).isEqualTo(VoiceAction.OPEN_APP)
        assertThat(intent.appName).isEqualTo("boa")
        assertThat(VoiceConfidence.shouldExecute(intent)).isTrue()
    }
}

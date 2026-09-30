package com.lumen.launcher.voice.intent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NaturalVoicePhrasesTest {
    @Test fun conversationalAppRequestsResolveLocally() {
        listOf("Could you bring up WhatsApp please", "I want to use WhatsApp",
            "take me to WhatsApp", "pull up WhatsApp for me").forEach {
            val intent = VoiceQueryRouter.route(listOf(it), listOf("WhatsApp", "Chrome"))
            assertThat(intent.action).isEqualTo(VoiceAction.OPEN_APP)
            assertThat(intent.appName).isEqualTo("whatsapp")
            assertThat(VoiceConfidence.shouldTrustLocal(intent)).isTrue()
        }
    }

    @Test fun naturalFocusRequestsKeepDuration() {
        mapOf("give me twenty minutes to concentrate" to 20,
            "help me focus for half an hour" to 30,
            "concentrate for 45 minutes" to 45).forEach { (text, minutes) ->
            val intent = VoiceQueryRouter.route(listOf(text))
            assertThat(intent.action).isEqualTo(VoiceAction.START_FOCUS)
            assertThat(intent.intValue).isEqualTo(minutes)
        }
    }

    @Test fun everydayTaskAndNoteRequests() {
        assertThat(VoiceQueryRouter.route(listOf("what do I have to do")).action)
            .isEqualTo(VoiceAction.SHOW_TASKS)
        val note = VoiceQueryRouter.route(listOf("remember this buy milk"))
        assertThat(note.action).isEqualTo(VoiceAction.SAVE_NOTE)
        assertThat(note.textValue).isEqualTo("buy milk")
    }

    @Test fun normalizationDoesNotRewriteNegationsOrEmbeddedInstructions() {
        listOf("dont bring up whatsapp", "note bring up whatsapp",
            "why should i use whatsapp", "take me home").forEach {
            assertThat(NaturalVoicePhrases.normalize(it)).isEqualTo(it)
        }
        assertThat(VoiceQueryRouter.route(listOf("dont bring up whatsapp"), listOf("WhatsApp")).action)
            .isNotEqualTo(VoiceAction.OPEN_APP)
    }
}

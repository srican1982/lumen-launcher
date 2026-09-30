package com.lumen.launcher.voice.intent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceConversationTest {
    private val local = VoiceIntent(VoiceAction.OPEN_APP, .99f, "work", appName = "work")
    @Test fun shortAnswerToQuestionUsesContextDespiteLocalMatch() {
        val conversation = VoiceConversation()
        conversation.record("help me plan", "Is this for work or home?", true)
        assertThat(conversation.needsContext("work", local)).isTrue()
        assertThat(conversation.needsContext("open camera", local)).isFalse()
    }
    @Test fun correctionsAndPronounsKeepContext() {
        val conversation = VoiceConversation()
        conversation.record("alarm at seven", "Alarm for seven.", false)
        assertThat(conversation.needsContext("actually make it eight", local)).isTrue()
        assertThat(conversation.needsContext("tell me more", local)).isTrue()
        assertThat(conversation.needsContext("thanks", local.copy(action = VoiceAction.END_TALK))).isFalse()
    }
    @Test fun memoryIsBoundedAndClearedForNewSession() {
        val conversation = VoiceConversation()
        repeat(20) { conversation.record("request $it", "reply $it", false) }
        assertThat(conversation.history()).hasSize(16)
        assertThat(conversation.history().last()).isEqualTo("Lumen: reply 19")
        conversation.clear()
        assertThat(conversation.history()).isEmpty()
        assertThat(conversation.needsContext("make it eight", local)).isFalse()
    }
}

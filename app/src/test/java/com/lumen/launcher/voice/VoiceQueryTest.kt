package com.lumen.launcher.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceQueryTest {

    @Test
    fun stripsPleaseAndOpenStays() {
        assertThat(VoiceQuery.clean("Hey Lumen open Outlook")).isEqualTo("open outlook")
        assertThat(VoiceQuery.clean("Open Microsoft Outlook.")).isEqualTo("open microsoft outlook")
    }

    @Test
    fun stripsPunctuation() {
        assertThat(VoiceQuery.clean("Open Outlook.")).isEqualTo("open outlook")
    }

    @Test
    fun ignoresOwnApology() {
        assertThat(VoiceQuery.isOwnSpeech("I didn't catch that. Say an app name, or say thanks to stop.")).isTrue()
        assertThat(VoiceQuery.isOwnSpeech("open Outlook")).isFalse()
    }
}

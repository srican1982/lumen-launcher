package com.lumen.launcher.voice

import android.speech.SpeechRecognizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceErrorsTest {

    @Test
    fun ignoresClientAndStartingErrors() {
        assertThat(VoiceErrors.ignore(SpeechRecognizer.ERROR_CLIENT, starting = false)).isTrue()
        assertThat(VoiceErrors.ignore(SpeechRecognizer.ERROR_NO_MATCH, starting = true)).isTrue()
        assertThat(VoiceErrors.ignore(SpeechRecognizer.ERROR_NO_MATCH, starting = false)).isFalse()
    }

    @Test
    fun treatsBusySeparatelyFromAMiss() {
        assertThat(VoiceErrors.isBusy(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)).isTrue()
        assertThat(VoiceErrors.isSoftMiss(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)).isFalse()
    }

    @Test
    fun treatsNoMatchAndTimeoutAsSoftMisses() {
        assertThat(VoiceErrors.isSoftMiss(SpeechRecognizer.ERROR_NO_MATCH)).isTrue()
        assertThat(VoiceErrors.isSoftMiss(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)).isTrue()
        assertThat(VoiceErrors.isSoftMiss(SpeechRecognizer.ERROR_NETWORK)).isFalse()
    }

    @Test
    fun namesHardErrorsForTheUser() {
        assertThat(VoiceErrors.message(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
            .isEqualTo("I need the microphone.")
        assertThat(VoiceErrors.message(SpeechRecognizer.ERROR_NETWORK))
            .isEqualTo("I need a network to hear you.")
        assertThat(VoiceErrors.message(SpeechRecognizer.ERROR_AUDIO))
            .isEqualTo("I couldn't use the mic. Tap Lumen to try again.")
        assertThat(VoiceErrors.message(SpeechRecognizer.ERROR_NO_MATCH)).isNull()
        assertThat(VoiceErrors.message(SpeechRecognizer.ERROR_CLIENT)).isNull()
    }
}

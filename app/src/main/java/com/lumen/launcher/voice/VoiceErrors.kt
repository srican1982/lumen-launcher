package com.lumen.launcher.voice

import android.speech.SpeechRecognizer

object VoiceErrors {

    fun ignore(error: Int, starting: Boolean): Boolean {
        return starting || error == SpeechRecognizer.ERROR_CLIENT
    }

    fun isBusy(error: Int): Boolean = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

    fun isSoftMiss(error: Int): Boolean {
        return error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    }

    fun message(error: Int): String? {
        return when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "I need the microphone."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "I need a network to hear you."
            SpeechRecognizer.ERROR_SERVER ->
                "Speech had a problem. Tap Lumen to try again."
            SpeechRecognizer.ERROR_AUDIO ->
                "I couldn't use the mic. Tap Lumen to try again."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "This language isn't available for speech."
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
            else -> "I couldn't hear you. Tap Lumen to try again."
        }
    }
}

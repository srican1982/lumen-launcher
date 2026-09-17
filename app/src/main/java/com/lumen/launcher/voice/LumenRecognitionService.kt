package com.lumen.launcher.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Required by VoiceInteractionService. Lumen is not a speech engine; this
 * forwards to an installed recognizer so the Digital Assistant role still works.
 */
class LumenRecognitionService : RecognitionService() {
    private var recognizer: SpeechRecognizer? = null
    private var activeCallback: Callback? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        teardown(clearCallback = true)
        activeCallback = listener
        val targets = SpeechRecognizers.commandTargets(this)
        if (targets.isEmpty()) {
            activeCallback = null
            listener.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        runCatching {
            val speech = SpeechRecognizers.create(this, targets.first())
                ?: error("No speech recognizer")
            recognizer = speech.also {
                speech.setRecognitionListener(forwardingListener())
                speech.startListening(recognizerIntent)
            }
        }.onFailure {
            activeCallback = null
            listener.error(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun onStopListening(listener: Callback) {
        runCatching { recognizer?.stopListening() }
    }

    override fun onCancel(listener: Callback) {
        teardown(clearCallback = true)
    }

    override fun onDestroy() {
        teardown(clearCallback = true)
        super.onDestroy()
    }

    private fun teardown(clearCallback: Boolean) {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        if (clearCallback) activeCallback = null
    }

    private fun forwardingListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            activeCallback?.readyForSpeech(params ?: Bundle.EMPTY)
        }
        override fun onBeginningOfSpeech() {
            activeCallback?.beginningOfSpeech()
        }
        override fun onRmsChanged(rmsdB: Float) {
            activeCallback?.rmsChanged(rmsdB)
        }
        override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null) activeCallback?.bufferReceived(buffer)
        }
        override fun onEndOfSpeech() {
            activeCallback?.endOfSpeech()
        }
        override fun onError(error: Int) {
            activeCallback?.error(error)
            teardown(clearCallback = true)
        }
        override fun onResults(results: Bundle?) {
            activeCallback?.results(results ?: Bundle.EMPTY)
            teardown(clearCallback = true)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            activeCallback?.partialResults(partialResults ?: Bundle.EMPTY)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

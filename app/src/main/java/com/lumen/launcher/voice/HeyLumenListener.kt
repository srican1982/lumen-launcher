package com.lumen.launcher.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class HeyLumenListener(
    private val context: Context,
    private val onWake: (remainder: String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var listening = false
    private var fired = false
    private var busyBackoffMs = 120L
    private val restart = Runnable { if (wanted) start() }

    fun setEnabled(enabled: Boolean) {
        if (wanted == enabled) return
        wanted = enabled
        if (enabled) {
            fired = false
            busyBackoffMs = 120L
            start()
        } else {
            stop()
        }
    }

    fun release() {
        wanted = false
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun start() {
        if (!wanted || listening || fired) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        val rec = recognizer ?: createRecognizer().also { recognizer = it }
        listening = true
        runCatching { rec.startListening(listenIntent()) }.onFailure {
            listening = false
            retry(400L)
        }
    }

    private fun stop() {
        handler.removeCallbacks(restart)
        listening = false
        runCatching { recognizer?.cancel() }
    }

    private fun retry(delayMs: Long) {
        if (!wanted || fired) return
        listening = false
        handler.removeCallbacks(restart)
        handler.postDelayed(restart, delayMs)
    }

    private fun hear(text: String) {
        if (!wanted || fired) return
        val hit = WakePhrase.detect(text) ?: return
        fired = true
        wanted = false
        listening = false
        handler.removeCallbacks(restart)
        runCatching { recognizer?.cancel() }
        onWake(hit.remainder)
    }

    private fun listenIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
        }
    }

    private fun createRecognizer(): SpeechRecognizer {
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                busyBackoffMs = 120L
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                if (!wanted || fired) {
                    listening = false
                    return
                }
                val delay = when (error) {
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_NO_MATCH -> 50L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> {
                        busyBackoffMs = (busyBackoffMs * 2).coerceAtMost(800L)
                        busyBackoffMs
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> 600L
                    else -> 160L
                }
                retry(delay)
            }
            override fun onResults(results: Bundle?) {
                listening = false
                if (!wanted || fired) return
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                texts.forEach { hear(it) }
                if (!fired) retry(50L)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                texts.forEach { hear(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        return rec
    }
}

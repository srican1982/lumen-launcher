package com.lumen.launcher.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * One SpeechRecognizer and one TTS. Wake and commands take turns.
 */
class VoiceEngine(
    context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onStarting()
        fun onListening()
        fun onPartial(text: String)
        fun onResults(texts: List<String>)
        fun onWake(remainder: String)
        fun onLevel(level: Float)
        fun onHardError(message: String)
        fun onSoftMiss()
        fun onSpeakFinished(listenAfter: Boolean)
        fun onNeedPrompt()
    }

    private enum class Mode { Idle, Wake, Command }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mode = Mode.Idle
    private var wakeWanted = false
    private var starting = false
    private var lastPartial = ""
    private var listenAfterSpeak = false
    private var generation = 0
    private var focusRequest: AudioFocusRequest? = null

    private val commandWatchdog = Runnable {
        if (mode != Mode.Command) return@Runnable
        val leftover = lastPartial.trim()
        lastPartial = ""
        if (leftover.isNotBlank()) callbacks.onResults(listOf(leftover))
        else callbacks.onSoftMiss()
    }

    private val wakeRestart = Runnable {
        if (wakeWanted && mode == Mode.Idle) begin(Mode.Wake)
    }

    init {
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            val lang = engine.setLanguage(Locale.getDefault())
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    main.post {
                        if (utteranceId == "lumen-speak") finishSpeak()
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { finishSpeak() }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { finishSpeak() }
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (interrupted) main.post { finishSpeak() }
                }
            })
        }
    }

    fun setWakeWanted(wanted: Boolean) {
        wakeWanted = wanted
        if (wanted) {
            if (mode == Mode.Idle) scheduleWake(280L)
        } else if (mode == Mode.Wake) {
            goIdle(destroy = false)
        }
    }

    fun listenCommand() {
        generation += 1
        lastPartial = ""
        main.removeCallbacks(commandWatchdog)
        main.removeCallbacks(wakeRestart)
        begin(Mode.Command)
    }

    fun stopCommand() {
        generation += 1
        listenAfterSpeak = false
        lastPartial = ""
        main.removeCallbacks(commandWatchdog)
        runCatching { tts?.stop() }
        dropFocus()
        goIdle(destroy = false)
        if (wakeWanted) scheduleWake(360L)
    }

    fun speak(text: String, listenAfter: Boolean) {
        listenAfterSpeak = listenAfter
        val engine = tts
        if (!ttsReady || engine == null) {
            callbacks.onSpeakFinished(listenAfter)
            return
        }
        requestFocus()
        val spoken = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lumen-speak")
        if (spoken != TextToSpeech.SUCCESS) {
            dropFocus()
            callbacks.onSpeakFinished(listenAfter)
        }
    }

    fun release() {
        wakeWanted = false
        generation += 1
        main.removeCallbacks(commandWatchdog)
        main.removeCallbacks(wakeRestart)
        goIdle(destroy = true)
        dropFocus()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    fun hasRecognizer(): Boolean = SpeechRecognizer.isRecognitionAvailable(app)

    private fun begin(next: Mode) {
        val gen = generation
        if (next == Mode.Command) callbacks.onStarting()
        starting = true
        runCatching { recognizer?.cancel() }
        main.postDelayed({
            if (gen != generation) return@postDelayed
            startNow(next, gen)
        }, 140L)
    }

    private fun startNow(next: Mode, gen: Int) {
        if (gen != generation) return
        val rec = ensureRecognizer()
        if (rec == null) {
            starting = false
            if (next == Mode.Command) callbacks.onNeedPrompt()
            else scheduleWake(600L)
            return
        }
        mode = next
        lastPartial = ""
        requestFocus()
        val intent = if (next == Mode.Wake) {
            SpeechRecognizers.wakeIntent(app)
        } else {
            SpeechRecognizers.commandIntent(app)
        }
        val started = runCatching { rec.startListening(intent) }.isSuccess
        starting = false
        if (!started) {
            recreateRecognizer()
            if (next == Mode.Command) {
                main.postDelayed({
                    if (gen == generation) startNow(Mode.Command, gen)
                }, 280L)
            } else {
                goIdle(destroy = true)
                scheduleWake(500L)
            }
            return
        }
        if (next == Mode.Command) {
            main.removeCallbacks(commandWatchdog)
            main.postDelayed(commandWatchdog, 12_000L)
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val rec = SpeechRecognizers.createForCommands(app) ?: return null
        rec.setRecognitionListener(listener)
        recognizer = rec
        return rec
    }

    private fun recreateRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun goIdle(destroy: Boolean) {
        mode = Mode.Idle
        starting = false
        main.removeCallbacks(commandWatchdog)
        if (destroy) recreateRecognizer()
        else runCatching { recognizer?.cancel() }
        dropFocus()
    }

    private fun scheduleWake(delayMs: Long) {
        main.removeCallbacks(wakeRestart)
        if (wakeWanted) main.postDelayed(wakeRestart, delayMs)
    }

    private fun finishSpeak() {
        val again = listenAfterSpeak
        listenAfterSpeak = false
        dropFocus()
        callbacks.onSpeakFinished(again)
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
        }
    }

    private fun dropFocus() {
        val request = focusRequest ?: return
        audio.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (mode == Mode.Command) {
                callbacks.onListening()
                main.removeCallbacks(commandWatchdog)
                main.postDelayed(commandWatchdog, 12_000L)
            }
        }
        override fun onBeginningOfSpeech() {
            if (mode == Mode.Command) {
                main.removeCallbacks(commandWatchdog)
                main.postDelayed(commandWatchdog, 12_000L)
            }
        }
        override fun onRmsChanged(rmsdB: Float) {
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            callbacks.onLevel(level)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) {
            if (VoiceErrors.ignore(error, starting)) return
            val current = mode
            main.removeCallbacks(commandWatchdog)
            if (current == Mode.Wake) {
                goIdle(destroy = VoiceErrors.isBusy(error))
                scheduleWake(if (VoiceErrors.isBusy(error)) 480L else 320L)
                return
            }
            if (current != Mode.Command) return
            goIdle(destroy = VoiceErrors.isBusy(error))
            when {
                VoiceErrors.isBusy(error) -> {
                    main.postDelayed({ listenCommand() }, 280L)
                }
                VoiceErrors.isSoftMiss(error) -> {
                    val leftover = lastPartial.trim()
                    lastPartial = ""
                    if (leftover.isNotBlank()) callbacks.onResults(listOf(leftover))
                    else callbacks.onSoftMiss()
                }
                else -> {
                    val message = VoiceErrors.message(error)
                        ?: "I couldn't hear you. Tap Lumen to try again."
                    callbacks.onHardError(message)
                }
            }
        }
        override fun onResults(results: Bundle?) {
            val current = mode
            main.removeCallbacks(commandWatchdog)
            val texts = SpeechRecognizers.rankedTexts(results)
            val leftover = lastPartial
            lastPartial = ""
            if (current == Mode.Wake) {
                goIdle(destroy = false)
                val spoken = texts.ifEmpty { listOf(leftover) }
                val hit = spoken.firstNotNullOfOrNull { WakePhrase.detect(it) }
                if (hit != null) {
                    wakeWanted = false
                    callbacks.onWake(hit.remainder)
                } else {
                    scheduleWake(240L)
                }
                return
            }
            if (current != Mode.Command) return
            goIdle(destroy = false)
            val spoken = if (texts.any { it.isNotBlank() }) texts else listOf(leftover)
            if (spoken.any { it.isNotBlank() }) callbacks.onResults(spoken)
            else callbacks.onSoftMiss()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val texts = SpeechRecognizers.rankedTexts(partialResults)
            val text = texts.firstOrNull().orEmpty()
            if (text.isBlank()) return
            lastPartial = text
            if (mode == Mode.Command) {
                main.removeCallbacks(commandWatchdog)
                main.postDelayed(commandWatchdog, 8_000L)
                callbacks.onPartial(text)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

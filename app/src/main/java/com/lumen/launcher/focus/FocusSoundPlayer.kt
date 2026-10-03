package com.lumen.launcher.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soft ambient loops for Focus. Uses in-app gain (separate from the system media slider)
 * and a partial wake lock so playback can continue with the screen off.
 */
class FocusSoundPlayer private constructor(context: Context) {
    private val app = context.applicationContext
    private val playing = AtomicBoolean(false)
    private val sessionMode = AtomicBoolean(false)
    private val soundRef = AtomicReference(FocusSound.Off)
    @Volatile private var gain = 0.35f
    @Volatile private var track: AudioTrack? = null
    @Volatile private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun currentSound(): FocusSound = soundRef.get()
    fun isPlaying(): Boolean = playing.get()
    fun volume(): Float = gain

    fun setVolume(volume: Float) {
        gain = volume.coerceIn(0.05f, 1f)
        FocusSoundPrefs.setVolume(app, gain)
    }

    fun select(sound: FocusSound, preview: Boolean = false) {
        FocusSoundPrefs.setSound(app, sound)
        soundRef.set(sound)
        if (sound == FocusSound.Off) {
            stopInternal(keepSession = sessionMode.get())
            return
        }
        if (preview || sessionMode.get()) startInternal(session = sessionMode.get() || !preview)
    }

    fun startSession() {
        sessionMode.set(true)
        val sound = FocusSoundPrefs.sound(app)
        soundRef.set(sound)
        gain = FocusSoundPrefs.volume(app)
        if (sound == FocusSound.Off) {
            stopInternal(keepSession = true)
            return
        }
        startInternal(session = true)
    }

    fun pauseSession() {
        if (!sessionMode.get()) return
        stopInternal(keepSession = true)
    }

    fun resumeSession() {
        if (!sessionMode.get()) return
        val sound = FocusSoundPrefs.sound(app).also { soundRef.set(it) }
        if (sound != FocusSound.Off) startInternal(session = true)
    }

    fun stopSession() {
        sessionMode.set(false)
        stopInternal(keepSession = false)
    }

    fun stopPreview() {
        if (!sessionMode.get()) stopInternal(keepSession = false)
    }

    private fun startInternal(session: Boolean) {
        stopInternal(keepSession = sessionMode.get())
        val sound = soundRef.get()
        if (sound == FocusSound.Off) return
        val sampleRate = 22_050
        val loop = FocusSoundSynth.loop(sound, sampleRate, seconds = 4)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes((minBuf * 2).coerceAtLeast(loop.size * 2))
            .build()
        track = audio
        playing.set(true)
        if (session) acquireWakeLock()
        audio.play()
        worker = Thread({
            val scratch = ShortArray(loop.size)
            try {
                while (playing.get() && !Thread.currentThread().isInterrupted) {
                    val g = gain
                    for (i in loop.indices) {
                        scratch[i] = (loop[i] * g).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    var offset = 0
                    while (offset < scratch.size && playing.get()) {
                        val written = audio.write(scratch, offset, scratch.size - offset)
                        if (written <= 0) break
                        offset += written
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { audio.stop() }
                runCatching { audio.release() }
            }
        }, "focus-sound").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopInternal(keepSession: Boolean) {
        playing.set(false)
        worker?.interrupt()
        worker = null
        runCatching { track?.pause() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        if (!keepSession) {
            sessionMode.set(false)
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lumen:focus_sound").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        @Volatile private var instance: FocusSoundPlayer? = null
        fun get(context: Context): FocusSoundPlayer =
            instance ?: synchronized(this) {
                instance ?: FocusSoundPlayer(context).also { instance = it }
            }
    }
}

internal object FocusSoundSynth {
    fun loop(sound: FocusSound, sampleRate: Int, seconds: Int): ShortArray {
        val n = sampleRate * seconds
        val out = ShortArray(n)
        val rnd = Random(sound.id.hashCode())
        var brown = 0.0
        var phase = 0.0
        var lfophase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val sample = when (sound) {
                FocusSound.Off -> 0.0
                FocusSound.Rain -> {
                    val white = rnd.nextDouble() * 2 - 1
                    // Light high-shelf rain hiss.
                    white * 0.22 + (rnd.nextDouble() * 2 - 1) * 0.08 * sin(t * 18)
                }
                FocusSound.Forest -> {
                    val breeze = (rnd.nextDouble() * 2 - 1) * 0.12
                    val bird = if (i % (sampleRate * 2) in 0..(sampleRate / 10)) {
                        sin(2 * PI * (1800 + 200 * sin(t * 3)) * t) * 0.04
                    } else 0.0
                    breeze + bird
                }
                FocusSound.Ocean -> {
                    lfophase += 2 * PI * 0.08 / sampleRate
                    val swell = (sin(lfophase) * 0.5 + 0.5)
                    (rnd.nextDouble() * 2 - 1) * 0.18 * swell
                }
                FocusSound.Fireplace -> {
                    val crackle = if (rnd.nextDouble() < 0.02) (rnd.nextDouble() * 2 - 1) * 0.35 else 0.0
                    (rnd.nextDouble() * 2 - 1) * 0.10 + crackle
                }
                FocusSound.Flute -> {
                    // Soft pentatonic meditation motif.
                    val notes = doubleArrayOf(294.0, 330.0, 392.0, 440.0, 392.0, 330.0)
                    val note = notes[((t / 1.6).toInt()) % notes.size]
                    phase += 2 * PI * note / sampleRate
                    val env = (sin(PI * ((t / 1.6) % 1.0))).coerceAtLeast(0.0)
                    sin(phase) * 0.14 * env + sin(phase * 2) * 0.03 * env
                }
                FocusSound.BrownNoise -> {
                    brown += (rnd.nextDouble() * 2 - 1) * 0.02
                    brown *= 0.995
                    brown.coerceIn(-0.35, 0.35)
                }
            }
            out[i] = (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        // Seamless-ish edges.
        val fade = sampleRate / 20
        for (i in 0 until fade) {
            val a = i.toFloat() / fade
            out[i] = (out[i] * a).toInt().toShort()
            out[n - 1 - i] = (out[n - 1 - i] * a).toInt().toShort()
        }
        return out
    }
}

package com.lumen.launcher.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sin

object AlarmTonePlayer {
    private const val SAMPLE_RATE = 44100
    private var track: AudioTrack? = null
    private var vibrator: Vibrator? = null
    private var previewGeneration = 0

    fun preview(context: Context, tone: String) {
        val generation = ++previewGeneration
        start(context, tone, loop = false)
        android.os.Handler(context.mainLooper).postDelayed({
            if (generation == previewGeneration) stop()
        }, 2400)
    }

    fun start(context: Context, tone: String, loop: Boolean = true) {
        stop()
        val samples = render(tone)
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        created.write(samples, 0, samples.size)
        if (loop) created.setLoopPoints(0, samples.size, -1)
        created.play()
        track = created
        val vib = vibrator(context)
        vibrator = vib
        val pattern = longArrayOf(0, 280, 220, 280, 900)
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, if (loop) 0 else -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, if (loop) 0 else -1)
        }
    }

    fun stop() {
        runCatching {
            track?.stop()
            track?.release()
        }
        track = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun vibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun render(tone: String): ShortArray {
        val seconds = 2.4
        val n = (SAMPLE_RATE * seconds).toInt()
        val mix = DoubleArray(n)
        when (tone) {
            AlarmTones.PULSE -> {
                ping(mix, 440.0, 0.00, 0.42, 0.42)
                ping(mix, 659.3, 0.50, 0.42, 0.38)
                ping(mix, 440.0, 1.20, 0.42, 0.42)
                ping(mix, 659.3, 1.70, 0.42, 0.38)
            }
            AlarmTones.DAWN -> {
                ping(mix, 523.3, 0.00, 0.38, 0.34)
                ping(mix, 659.3, 0.28, 0.38, 0.34)
                ping(mix, 784.0, 0.56, 0.42, 0.36)
                ping(mix, 1046.5, 0.92, 0.70, 0.32)
                ping(mix, 523.3, 1.55, 0.55, 0.22)
            }
            AlarmTones.BELL -> {
                ping(mix, 523.3, 0.00, 1.8, 0.38)
                ping(mix, 1046.5, 0.00, 1.2, 0.16)
                ping(mix, 1568.0, 0.00, 0.7, 0.08)
                ping(mix, 523.3, 1.55, 0.7, 0.22)
            }
            else -> {
                ping(mix, 392.0, 0.00, 1.1, 0.28)
                ping(mix, 523.3, 0.35, 1.2, 0.30)
                ping(mix, 659.3, 0.80, 1.3, 0.26)
                ping(mix, 392.0, 1.55, 0.7, 0.16)
            }
        }
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = mix[i].coerceIn(-1.0, 1.0)
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun ping(mix: DoubleArray, freq: Double, startSec: Double, durSec: Double, peak: Double) {
        val start = (startSec * SAMPLE_RATE).toInt()
        val dur = (durSec * SAMPLE_RATE).toInt()
        val attack = (0.018 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        for (i in 0 until dur) {
            val idx = start + i
            if (idx !in mix.indices) return
            val env = when {
                i < attack -> i.toDouble() / attack
                else -> {
                    val t = (i - attack).toDouble() / (dur - attack).coerceAtLeast(1)
                    (1.0 - t).coerceAtLeast(0.0).let { it * it }
                }
            }
            mix[idx] += env * peak * sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
        }
    }
}

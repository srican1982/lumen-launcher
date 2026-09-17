package com.lumen.launcher.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

object SpeechRecognizers {

    private val brokenPackages = setOf(
        "com.google.android.as"
    )

    fun defaultService(context: Context): ComponentName? {
        val raw = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            ?: return null
        return ComponentName.unflattenFromString(raw)
    }

    fun isUsableEngine(context: Context, component: ComponentName?): Boolean {
        if (component == null) return false
        if (component.packageName == context.packageName) return false
        if (component.packageName in brokenPackages) return false
        val cls = component.className
        if (cls.contains("AiAi", ignoreCase = true)) return false
        if (cls.contains("offline", ignoreCase = true) &&
            component.packageName.contains("googlequicksearchbox", ignoreCase = true)
        ) return false
        return true
    }

    fun rankedEngines(context: Context): List<ComponentName> {
        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            PackageManager.MATCH_ALL
        )
        return services
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
            .filter { isUsableEngine(context, it) }
            .sortedByDescending { score(it) }
    }

    fun commandTargets(context: Context): List<ComponentName?> {
        val default = defaultService(context)
        val ranked = rankedEngines(context)
        val out = mutableListOf<ComponentName?>()
        if (isUsableEngine(context, default)) out += null
        ranked.forEach { engine ->
            if (engine != default) out += engine
        }
        return out
    }

    fun create(context: Context, engine: ComponentName?): SpeechRecognizer? {
        return if (engine == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context, engine) }.getOrNull()
        }
    }

    fun createForCommands(context: Context): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }

    fun createForWake(context: Context): SpeechRecognizer? = createForCommands(context)

    fun commandIntent(context: Context): Intent {
        val lang = Locale.getDefault().toLanguageTag()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            if (Build.VERSION.SDK_INT >= 33) {
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_BIASING_STRINGS,
                    ArrayList(VoiceHearing.biasWords())
                )
            }
        }
    }

    fun wakeIntent(context: Context): Intent = commandIntent(context)

    fun promptIntent(context: Context): Intent? {
        val intent = commandIntent(context).apply {
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Lumen")
        }
        val hit = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo.packageName != context.packageName }
            ?: return null
        intent.setPackage(hit.activityInfo.packageName)
        return intent
    }

    fun installedEngine(context: Context): ComponentName? = rankedEngines(context).firstOrNull()

    fun rankedTexts(results: Bundle?): List<String> {
        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val ranked = if (scores != null && scores.size == texts.size) {
            texts.zip(scores.toList()).sortedByDescending { it.second }.map { it.first }
        } else {
            texts
        }
        return ranked.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun score(component: ComponentName): Int {
        val pkg = component.packageName.lowercase()
        val cls = component.className.lowercase()
        return when {
            pkg.contains("googlequicksearchbox") && cls.contains("googlerecognition") -> 110
            pkg.contains("googlequicksearchbox") -> 105
            pkg == "com.google.android.tts" && cls.contains("recognition") -> 100
            pkg.contains("samsung") && cls.contains("recognition") -> 80
            pkg.contains("samsung") -> 70
            pkg.contains("google") && cls.contains("recognition") -> 60
            pkg.contains("google") -> 40
            else -> 20
        }
    }
}

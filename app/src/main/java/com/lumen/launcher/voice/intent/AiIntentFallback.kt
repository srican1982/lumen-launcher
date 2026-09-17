package com.lumen.launcher.voice.intent

import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.flow.flowModuleFromSpeech
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.VoiceMatch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini maps leftover natural language onto Lumen's closed actions.
 * Local rules still win. This never executes anything.
 */
object AiIntentFallback {

    const val MODEL = "gemini-3-flash-preview"

    private val destructive = setOf(
        VoiceAction.REMOVE_FROM_DOCK,
        VoiceAction.UNPIN_APP,
        VoiceAction.HIDE_APP,
        VoiceAction.MOVE_TO_PRIVATE,
        VoiceAction.CANCEL_ALARM
    )

    fun needsFallback(intent: VoiceIntent): Boolean {
        return intent.action == VoiceAction.UNKNOWN ||
            (!VoiceConfidence.shouldExecute(intent) && !VoiceConfidence.shouldAsk(intent))
    }

    fun interpret(
        apiKey: String,
        candidates: List<String>,
        appLabels: List<String>
    ): VoiceIntent? {
        if (apiKey.isBlank()) return null
        val said = candidates.firstOrNull { it.isNotBlank() } ?: return null
        val body = requestBody(said, appLabels)
        val raw = post(apiKey, body) ?: return null
        val text = extractText(raw) ?: return null
        return parse(text, said, appLabels)
    }

    fun parse(raw: String, original: String, appLabels: List<String> = emptyList()): VoiceIntent? {
        val json = jsonObject(raw) ?: return null
        val action = runCatching {
            VoiceAction.valueOf(json.optString("action").uppercase())
        }.getOrNull() ?: return VoiceIntent(VoiceAction.UNKNOWN, 0.20f, original)
        if (action == VoiceAction.UNKNOWN) {
            return VoiceIntent(VoiceAction.UNKNOWN, 0.20f, original)
        }
        val confidence = json.optDouble("confidence", 0.80).toFloat().coerceIn(0f, 0.96f)
        val appName = refineApp(json.optionalString("appName"), appLabels)
        val clarify = json.optJSONArray("clarify").toStringList()
        val alternatives = json.optJSONArray("options")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                parse(item.toString(), original, appLabels)
            }
        }.orEmpty()
        val intent = VoiceIntent(
            action = if (action == VoiceAction.CLARIFY || alternatives.size > 1) {
                VoiceAction.CLARIFY
            } else {
                action
            },
            confidence = confidence,
            originalText = original,
            appName = appName,
            folderName = json.optionalString("folderName"),
            module = json.optionalString("module")?.let { flowModuleFromSpeech(it) },
            beforeModule = json.optionalString("beforeModule")?.let { flowModuleFromSpeech(it) },
            space = space(json.optionalString("space")),
            enabled = if (json.has("enabled") && !json.isNull("enabled")) json.optBoolean("enabled") else null,
            intValue = json.optionalInt("intValue"),
            floatValue = json.optionalDouble("floatValue")?.toFloat(),
            textValue = json.optionalString("textValue"),
            hour = json.optionalInt("hour"),
            minute = json.optionalInt("minute"),
            daily = json.optBoolean("daily", false),
            clarify = clarify,
            alternatives = alternatives,
            destructive = action in destructive || json.optBoolean("destructive", false)
        )
        return if (intent.action == VoiceAction.CLARIFY && intent.clarify.isEmpty() && alternatives.isNotEmpty()) {
            intent.copy(clarify = alternatives.map { VoiceQueryRouter.describe(it) })
        } else {
            intent
        }
    }

    internal fun requestBody(said: String, appLabels: List<String>): String {
        val apps = appLabels.take(80).joinToString(", ")
        val actions = VoiceAction.entries.joinToString(", ") { it.name }
        val prompt = """
            You are Lumen's command interpreter inside a phone launcher.
            Select exactly one allowed action. Never execute anything. Never invent an action.
            If two meanings are close, use CLARIFY and list options.
            If the user wants an app, pick from Installed apps only.
            Installed labels are often short or abbreviated. The user may say the full name.
            Map that speech onto the installed label (Bank of America → BOA, Chase Bank → Chase).
            appName must be the installed label, never the spoken full name.
            Do not use UNKNOWN only because the spoken name is longer than the label.
            If no installed label fits, use UNKNOWN.

            Allowed actions: $actions
            Installed apps: $apps

            User said: $said

            Return JSON only with keys:
            action, confidence, appName, folderName, module, beforeModule, space,
            enabled, intValue, floatValue, textValue, hour, minute, daily, destructive,
            clarify (array of strings), options (array of the same object).
            Use null when a field does not apply. space is Home, Work, Personal, Focus, or null.
        """.trimIndent()
        val payload = JSONObject()
        payload.put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        )
        payload.put(
            "generationConfig",
            JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json")
        )
        return payload.toString()
    }

    private fun post(apiKey: String, body: String): String? {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            stream?.bufferedReader()?.readText()
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun extractText(raw: String): String? {
        val root = jsonObject(raw) ?: return raw.takeIf { it.contains("\"action\"") }
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return raw.takeIf { it.contains("\"action\"") }
        for (i in 0 until parts.length()) {
            val text = parts.optJSONObject(i)?.optString("text").orEmpty()
            if (text.contains("\"action\"")) return text
        }
        return parts.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
    }

    private fun jsonObject(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    private fun refineApp(name: String?, appLabels: List<String>): String? {
        if (name.isNullOrBlank()) return null
        if (appLabels.isEmpty()) return FuzzySearch.normalize(name)
        val label = VoiceMatch.best(name, appLabels) ?: return FuzzySearch.normalize(name)
        return FuzzySearch.normalize(label)
    }

    private fun space(raw: String?): SpaceKind? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim().lowercase()
        if (value == "auto" || value == "automatic" || value == "null") return null
        return SpaceKind.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optionalDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).trim().takeIf { item -> item.isNotBlank() } }
    }
}

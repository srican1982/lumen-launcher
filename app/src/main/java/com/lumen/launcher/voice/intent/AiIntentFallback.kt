package com.lumen.launcher.voice.intent

import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.flowModuleFromSpeech
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.VoiceMatch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini turns leftover speech into a Lumen action or a spoken answer.
 * Exact local hits still win. This never executes anything.
 */
object AiIntentFallback {

    const val MODEL = "gemini-3-flash-preview"
    private val models = listOf(MODEL, "gemini-2.5-flash")

    private val destructive = setOf(
        VoiceAction.REMOVE_FROM_DOCK,
        VoiceAction.UNPIN_APP,
        VoiceAction.HIDE_APP,
        VoiceAction.MOVE_TO_PRIVATE,
        VoiceAction.CANCEL_ALARM
    )

    data class Context(
        val apps: List<String> = emptyList(),
        val contacts: List<String> = emptyList(),
        val alarms: List<String> = emptyList(),
        val history: List<String> = emptyList(),
        val now: String = "",
        val transcripts: List<String> = emptyList()
    )

    sealed class Outcome {
        data class Ok(val intent: VoiceIntent) : Outcome()
        data class Failed(val error: Error) : Outcome()
    }

    enum class Error {
        NO_KEY,
        UNAUTHORIZED,
        QUOTA,
        OFFLINE,
        MODEL,
        EMPTY
    }

    fun needsFallback(intent: VoiceIntent): Boolean = !VoiceConfidence.shouldTrustLocal(intent)

    fun spokenError(error: Error): String {
        return when (error) {
            Error.NO_KEY -> "Add a Gemini API key in Lumen settings so I can understand more."
            Error.UNAUTHORIZED -> "That Gemini key isn't working. Check it in Lumen settings."
            Error.QUOTA -> "Gemini is out of quota right now. Try again in a bit."
            Error.OFFLINE -> "I need a network to think that through."
            Error.MODEL -> "Gemini isn't available on this key yet. Try again, or check the model in settings later."
            Error.EMPTY -> "I heard you, but I didn't get a usable answer back."
        }
    }

    fun interpret(apiKey: String, candidates: List<String>, appLabels: List<String>): VoiceIntent? {
        return when (val outcome = interpret(apiKey, candidates, Context(apps = appLabels))) {
            is Outcome.Ok -> outcome.intent
            is Outcome.Failed -> null
        }
    }

    fun interpret(apiKey: String, candidates: List<String>, context: Context): Outcome {
        if (apiKey.isBlank()) return Outcome.Failed(Error.NO_KEY)
        val said = candidates.firstOrNull { it.isNotBlank() } ?: return Outcome.Failed(Error.EMPTY)
        val saidContext = context.copy(transcripts = candidates.filter { it.isNotBlank() })
        var last = Error.EMPTY
        for (model in models) {
            for (body in requestBodies(said, saidContext, model)) {
                val reply = post(apiKey, body, model) ?: return Outcome.Failed(Error.OFFLINE)
                when {
                    reply.code in 200..299 -> {
                        val text = extractText(reply.body) ?: continue
                        val intent = parse(text, said, context.apps)
                            ?: VoiceIntent(VoiceAction.ANSWER, 0.72f, said, textValue = spokenFallback(text))
                        return Outcome.Ok(intent)
                    }
                    reply.code == 401 || reply.code == 403 -> return Outcome.Failed(Error.UNAUTHORIZED)
                    reply.code == 429 -> return Outcome.Failed(Error.QUOTA)
                    reply.code == 404 -> last = Error.MODEL
                    else -> last = Error.MODEL
                }
            }
        }
        return Outcome.Failed(last)
    }

    fun parse(raw: String, original: String, appLabels: List<String> = emptyList()): VoiceIntent? {
        val json = jsonObject(raw) ?: return null
        if (json.has("candidates") && !json.has("action")) return null
        val actionName = json.optString("action").uppercase()
        if (actionName.isBlank() && json.optionalString("textValue") == null) return null
        val action = runCatching {
            VoiceAction.valueOf(actionName)
        }.getOrNull() ?: return VoiceIntent(VoiceAction.UNKNOWN, 0.20f, original)
        if (action == VoiceAction.UNKNOWN) {
            val answer = json.optionalString("textValue")
            if (!answer.isNullOrBlank()) {
                return VoiceIntent(VoiceAction.ANSWER, 0.80f, original, textValue = answer)
            }
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
        return requestBody(said, Context(apps = appLabels, transcripts = listOf(said)))
    }

    internal fun requestBody(said: String, context: Context): String {
        return payload(requestPrompt(said, context), jsonMime = true, thinking = null).toString()
    }

    private fun requestBodies(said: String, context: Context, model: String): List<String> {
        val prompt = requestPrompt(said, context)
        return if (model.startsWith("gemini-3")) {
            listOf(
                payload(prompt, jsonMime = false, thinking = "level").toString(),
                payload(prompt, jsonMime = false, thinking = "off").toString(),
                payload(prompt, jsonMime = true, thinking = null).toString()
            )
        } else {
            listOf(
                payload(prompt, jsonMime = true, thinking = "off").toString(),
                payload(prompt, jsonMime = false, thinking = "off").toString()
            )
        }
    }

    private fun requestPrompt(said: String, context: Context): String {
        val apps = context.apps.take(80).joinToString(", ")
        val contacts = context.contacts.take(40).joinToString(", ")
        val alarms = context.alarms.take(8).joinToString("; ")
        val history = context.history.takeLast(8).joinToString("\n")
        val heard = context.transcripts.ifEmpty { listOf(said) }.joinToString(" | ")
        val actions = VoiceAction.entries.joinToString(", ") { it.name }
        return """
            You are Lumen, the voice of this Android launcher.
            Local rules already tried. Interpret what the person meant.
            You may answer questions. Put a short spoken reply in textValue and use ANSWER.
            You may call, message, set a timer, navigate, play media, or toggle the torch.
            Never invent an action outside the allowed list.
            If two launcher meanings are close, use CLARIFY and list options.
            If the user wants an app, pick from Installed apps only.
            Installed labels are often short or abbreviated. The user may say the full name.
            Map that speech onto the installed label (Bank of America → BOA, Chase Bank → Chase).
            appName must be the installed label, never the spoken full name.
            Do not use UNKNOWN only because the spoken name is longer than the label.
            If they ask a general question, use ANSWER. Do not force it onto an app.
            Use conversation history for words like him, that, or make it 8 instead.
            For CALL or SEND_MESSAGE, put the person in appName or textValue.
            For SET_TIMER, intValue is minutes.
            For NAVIGATE or PLAY_MEDIA, put the place or query in textValue.
            If a slot is missing, use ASK_USER and put the follow-up in textValue.

            Allowed actions: $actions
            Now: ${context.now.ifBlank { "unknown" }}
            Installed apps: $apps
            Contacts: ${contacts.ifBlank { "none on device" }}
            Alarms: ${alarms.ifBlank { "none" }}
            Recent turns:
            ${history.ifBlank { "(none)" }}

            Speech alternatives: $heard
            User said: $said

            Return JSON only with keys:
            action, confidence, appName, folderName, module, beforeModule, space,
            enabled, intValue, floatValue, textValue, hour, minute, daily, destructive,
            clarify (array of strings), options (array of the same object).
            Use null when a field does not apply. space is Home, Work, Personal, Focus, or null.
        """.trimIndent()
    }

    private fun payload(prompt: String, jsonMime: Boolean, thinking: String?): JSONObject {
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
        val config = JSONObject()
            .put("temperature", 0.2)
            .put("maxOutputTokens", 2048)
        if (jsonMime) config.put("responseMimeType", "application/json")
        when (thinking) {
            "level" -> config.put(
                "thinkingConfig",
                JSONObject().put("thinkingLevel", "MINIMAL")
            )
            "off" -> config.put(
                "thinkingConfig",
                JSONObject().put("thinkingBudget", 0)
            )
        }
        payload.put("generationConfig", config)
        return payload
    }

    private data class Reply(val code: Int, val body: String)

    private fun post(apiKey: String, body: String, model: String): Reply? {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 18_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            Reply(code, text)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    internal fun extractText(raw: String): String? {
        val root = jsonObject(raw) ?: return raw.takeIf { it.isNotBlank() }
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        if (parts == null) {
            return raw.takeIf { it.contains("\"action\"") || looksLikeSpeech(raw) }
        }
        val texts = (0 until parts.length()).mapNotNull { index ->
            val part = parts.optJSONObject(index) ?: return@mapNotNull null
            if (part.optBoolean("thought") || part.optBoolean("thoughts")) return@mapNotNull null
            part.optString("text").trim().takeIf { it.isNotBlank() }
        }
        texts.firstOrNull { it.contains("\"action\"") }?.let { return it }
        val joined = texts.joinToString("\n").trim()
        return joined.takeIf { it.isNotBlank() }
    }

    private fun spokenFallback(text: String): String {
        val cleaned = text
            .replace(Regex("```json|```"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(220).ifBlank { "I heard you, but I couldn't turn that into an action." }
    }

    private fun looksLikeSpeech(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isNotBlank() && !trimmed.startsWith("{") && trimmed.length < 400
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

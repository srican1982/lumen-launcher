package com.lumen.launcher.voice.intent

import com.lumen.launcher.data.LlmClient
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.flowModuleFromSpeech
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.VoiceMatch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini turns leftover speech into a Lumen action or a spoken answer.
 * Exact local hits still win. This never executes anything.
 */
object AiIntentFallback {

    const val MODEL = "gemini-3-flash-preview"
    private val models = listOf(MODEL, "gemini-2.5-flash")
    private val openRouterModels = listOf(
        "google/gemini-2.5-flash",
        "google/gemini-2.0-flash-001"
    )

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
            Error.NO_KEY -> "Add a Gemini or OpenRouter key in Lumen settings so I can understand more."
            Error.UNAUTHORIZED -> "That API key isn't working. Save it again in Lumen settings."
            Error.QUOTA -> "The model is out of quota right now. Try again in a bit."
            Error.OFFLINE -> "I need a network to think that through."
            Error.MODEL -> "That key's model isn't available right now. Try again, or check the key in settings."
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
        return if (LlmClient.kind(apiKey) == LlmClient.Kind.OpenRouter) {
            interpretOpenRouter(apiKey, said, saidContext, context.apps)
        } else {
            interpretGemini(apiKey, said, saidContext, context.apps)
        }
    }

    private fun interpretGemini(
        apiKey: String,
        said: String,
        saidContext: Context,
        apps: List<String>
    ): Outcome {
        var last = Error.EMPTY
        for (model in models) {
            for (body in requestBodies(said, saidContext, model)) {
                val reply = LlmClient.postGemini(apiKey, model, body) ?: return Outcome.Failed(Error.OFFLINE)
                when (val outcome = readReply(reply, said, apps, last)) {
                    is Outcome.Ok -> return outcome
                    is Outcome.Failed -> {
                        if (outcome.error == Error.UNAUTHORIZED || outcome.error == Error.QUOTA) {
                            return outcome
                        }
                        last = outcome.error
                    }
                }
            }
        }
        return Outcome.Failed(last)
    }

    private fun interpretOpenRouter(
        apiKey: String,
        said: String,
        saidContext: Context,
        apps: List<String>
    ): Outcome {
        val prompt = requestPrompt(said, saidContext)
        var last = Error.EMPTY
        for (model in openRouterModels) {
            for (json in listOf(true, false)) {
                val reply = LlmClient.postOpenRouter(apiKey, model, prompt, json, 2048)
                    ?: return Outcome.Failed(Error.OFFLINE)
                when (val outcome = readReply(reply, said, apps, last)) {
                    is Outcome.Ok -> return outcome
                    is Outcome.Failed -> {
                        if (outcome.error == Error.UNAUTHORIZED || outcome.error == Error.QUOTA) {
                            return outcome
                        }
                        last = outcome.error
                    }
                }
            }
        }
        return Outcome.Failed(last)
    }

    private fun readReply(
        reply: LlmClient.Reply,
        said: String,
        apps: List<String>,
        last: Error
    ): Outcome {
        return when {
            reply.code in 200..299 -> {
                val text = extractText(reply.body) ?: return Outcome.Failed(Error.EMPTY)
                val intent = parse(text, said, apps)
                    ?: VoiceIntent(VoiceAction.ANSWER, 0.72f, said, textValue = spokenFallback(text))
                Outcome.Ok(intent)
            }
            reply.code == 401 || reply.code == 403 -> Outcome.Failed(Error.UNAUTHORIZED)
            reply.code == 429 -> Outcome.Failed(Error.QUOTA)
            reply.code == 404 -> Outcome.Failed(Error.MODEL)
            else -> Outcome.Failed(if (last == Error.EMPTY) Error.MODEL else last)
        }
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
            Use null when a field does not apply. space is Home, Work, Social (or Personal), Focus, or null.
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

    internal fun extractText(raw: String): String? = LlmClient.extractText(raw)

    private fun spokenFallback(text: String): String {
        val cleaned = text
            .replace(Regex("```json|```"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(220).ifBlank { "I heard you, but I couldn't turn that into an action." }
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
        return SpaceKind.fromSpeechAlias(raw)
            ?: SpaceKind.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
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

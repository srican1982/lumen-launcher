package com.lumen.launcher.voice.intent

import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.VoiceMatch
import com.lumen.launcher.voice.VoiceHearing

object VoiceQueryRouter {

    fun route(candidates: List<String>, appLabels: List<String> = emptyList(), folderNames: List<String> = emptyList()): VoiceIntent {
        val actual = candidates.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
        if (actual.isEmpty()) return VoiceIntent(VoiceAction.UNKNOWN, 0f, "")
        fun evaluate(raw: String, penalty: Float): List<VoiceIntent> {
            appLabels.firstOrNull { it.equals(raw.trim(), true) }?.let {
                return listOf(VoiceIntent(VoiceAction.OPEN_APP, (.99f - penalty).coerceAtLeast(0f), raw, appName = FuzzySearch.normalize(it)))
            }
            val text = NaturalVoicePhrases.normalize(FolderVoiceNames.normalize(raw, folderNames), appLabels)
            val basic = localRequest(raw, appLabels) ?: LocalVoiceBasics.parse(raw, appLabels)
            val hits = if (basic != null) listOf(basic) else DeterministicIntentParser.parse(text) + SemanticIntentMatcher.match(text)
            val resolved = hits.map { refineApp(it.copy(originalText = raw), appLabels) }
                .filterNot { unresolvedApp(it, appLabels) }
            val explicit = resolved.firstOrNull { it.action == VoiceAction.OPEN_APP &&
                (appLabels.isEmpty() || VoiceMatch.best(it.appName.orEmpty(), appLabels) != null) }
            val choices = if (explicit != null) listOf(explicit.copy(confidence = 0.99f)) else resolved
            val pool = choices.ifEmpty { listOfNotNull(resolveBareApp(text, appLabels)?.copy(originalText = raw)) }
            return pool.map { it.copy(confidence = (it.confidence - penalty).coerceAtLeast(0f)) }
        }
        val first = rank(evaluate(actual.first(), 0f))
        // A confident real top transcript beats all other transcripts and generated repairs.
        if (first != null && first.action !in setOf(VoiceAction.UNKNOWN, VoiceAction.CLARIFY) && first.confidence >= VoiceConfidence.LOCAL_TRUST) return requireEntities(first)
        val hits = actual.flatMapIndexed { i, text -> evaluate(text, i * 0.18f) }.toMutableList()
        actual.forEachIndexed { i, text ->
            val normalized = NaturalVoicePhrases.normalize(FolderVoiceNames.normalize(text, folderNames), appLabels)
            VoiceHearing.expand(normalized, appLabels).filter { it != normalized }.take(12).forEach { variant ->
                hits += evaluate(variant, i * 0.18f + 0.22f).map { it.copy(originalText = text) }
            }
        }
        rank(hits)?.let { return requireEntities(it) }
        val normalized = NaturalVoicePhrases.normalize(actual.first(), appLabels)
        // Offer app guesses only for app-like requests. An unrelated question can still use AI.
        if (appLabels.isNotEmpty() && (normalized.startsWith("open ") || normalized.startsWith("ask "))) {
            val wanted = normalized.removePrefix("open ").removePrefix("ask ").substringBefore(" what ").trim()
            val suggestions = appLabels.distinct().sortedByDescending { label ->
                maxOf(FuzzySearch.score(wanted, label), VoiceMatch.score(wanted, label),
                    400 - FuzzySearch.levenshtein(wanted.replace(" ", ""), FuzzySearch.normalize(label).replace(" ", ""), 12) * 25)
            }.take(3)
            return VoiceIntent(VoiceAction.CLARIFY, 0.6f, actual.first(), clarify = suggestions,
                alternatives = suggestions.map { VoiceIntent(VoiceAction.OPEN_APP, 0.6f, actual.first(), appName = FuzzySearch.normalize(it)) })
        }
        return VoiceIntent(VoiceAction.UNKNOWN, 0.25f, actual.first())
    }

    private fun localRequest(raw: String, labels: List<String>): VoiceIntent? {
        val text = LocalVoiceBasics.commandText(raw)
        Regex("""take me to\s+(.+)""", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
            val destination = it.groupValues[1].removePrefix("the ")
            val app = VoiceMatch.best(destination, labels, 920)
            return if (app != null) VoiceIntent(VoiceAction.OPEN_APP, .98f, raw, appName = FuzzySearch.normalize(app))
            else VoiceIntent(VoiceAction.NAVIGATE, .98f, raw, textValue = it.groupValues[1])
        }
        val ask = Regex("""(?:ask\s+(.+)|open\s+(.+?)\s+and ask\s+(.+))""", RegexOption.IGNORE_CASE).matchEntire(text) ?: return null
        val known = labels.filter { FuzzySearch.normalize(it).replace(" ", "") in setOf("chatgpt", "gemini", "claude", "copilot", "perplexity") }
        if (ask.groupValues[2].isNotBlank()) {
            val app = VoiceMatch.best(ask.groupValues[2], known, 920) ?: return null
            return VoiceIntent(VoiceAction.OPEN_APP, .98f, raw, appName = FuzzySearch.normalize(app), textValue = ask.groupValues[3])
        }
        val remainder = ask.groupValues[1]
        for (count in 1..minOf(3, remainder.split(' ').size - 1)) {
            val words = remainder.split(' ')
            val app = known.firstOrNull { FuzzySearch.normalize(it).replace(" ", "") == FuzzySearch.normalize(words.take(count).joinToString(" ")).replace(" ", "") } ?: continue
            return VoiceIntent(VoiceAction.OPEN_APP, .98f, raw, appName = FuzzySearch.normalize(app), textValue = words.drop(count).joinToString(" "))
        }
        return null
    }

    private fun rank(hits: List<VoiceIntent>): VoiceIntent? {
        val ranked = hits.groupBy { it.key }.map { (_, group) -> group.maxBy { it.confidence } }.sortedByDescending { it.confidence }
        val best = ranked.firstOrNull() ?: return null
        val rival = ranked.firstOrNull { it.action != best.action && kotlin.math.abs(it.confidence - best.confidence) < 0.08f }
        if (rival != null) return VoiceIntent(VoiceAction.CLARIFY, best.confidence, best.originalText,
            clarify = listOf(describe(best), describe(rival)).distinct(), alternatives = listOf(best, rival))
        return best
    }

    private fun unresolvedApp(intent: VoiceIntent, appLabels: List<String>): Boolean {
        val needsApp = intent.action in setOf(
            VoiceAction.OPEN_APP,
            VoiceAction.HIDE_APP,
            VoiceAction.UNHIDE_APP,
            VoiceAction.PIN_APP,
            VoiceAction.UNPIN_APP,
            VoiceAction.ADD_TO_DOCK,
            VoiceAction.REMOVE_FROM_DOCK,
            VoiceAction.ADD_TO_FOLDER,
            VoiceAction.MOVE_TO_PRIVATE,
            VoiceAction.REMOVE_FROM_PRIVATE,
            VoiceAction.WHERE_APP,
            VoiceAction.LEARN_ALIAS
        )
        if (!needsApp) return false
        val name = intent.appName?.takeIf { it.isNotBlank() } ?: return false
        if (appLabels.isEmpty()) return false
        return VoiceMatch.best(name, appLabels) == null
    }

    private fun refineApp(intent: VoiceIntent, appLabels: List<String>): VoiceIntent {
        if (intent.action in setOf(VoiceAction.CALL, VoiceAction.SEND_MESSAGE)) return intent
        val name = intent.appName ?: return intent
        if (appLabels.isEmpty()) return intent
        val label = VoiceMatch.best(name, appLabels) ?: return intent
        return intent.copy(appName = FuzzySearch.normalize(label))
    }

    private fun requireEntities(intent: VoiceIntent): VoiceIntent {
        val needsApp = intent.action in setOf(
            VoiceAction.OPEN_APP,
            VoiceAction.ADD_TO_DOCK,
            VoiceAction.REMOVE_FROM_DOCK,
            VoiceAction.PIN_APP,
            VoiceAction.UNPIN_APP,
            VoiceAction.HIDE_APP,
            VoiceAction.UNHIDE_APP,
            VoiceAction.MOVE_TO_PRIVATE,
            VoiceAction.REMOVE_FROM_PRIVATE,
            VoiceAction.WHERE_APP,
            VoiceAction.LEARN_ALIAS,
            VoiceAction.ADD_TO_FOLDER
        )
        if (needsApp && intent.appName.isNullOrBlank()) {
            return VoiceIntent(
                action = VoiceAction.CLARIFY,
                confidence = intent.confidence,
                originalText = intent.originalText,
                clarify = listOf("tell me which app"),
                alternatives = listOf(intent)
            )
        }
        if (intent.action == VoiceAction.ADD_TO_FOLDER && intent.folderName.isNullOrBlank()) {
            return VoiceIntent(
                action = VoiceAction.CLARIFY,
                confidence = intent.confidence,
                originalText = intent.originalText,
                clarify = listOf("tell me which folder"),
                alternatives = listOf(intent)
            )
        }
        return intent
    }

    private fun resolveBareApp(text: String, appLabels: List<String>): VoiceIntent? {
        if (appLabels.isEmpty()) return null
        val stripped = text
            .removePrefix("open up ")
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .removePrefix("run ")
            .removePrefix("go to ")
            .trim()
        if (stripped.contains(' ') && stripped.split(' ').size > 3) return null
        val label = VoiceMatch.best(stripped, appLabels) ?: return null
        val score = VoiceMatch.score(stripped, label)
        val confidence = if (score >= 980) 0.92f else 0.70f
        return VoiceIntent(VoiceAction.OPEN_APP, confidence, text, appName = FuzzySearch.normalize(label))
    }

    internal fun describe(intent: VoiceIntent): String {
        val app = intent.appName
        return when (intent.action) {
            VoiceAction.OPEN_APP -> "open ${app ?: "that app"}"
            VoiceAction.ADD_TO_DOCK -> "add ${app ?: "it"} to the dock"
            VoiceAction.PIN_APP -> "pin ${app ?: "it"} to Home"
            VoiceAction.UNPIN_APP -> "remove ${app ?: "it"} from Home"
            VoiceAction.HIDE_APP -> "hide ${app ?: "it"} from Lumen"
            VoiceAction.MOVE_TO_PRIVATE -> "move ${app ?: "it"} to Locked Space"
            VoiceAction.REMOVE_FROM_PRIVATE -> "take ${app ?: "it"} out of Locked Space"
            VoiceAction.WHERE_APP -> "find ${app ?: "that app"}"
            VoiceAction.LEARN_ALIAS -> "remember that name"
            VoiceAction.SET_LABELS -> if (intent.enabled == false) "hide app labels" else "show app labels"
            VoiceAction.SET_ICON_SIZE -> if ((intent.floatValue ?: 0f) < 0f) "make icons smaller" else "make icons bigger"
            VoiceAction.SET_GRID -> "fit more apps per row"
            VoiceAction.SET_SPACE -> "switch space"
            VoiceAction.CALL -> "prepare a call to ${intent.recipientName ?: app ?: "a contact"}${intent.messagingApp?.let { " using $it" }.orEmpty()}"
            VoiceAction.SEND_MESSAGE -> "prepare a message to ${intent.recipientName ?: app ?: "a contact"}${intent.messagingApp?.let { " using $it" }.orEmpty()}${intent.textValue?.let { ": $it" }.orEmpty()}"
            VoiceAction.SET_ALARM -> "set an alarm for ${intent.hour ?: 0}:${(intent.minute ?: 0).toString().padStart(2, '0')}${if (intent.daily) " every day" else if (intent.originalText.contains("tomorrow", true)) " tomorrow" else ""}"
            VoiceAction.CANCEL_ALARM -> "cancel ${intent.textValue ?: "the alarm"}"
            VoiceAction.DELETE_TASK -> "delete ${intent.textValue ?: "that task"}"
            else -> intent.action.name.lowercase().replace('_', ' ')
        }
    }
}

package com.lumen.launcher.voice.intent

import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.VoiceMatch
import com.lumen.launcher.voice.VoiceHearing

object VoiceQueryRouter {

    fun route(candidates: List<String>, appLabels: List<String> = emptyList()): VoiceIntent {
        val texts = VoiceHearing.expandAll(candidates, appLabels)
        if (texts.isEmpty()) return VoiceIntent(VoiceAction.UNKNOWN, 0f, "")

        // An explicit request for an installed app is unambiguous. Resolve it
        // before semantic matching so another intent cannot steal the command.
        texts.forEachIndexed { index, text ->
            val parsed = DeterministicIntentParser.parse(text)
                .firstOrNull { it.action == VoiceAction.OPEN_APP }
            val name = parsed?.appName
            if (parsed != null && !name.isNullOrBlank()) {
                val label = VoiceMatch.best(name, appLabels)
                if (label != null) {
                    return parsed.copy(
                        confidence = (0.99f - index * 0.05f).coerceAtLeast(0f),
                        appName = FuzzySearch.normalize(label)
                    )
                }
            }
        }

        val hits = texts.flatMapIndexed { index, text ->
            val rankPenalty = index * 0.05f
            (DeterministicIntentParser.parse(text) + SemanticIntentMatcher.match(text)).map {
                it.copy(confidence = (it.confidence - rankPenalty).coerceAtLeast(0f))
            }
        }.map { refineApp(it, appLabels) }
            .filterNot { unresolvedApp(it, appLabels) }

        if (hits.isEmpty()) {
            texts.forEach { text ->
                resolveBareApp(text, appLabels)?.let { return it }
            }
            return VoiceIntent(VoiceAction.UNKNOWN, 0.25f, texts.first())
        }

        val ranked = hits
            .groupBy { it.key }
            .map { (_, group) -> group.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }

        val best = ranked.first()
        val rival = ranked.firstOrNull { it.action != best.action && kotlin.math.abs(it.confidence - best.confidence) < 0.08f }
        if (rival != null) {
            return VoiceIntent(
                action = VoiceAction.CLARIFY,
                confidence = best.confidence,
                originalText = best.originalText,
                clarify = listOf(describe(best), describe(rival)).distinct(),
                alternatives = listOf(best, rival)
            )
        }
        return requireEntities(best)
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
            else -> intent.action.name.lowercase().replace('_', ' ')
        }
    }
}

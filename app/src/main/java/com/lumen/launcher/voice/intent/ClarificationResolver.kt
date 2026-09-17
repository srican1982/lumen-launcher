package com.lumen.launcher.voice.intent

import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.VoiceMatch
import com.lumen.launcher.voice.VoiceQuery

sealed interface ClarificationResolution {
    data class Execute(val intent: VoiceIntent) : ClarificationResolution
    data object Cancel : ClarificationResolution
    data object End : ClarificationResolution
    data class Ask(val message: String) : ClarificationResolution
    data object NoMatch : ClarificationResolution
}

object ClarificationResolver {
    private val yes = setOf("yes", "yeah", "yep", "correct", "do it", "please do", "that's right", "thats right")
    private val no = setOf("no", "nope", "nah", "cancel", "don't", "dont", "never mind", "nevermind", "not that")
    private val end = setOf("thanks", "thank you", "goodbye", "good bye", "bye", "that's all", "thats all")

    fun resolve(
        candidates: List<String>,
        pending: VoiceIntent,
        appLabels: List<String> = emptyList()
    ): ClarificationResolution {
        val replies = candidates.map { VoiceQuery.clean(it) }.filter { it.isNotBlank() }
        if (replies.any { it in end }) return ClarificationResolution.End
        if (replies.any { it in no }) return ClarificationResolution.Cancel

        val options = pending.alternatives.ifEmpty {
            if (pending.action == VoiceAction.CLARIFY) emptyList() else listOf(pending)
        }
        if (replies.any { it in yes }) {
            return if (options.size == 1) {
                ClarificationResolution.Execute(confirmed(options.first()))
            } else {
                ClarificationResolution.Ask("Which option do you want?")
            }
        }

        replies.forEach { reply ->
            ordinal(reply, options.size)?.let { index ->
                return ClarificationResolution.Execute(confirmed(options[index]))
            }

            if (options.size == 1 && options.first().appName.isNullOrBlank()) {
                resolveApp(reply, appLabels)?.let { app ->
                    return ClarificationResolution.Execute(
                        confirmed(options.first().copy(appName = app))
                    )
                }
            }
            if (
                options.size == 1 &&
                options.first().action == VoiceAction.ADD_TO_FOLDER &&
                options.first().folderName.isNullOrBlank() &&
                reply.length >= 2
            ) {
                return ClarificationResolution.Execute(
                    confirmed(options.first().copy(folderName = reply))
                )
            }

            val routed = VoiceQueryRouter.route(listOf(reply), appLabels)
            options.firstOrNull { sameChoice(it, routed) }?.let {
                return ClarificationResolution.Execute(confirmed(it))
            }
        }
        return ClarificationResolution.NoMatch
    }

    private fun confirmed(intent: VoiceIntent): VoiceIntent {
        return intent.copy(confidence = 1f, destructive = false, alternatives = emptyList(), clarify = emptyList())
    }

    private fun resolveApp(reply: String, appLabels: List<String>): String? {
        val label = appLabels.maxByOrNull { VoiceMatch.score(reply, it) } ?: return null
        if (VoiceMatch.score(reply, label) < 860) return null
        return FuzzySearch.normalize(label)
    }

    private fun sameChoice(expected: VoiceIntent, actual: VoiceIntent): Boolean {
        if (expected.action != actual.action) return false
        if (expected.appName != null && actual.appName != expected.appName) return false
        if (expected.folderName != null && actual.folderName != expected.folderName) return false
        if (expected.module != null && actual.module != expected.module) return false
        if (expected.beforeModule != null && actual.beforeModule != expected.beforeModule) return false
        if (expected.action == VoiceAction.SET_SPACE && actual.space != expected.space) return false
        if (expected.enabled != null && actual.enabled != expected.enabled) return false
        if (expected.intValue != null && actual.intValue != expected.intValue) return false
        if (expected.floatValue != null && actual.floatValue != expected.floatValue) return false
        return true
    }

    private fun ordinal(reply: String, size: Int): Int? {
        val index = when {
            reply in setOf("first", "first one", "option one", "one", "1") -> 0
            reply in setOf("second", "second one", "option two", "two", "2") -> 1
            reply in setOf("third", "third one", "option three", "three", "3") -> 2
            else -> return null
        }
        return index.takeIf { it < size }
    }
}

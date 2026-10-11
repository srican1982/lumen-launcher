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
    private val appActions = setOf(VoiceAction.OPEN_APP, VoiceAction.ADD_TO_DOCK, VoiceAction.REMOVE_FROM_DOCK, VoiceAction.PIN_APP, VoiceAction.UNPIN_APP, VoiceAction.HIDE_APP, VoiceAction.UNHIDE_APP, VoiceAction.MOVE_TO_PRIVATE, VoiceAction.REMOVE_FROM_PRIVATE, VoiceAction.WHERE_APP, VoiceAction.LEARN_ALIAS, VoiceAction.ADD_TO_FOLDER)
    private val yes = setOf("yes", "yeah", "yep", "correct", "do it", "please do", "that's right", "thats right")
    private val no = setOf("no", "nope", "nah", "cancel", "don't", "dont", "never mind", "nevermind", "not that")
    private val end = setOf("thanks", "thank you", "goodbye", "good bye", "bye", "that's all", "thats all")

    fun resolve(
        candidates: List<String>,
        pending: VoiceIntent,
        appLabels: List<String> = emptyList()
    ): ClarificationResolution {
        val replies = candidates.map { VoiceQuery.clean(it) }.filter { it.isNotBlank() }
        val primary = replies.firstOrNull()
        if (primary in end) return ClarificationResolution.End
        if (primary in no) return ClarificationResolution.Cancel

        if (pending.missingSlot != null) {
            if (primary in yes) return ClarificationResolution.Ask(pending.textValue.orEmpty())
            val raw = candidates.firstOrNull()?.trim().orEmpty()
            val target = pending.alternatives.singleOrNull() ?: return ClarificationResolution.NoMatch
            // An explicit new command replaces the pending slot. A bare contact/name does not.
            val routed = VoiceQueryRouter.route(listOf(raw), appLabels)
            val explicitCommand = routed.clockQuery != null || Regex(
                """^(?:call|phone|dial|text|message|send|open|launch|navigate|directions|take me to|set|start|turn|switch|ask|show|hide|delete|remind|play|stop|end|save|calculate|what|where|how)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(LocalVoiceBasics.commandText(raw))
            if (explicitCommand && routed.confidence >= .88f && routed.action !in setOf(VoiceAction.UNKNOWN, VoiceAction.CLARIFY)) return ClarificationResolution.NoMatch
            val filled = when (pending.missingSlot) {
                VoiceSlot.CONTACT -> LocalVoiceBasics.parse("${if (target.action == VoiceAction.CALL) "call" else "message"} $raw", appLabels)
                VoiceSlot.DURATION -> LocalVoiceBasics.parse("timer for $raw", appLabels)?.takeIf { it.missingSlot == null }
                VoiceSlot.ALARM_TIME -> com.lumen.launcher.search.VoiceCommands.alarm("set alarm for $raw")?.let {
                    target.copy(hour = it.hour, minute = it.minute, daily = it.daily)
                }
                VoiceSlot.APP -> resolveApp(primary.orEmpty(), appLabels)?.let { target.copy(appName = it) }
            } ?: return ClarificationResolution.Ask(pending.textValue.orEmpty())
            return ClarificationResolution.Execute(filled.copy(originalText = "${target.originalText} $raw", confirmed = false))
        }

        val options = pending.alternatives.ifEmpty {
            if (pending.action == VoiceAction.CLARIFY) emptyList() else listOf(pending)
        }
        if (primary in yes) {
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

            if (options.size == 1 && options.first().action in appActions && options.first().appName.isNullOrBlank()) {
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

    fun confirmed(intent: VoiceIntent): VoiceIntent {
        return intent.copy(confidence = 1f, confirmed = true, destructive = false, alternatives = emptyList(), clarify = emptyList())
    }

    private fun resolveApp(reply: String, appLabels: List<String>): String? {
        val label = appLabels.maxByOrNull { VoiceMatch.score(reply, it) } ?: return null
        if (VoiceMatch.score(reply, label) < 860) return null
        return FuzzySearch.normalize(label)
    }

    private fun sameChoice(expected: VoiceIntent, actual: VoiceIntent): Boolean {
        if (expected.action != actual.action) return false
        if (expected.appName != null && actual.appName != expected.appName) return false
        if (expected.recipientName != null && actual.recipientName != expected.recipientName) return false
        if (expected.messagingApp != null && actual.messagingApp != expected.messagingApp) return false
        if (expected.durationSeconds != null && actual.durationSeconds != expected.durationSeconds) return false
        if (expected.phoneNumber != null && actual.phoneNumber != expected.phoneNumber) return false
        if (expected.folderName != null && actual.folderName != expected.folderName) return false
        if (expected.module != null && actual.module != expected.module) return false
        if (expected.beforeModule != null && actual.beforeModule != expected.beforeModule) return false
        if (expected.action == VoiceAction.SET_SPACE && actual.space != expected.space) return false
        if (expected.enabled != null && actual.enabled != expected.enabled) return false
        if (expected.intValue != null && actual.intValue != expected.intValue) return false
        if (expected.floatValue != null && actual.floatValue != expected.floatValue) return false
        if (expected.textValue != null && actual.textValue != expected.textValue) return false
        if (expected.hour != null && actual.hour != expected.hour) return false
        if (expected.minute != null && actual.minute != expected.minute) return false
        if (expected.daily != actual.daily) return false
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

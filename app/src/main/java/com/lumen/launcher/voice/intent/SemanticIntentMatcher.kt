package com.lumen.launcher.voice.intent

internal object SemanticIntentMatcher {

    private val stop = setOf(
        "a", "an", "the", "my", "me", "to", "on", "of", "for", "can", "you", "please",
        "just", "would", "could", "will", "i", "we", "it", "this", "that", "with",
        "from", "at", "in", "and", "or", "do", "did", "is", "are", "be"
    )

    private data class Example(
        val action: VoiceAction,
        val phrases: List<String>,
        val enabled: Boolean? = null,
        val floatValue: Float? = null,
        val textValue: String? = null,
        val destructive: Boolean = false
    )

    private val catalog = listOf(
        Example(
            VoiceAction.SET_LABELS,
            listOf(
                "get rid of the names under the icons",
                "hide the names under the icons",
                "remove names under icons",
                "don't show app names",
                "turn off icon names"
            ),
            enabled = false
        ),
        Example(
            VoiceAction.SET_LABELS,
            listOf(
                "put the names back under the icons",
                "show the names under the icons",
                "i want app names visible"
            ),
            enabled = true
        ),
        Example(
            VoiceAction.ADD_TO_DOCK,
            listOf(
                "put it down at the bottom with my other main apps",
                "put that with my main apps at the bottom",
                "add it to the bottom row",
                "keep it in the dock"
            )
        ),
        Example(
            VoiceAction.SET_ICON_SIZE,
            listOf(
                "make everything on my home a little smaller",
                "make home icons smaller",
                "shrink the icons",
                "icons are too big"
            ),
            floatValue = -8f
        ),
        Example(
            VoiceAction.SET_ICON_SIZE,
            listOf(
                "make everything on my home a little bigger",
                "make home icons bigger",
                "icons are too small"
            ),
            floatValue = 8f
        ),
        Example(
            VoiceAction.SET_GRID,
            listOf(
                "fit more apps on each row",
                "home is too crowded",
                "make my home screen less crowded",
                "more apps per row"
            ),
            textValue = "clarify-grid"
        ),
        Example(
            VoiceAction.PURPOSE,
            listOf(
                "show me stuff i use for money",
                "apps for money",
                "things i use to pay bills"
            ),
            textValue = "finance apps"
        ),
        Example(
            VoiceAction.NEED_NOW,
            listOf(
                "what should i open right now",
                "what do i usually open at this hour"
            )
        ),
        Example(
            VoiceAction.WEATHER,
            listOf("do i need a jacket", "is it raining", "how's it outside", "how is it outside")
        ),
        Example(
            VoiceAction.OPEN_DRAWER,
            listOf("show me all my apps", "i want to see every app")
        )
    )

    fun match(text: String): List<VoiceIntent> {
        val q = text.lowercase().trim().replace(Regex("\\s+"), " ")
        if (q.length < 8) return emptyList()
        val scored = catalog.mapNotNull { example ->
            val best = example.phrases.maxOf { phraseScore(q, it) }
            if (best < 0.72f) return@mapNotNull null
            val action = if (example.textValue == "clarify-grid") VoiceAction.CLARIFY else example.action
            val alternatives = if (example.textValue == "clarify-grid") {
                listOf(
                    VoiceIntent(VoiceAction.SET_ICON_SIZE, 0.90f, text, floatValue = -8f),
                    VoiceIntent(VoiceAction.SET_GRID, 0.90f, text, intValue = 5),
                    VoiceIntent(VoiceAction.SET_LABELS, 0.90f, text, enabled = false)
                )
            } else {
                emptyList()
            }
            VoiceIntent(
                action = action,
                confidence = (0.70f + (best - 0.72f) * 0.8f).coerceAtMost(0.93f),
                originalText = text,
                enabled = example.enabled,
                floatValue = example.floatValue,
                intValue = if (action == VoiceAction.SET_GRID) 5 else null,
                textValue = if (action == VoiceAction.CLARIFY) null else example.textValue,
                clarify = if (action == VoiceAction.CLARIFY) {
                    listOf("reduce icon size", "use 5 columns", "hide labels")
                } else emptyList(),
                alternatives = alternatives,
                destructive = example.destructive
            )
        }
        return scored.sortedByDescending { it.confidence }
    }

    internal fun phraseScore(utterance: String, example: String): Float {
        if (utterance == example) return 1f
        val u = content(utterance)
        val e = content(example)
        if (u.isEmpty() || e.isEmpty()) return 0f
        val overlap = u.intersect(e).size.toFloat()
        val jaccard = overlap / u.union(e).size.toFloat()
        val coverage = e.count { it in u }.toFloat() / e.size
        return 0.35f * jaccard + 0.65f * coverage
    }

    private fun content(text: String): Set<String> {
        return text.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in stop }
            .toSet()
    }
}

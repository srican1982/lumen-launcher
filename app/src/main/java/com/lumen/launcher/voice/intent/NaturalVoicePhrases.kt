package com.lumen.launcher.voice.intent

import com.lumen.launcher.voice.VoiceQuery

/** Whole-request paraphrases only: never replace words inside a note or app name. */
internal object NaturalVoicePhrases {
    fun normalize(raw: String, appLabels: List<String> = emptyList()): String {
        val q = VoiceQuery.clean(raw)
        val exact = mapOf(
            "what do i have to do" to "my tasks",
            "what do i need to do today" to "my tasks",
            "what is on my agenda" to "next event",
            "show me my schedule" to "next event",
            "show me every app" to "show all apps",
            "let me see all my apps" to "show all apps",
            "take me to my home screen" to "open home",
            "take me to settings" to "open settings",
            "take me to private space" to "open private space",
            "help me concentrate" to "start focus",
            "help me focus" to "start focus",
            "i need some quiet time" to "start focus",
            "im finished focusing" to "end focus",
            "im done focusing" to "end focus",
            "how does this work" to "help"
        )
        exact[q]?.let { return it }
        if (q.startsWith("use ") && appLabels.any { it.equals(q.removePrefix("use "), ignoreCase = true) }) return "open ${q.removePrefix("use ")}"
        Regex("""^(?:bring up|pull up|take me to|let me use) (.+)$""").matchEntire(q)?.let {
            // "Take me home" remains a navigation request, not launcher navigation.
            return "open ${it.groupValues[1]}"
        }
        Regex("""^(?:make a note that|save a note that|remember this|write this down) (.+)$""")
            .matchEntire(q)?.let { return "note ${it.groupValues[1]}" }
        Regex("""^(?:give me (.+) to (?:focus|concentrate)|(?:help me )?(?:focus|concentrate) for (.+))$""")
            .matchEntire(q)?.let { match ->
                val duration = match.groupValues.drop(1).first { it.isNotBlank() }
                minutes(duration)?.let { return "focus for $it minutes" }
            }
        return q
    }

    private fun minutes(text: String): Int? {
        if (text in setOf("half an hour", "half hour")) return 30
        if (text in setOf("an hour", "one hour")) return 60
        val amount = Regex("""^(.+) (?:minutes?|mins?)$""").matchEntire(text)?.groupValues?.get(1) ?: return null
        val words = mapOf("five" to 5, "ten" to 10, "fifteen" to 15, "twenty" to 20,
            "twenty five" to 25, "thirty" to 30, "forty" to 40, "forty five" to 45,
            "fifty" to 50, "sixty" to 60, "ninety" to 90)
        return (amount.toIntOrNull() ?: words[amount])?.takeIf { it in 5..120 }
    }
}

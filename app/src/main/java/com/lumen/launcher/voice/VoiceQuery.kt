package com.lumen.launcher.voice

object VoiceQuery {

    private val leading = listOf(
        "hey lumen ", "hi lumen ", "hello lumen ", "ok lumen ", "okay lumen ",
        "please ", "could you ", "can you ", "would you ", "will you ",
        "i want you to ", "i want to ", "i need to ", "i would like to ",
        "lumen "
    )

    fun clean(raw: String): String {
        var q = WakePhrase.strip(raw)
            .lowercase()
            .replace("'", "")
            .replace(Regex("[^a-z0-9%+*/(),.\\-\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', '!', '?', ',')
        var again = true
        while (again) {
            again = false
            for (prefix in leading) {
                if (q.startsWith(prefix)) {
                    q = q.removePrefix(prefix).trim()
                    again = true
                }
            }
        }
        q = q.removeSuffix(" please").removeSuffix(" for me").trim()
        return q
    }

    fun isOwnSpeech(text: String): Boolean {
        val n = text.lowercase().replace("'", "")
        return listOf(
            "didnt catch",
            "did not catch",
            "say an app",
            "try an app",
            "im listening",
            "give me a second",
            "couldnt hear",
            "could not hear",
            "hi im lumen",
            "hi i am lumen",
            "you can keep talking"
        ).any { n.contains(it) }
    }
}

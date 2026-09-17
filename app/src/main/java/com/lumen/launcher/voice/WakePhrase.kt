package com.lumen.launcher.voice

object WakePhrase {

    data class Hit(val remainder: String)

    private val greetings = setOf("hey", "hi", "hello", "ok", "okay", "yo")
    private val names = setOf(
        "lumen", "lumina", "lumin", "lumens",
        "loomin", "loumin", "lemon", "leman", "luman", "louman", "blumen"
    )

    fun detect(text: String): Hit? {
        val tokens = normalize(text).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        for (index in tokens.indices) {
            if (tokens[index] !in greetings) continue
            val name = tokens.getOrNull(index + 1) ?: continue
            if (!isName(name)) continue
            val rest = tokens.drop(index + 2).joinToString(" ")
            return Hit(rest)
        }
        return null
    }

    fun strip(text: String): String {
        val hit = detect(text) ?: return text.trim()
        return hit.remainder.ifBlank { text.trim() }
    }

    private fun isName(token: String): Boolean {
        if (token in names) return true
        if (token.length < 5) return false
        return names.any { name ->
            name.length >= 5 &&
                token.first() == name.first() &&
                kotlin.math.abs(name.length - token.length) <= 1 &&
                VoiceHearing.editDistance(token, name) <= 1
        }
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

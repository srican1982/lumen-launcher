package com.lumen.launcher.voice.intent

/** Strict local duration parsing. Unconsumed words or invalid ranges never create a timer. */
object VoiceDuration {
    fun seconds(raw: String): Int? {
        if (Regex("""-\s*\d""").containsMatchIn(raw)) return null
        val text = raw.lowercase().trim().replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .replace("half an hour", "30 minutes").replace("half a hour", "30 minutes")
            .replace("half a minute", "30 seconds")
        val terms = Regex("""(\d+|[a-z ]+?)\s+(seconds?|secs?|minutes?|mins?|hours?|hrs?)\b""").findAll(text).toList()
        if (terms.isEmpty()) return null
        var end = 0
        var total = 0L
        for (term in terms) {
            if (text.substring(end, term.range.first).trim().removePrefix("and").trim().isNotEmpty()) return null
            val words = term.groupValues[1].trim().removePrefix("and ")
            val fraction = words.endsWith(" and a half")
            val base = if (fraction) words.removeSuffix(" and a half") else words
            val amount = number(base) ?: return null
            val unit = term.groupValues[2]
            val scale = when { unit.startsWith("h") -> 3600; unit.startsWith("m") -> 60; else -> 1 }
            total += amount.toLong() * scale + if (fraction) scale / 2 else 0
            end = term.range.last + 1
        }
        if (text.substring(end).trim().isNotEmpty()) return null
        return total.takeIf { it in 1..86400 }?.toInt()
    }

    private fun number(text: String): Int? {
        text.toIntOrNull()?.let { return it.takeIf { n -> n >= 0 } }
        val small = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        if (text in setOf("a", "an")) return 1
        small.indexOf(text).takeIf { it >= 0 }?.let { return it }
        val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
        tens[text]?.let { return it }
        val parts = text.split(' ')
        if (parts.size == 2 && parts[0] in tens && small.indexOf(parts[1]) in 1..9) return tens.getValue(parts[0]) + small.indexOf(parts[1])
        return null
    }

    fun describe(seconds: Int): String = listOf(
        seconds / 3600 to "hour", seconds % 3600 / 60 to "minute", seconds % 60 to "second"
    ).filter { it.first > 0 }.joinToString(" ") { (n, unit) -> "$n $unit${if (n == 1) "" else "s"}" }
}

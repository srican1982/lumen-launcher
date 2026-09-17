package com.lumen.launcher.voice

import com.lumen.launcher.search.FuzzySearch

/**
 * Google will hear the wrong word. Tokens are compared to Lumen's
 * command vocabulary by sound and by spelling. The original line is
 * always kept, so a real weather "cold" still wins.
 */
object VoiceHearing {

    private val stop = setOf(
        "a", "an", "the", "my", "me", "to", "on", "of", "for", "can", "you",
        "please", "just", "i", "we", "it", "this", "that", "with", "from",
        "at", "in", "and", "or", "do", "is", "are", "be", "up", "all", "how",
        "what", "whats", "s", "like", "app"
    )

    private val lexicon = setOf(
        "open", "launch", "start", "run", "go", "show", "hide", "create", "make",
        "add", "put", "remove", "pin", "unpin", "dock", "folder", "called", "named",
        "labels", "label", "names", "icons", "icon", "columns", "column",
        "drawer", "settings", "home", "flow", "search", "recents", "recent",
        "private", "work", "personal", "focus", "space", "layout", "mode",
        "weather", "calendar", "alarm", "alarms", "reminder", "remind",
        "tasks", "list", "help", "goodbye", "news", "inbox", "switch",
        "bigger", "smaller", "turn", "off", "on", "size", "grid", "capacity",
        "yesterday", "meeting", "event", "temperature", "move", "above",
        "before", "under", "panel", "where", "remember", "locked", "means"
    )

    private val phoneticIndex: Map<String, List<String>> by lazy {
        lexicon.groupBy { phoneticKey(it) }.filterKeys { it.isNotBlank() }
    }

    fun expand(raw: String, appLabels: List<String> = emptyList()): List<String> {
        val cleaned = VoiceQuery.clean(raw)
        if (cleaned.isBlank()) return emptyList()
        val protected = protectedTokens(appLabels)
        val tokens = cleaned.split(' ').filter { it.isNotBlank() }
        val variants = linkedSetOf<String>()
        variants += repair(cleaned, appLabels)
        tokens.forEachIndexed { index, token ->
            guesses(token, tokens.getOrNull(index - 1), tokens.getOrNull(index + 1), protected)
                .forEach { guess ->
                    val copy = tokens.toMutableList()
                    copy[index] = guess
                    variants += copy.joinToString(" ")
                }
        }
        variants += cleaned
        return variants.filter { it.isNotBlank() }.distinct()
    }

    fun expandAll(candidates: List<String>, appLabels: List<String> = emptyList()): List<String> =
        candidates.flatMap { expand(it, appLabels) }.distinct()

    fun biasWords(): List<String> = lexicon.filter { it.length >= 4 }.distinct()

    internal fun repair(text: String, appLabels: List<String> = emptyList()): String {
        val q = text.lowercase().trim().replace(Regex("\\s+"), " ")
        if (q.isBlank()) return q
        val protected = protectedTokens(appLabels)
        val tokens = q.split(' ').filter { it.isNotBlank() }
        return tokens.mapIndexed { index, token ->
            guesses(
                token,
                tokens.getOrNull(index - 1),
                tokens.getOrNull(index + 1),
                protected
            ).firstOrNull() ?: token
        }.joinToString(" ")
    }

    internal fun guesses(
        token: String,
        prev: String?,
        next: String?,
        protected: Set<String>
    ): List<String> {
        if (token in lexicon || token in stop || token in protected) return emptyList()
        if (token.any { it.isDigit() }) return emptyList()

        val scored = linkedMapOf<String, Int>()
        fun consider(word: String, score: Int) {
            if (word == token || word !in lexicon) return
            val previous = scored[word]
            if (previous == null || score > previous) scored[word] = score
        }

        val key = phoneticKey(token)
        if (key.length >= 2 && token.length >= 3) {
            phoneticIndex[key].orEmpty().forEach { word ->
                if (kotlin.math.abs(word.length - token.length) <= 3) consider(word, 80)
            }
            if (key.length >= 3 && token.length >= 6) {
                val body = key.drop(1)
                lexicon.forEach { word ->
                    val other = phoneticKey(word)
                    if (word.length >= 6 && other.length >= 3 && other.drop(1) == body &&
                        kotlin.math.abs(word.length - token.length) <= 2
                    ) {
                        consider(word, 75)
                    }
                }
            }
        }

        val neighbor = prev in lexicon || next in lexicon || next == "app" || prev == "app"
        lexicon.forEach { word ->
            val distance = editDistance(token, word)
            when {
                token.length >= 5 && distance <= 1 -> consider(word, 70)
                neighbor && token.length >= 4 && distance <= 2 -> consider(word, 50)
            }
        }

        listOf("er", "s", "ing", "ed").forEach { suffix ->
            consider(token + suffix, 65)
        }

        val ranked = scored.entries.sortedByDescending { it.value }
        if (ranked.isEmpty() || ranked.first().value < 65) {
            return ranked.filter { it.value >= 50 }.take(2).map { it.key }
        }
        val top = ranked.first().value
        return ranked.filter { it.value == top }.take(2).map { it.key }
    }

    private fun protectedTokens(appLabels: List<String>): Set<String> {
        return appLabels.flatMap { label ->
            val n = FuzzySearch.normalize(label)
            n.split(' ') + n.replace(" ", "")
        }.filter { it.length >= 2 }.toSet()
    }

    internal fun phonetic(word: String): String = phoneticKey(word)

    private fun phoneticKey(word: String): String {
        val src = word.lowercase().filter { it.isLetter() }
        if (src.isEmpty()) return ""
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            val n = src.getOrNull(i + 1)
            when {
                c in "aeiouy" -> {
                    if (out.isEmpty()) out.append('A')
                    i++
                }
                c == 'c' && n == 'h' -> {
                    push(out, 'X'); i += 2
                }
                c == 'c' && n == 'k' -> {
                    push(out, 'K'); i += 2
                }
                c == 'c' && n != null && n in "ei" -> {
                    push(out, 'S'); i++
                }
                c in "cqk" -> {
                    push(out, 'K'); i++
                    while (i < src.length && src[i] in "cqk") i++
                }
                c == 'p' && n == 'h' -> {
                    push(out, 'F'); i += 2
                }
                c == 'g' && n == 'h' -> i += 2
                c == 'd' -> {
                    push(out, 'D'); i++
                    while (i < src.length && src[i] == 'd') i++
                }
                c == 't' -> {
                    push(out, 'T'); i++
                    while (i < src.length && src[i] == 't') i++
                }
                c in "sz" -> {
                    push(out, 'S'); i++
                    while (i < src.length && src[i] in "sz") i++
                }
                c in "fv" -> {
                    push(out, 'F'); i++
                }
                c in "bp" -> {
                    push(out, 'P'); i++
                }
                c in "gj" -> {
                    push(out, 'J'); i++
                }
                c == 'x' -> {
                    push(out, 'K'); push(out, 'S'); i++
                }
                c == 'h' -> i++
                c == 'w' -> {
                    push(out, 'W'); i++
                }
                c == 'r' -> {
                    push(out, 'R'); i++
                }
                c == 'l' -> {
                    push(out, 'L'); i++
                    while (i < src.length && src[i] == 'l') i++
                }
                c == 'm' -> {
                    push(out, 'M'); i++
                }
                c == 'n' -> {
                    push(out, 'N'); i++
                }
                else -> i++
            }
        }
        return out.toString().trimEnd('S')
    }

    private fun push(out: StringBuilder, ch: Char) {
        if (out.isEmpty() || out.last() != ch) out.append(ch)
    }

    internal fun editDistance(left: String, right: String): Int {
        if (left == right) return 0
        val dp = Array(left.length + 1) { IntArray(right.length + 1) }
        for (i in 0..left.length) dp[i][0] = i
        for (j in 0..right.length) dp[0][j] = j
        for (i in 1..left.length) {
            for (j in 1..right.length) {
                dp[i][j] = if (left[i - 1] == right[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[left.length][right.length]
    }
}

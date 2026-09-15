package com.lumen.launcher.search

/**
 * Fast, typo-tolerant app search.
 *
 * Prefix and substring matches win. Short queries like `wh` still rank
 * WhatsApp highly, and `watsap` still finds WhatsApp.
 */
object FuzzySearch {

    fun score(query: String, target: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val t = normalize(target)
        if (t.isEmpty()) return -1
        if (t == q) return 1_000

        val words = t.split(' ')
        if (words.any { it == q }) return 980
        if (t.startsWith(q)) return 920 - (t.length - q.length).coerceAtMost(40)
        if (words.any { it.startsWith(q) }) return 860
        if (t.contains(q)) return 740 - t.indexOf(q).coerceAtMost(80)

        val initials = words.mapNotNull { it.firstOrNull() }.joinToString("")
        if (initials.startsWith(q) && q.length >= 2) return 700

        if (isSubsequence(q, t)) {
            val span = subsequenceSpan(q, t)
            val tightness = if (span <= q.length + 2) 80 else 0
            return 520 + tightness - (span - q.length).coerceAtMost(40)
        }

        if (q.length >= 3) {
            val window = t.take(q.length + 3)
            val dist = levenshtein(q, window, maxDistance = 2)
            if (dist in 0..2) return 430 - dist * 60

            words.forEach { word ->
                if (word.length >= 4) {
                    val wordDist = levenshtein(q, word.take(q.length + 3), maxDistance = 2)
                    if (wordDist in 0..2) return 400 - wordDist * 50
                }
            }
        }
        return -1
    }

    fun normalize(value: String): String {
        val builder = StringBuilder(value.length)
        for (char in value.lowercase()) {
            when {
                char.isLetterOrDigit() -> builder.append(char)
                char.isWhitespace() && builder.isNotEmpty() && builder.last() != ' ' -> builder.append(' ')
            }
        }
        return builder.trimEnd().toString()
    }

    internal fun isSubsequence(query: String, target: String): Boolean {
        var i = 0
        for (char in target) {
            if (char == query[i]) {
                i++
                if (i == query.length) return true
            }
        }
        return false
    }

    private fun subsequenceSpan(query: String, target: String): Int {
        var i = 0
        var start = -1
        for (index in target.indices) {
            if (target[index] == query[i]) {
                if (start < 0) start = index
                i++
                if (i == query.length) return index - start + 1
            }
        }
        return target.length
    }

    internal fun levenshtein(left: String, right: String, maxDistance: Int = 2): Int {
        if (left == right) return 0
        if (kotlin.math.abs(left.length - right.length) > maxDistance) return maxDistance + 1
        val prev = IntArray(right.length + 1) { it }
        val curr = IntArray(right.length + 1)
        for (i in 1..left.length) {
            curr[0] = i
            var rowMin = curr[0]
            val leftChar = left[i - 1]
            for (j in 1..right.length) {
                val cost = if (leftChar == right[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > maxDistance) return maxDistance + 1
            for (j in prev.indices) prev[j] = curr[j]
        }
        return prev[right.length]
    }
}

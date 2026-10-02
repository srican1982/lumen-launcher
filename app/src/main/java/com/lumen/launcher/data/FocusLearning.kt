package com.lumen.launcher.data

/** Small, local task/app history. No contacts, messages or document contents are stored. */
object FocusLearning {
    private val stop = setOf("the", "and", "for", "with", "this", "that", "from", "today", "tomorrow", "please", "task", "test", "prepare", "create")
    fun tokens(text: String): Set<String> = Regex("""[\p{L}\p{N}]+""")
        .findAll(text.lowercase()).map { it.value }.filter { it.length > 2 && it !in stop }.toSet()
    fun related(task: String, text: String): Boolean = tokens(task).intersect(tokens(text)).isNotEmpty()
    fun rank(task: String, history: Map<String, Map<String, Int>>, allowed: Set<String>): List<String> {
        val words = tokens(task)
        if (words.isEmpty()) return emptyList()
        val scores = mutableMapOf<String, Double>()
        history.forEach { (past, apps) ->
            val other = tokens(past)
            val overlap = words.intersect(other).size
            if (overlap > 0) {
                val similarity = overlap.toDouble() / words.union(other).size
                if (similarity >= .4) apps.forEach { (app, count) ->
                    if (app in allowed) scores[app] = (scores[app] ?: 0.0) + similarity * count.coerceAtMost(10)
                }
            }
        }
        return scores.entries.sortedByDescending { it.value }.map { it.key }.take(4)
    }
}

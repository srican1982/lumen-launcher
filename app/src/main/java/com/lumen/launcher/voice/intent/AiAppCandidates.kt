package com.lumen.launcher.voice.intent

import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.VoiceMatch

object AiAppCandidates {
    private val filler = setOf("can", "could", "would", "you", "please", "open", "launch", "start", "run", "the", "app", "lumen", "hey")
    fun rank(spoken: String, labels: List<String>, limit: Int = 80): List<String> {
        val normalized = FuzzySearch.normalize(spoken)
        val words = normalized.split(' ').filter { it.isNotBlank() && it !in filler }
        val queries = mutableSetOf(normalized, words.joinToString(" "))
        for (size in 1..minOf(6, words.size)) words.windowed(size).forEach { queries += it.joinToString(" ") }
        return labels.distinct().sortedByDescending { label ->
            queries.maxOf { maxOf(FuzzySearch.score(it, label), VoiceMatch.score(it, label)) }
        }.take(limit)
    }
}

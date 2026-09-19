package com.lumen.launcher.search

object AliasHints {

    fun spokenKey(spoken: String): String = FuzzySearch.normalize(spoken).trim()

    fun shouldTrack(spoken: String, label: String): Boolean {
        val query = spokenKey(spoken)
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .removePrefix("run ")
            .removePrefix("go to ")
            .trim()
        val name = FuzzySearch.normalize(label)
        if (query.length !in 2..40) return false
        if (query.split(' ').size > 6) return false
        if (query == name) return false
        if (name.startsWith(query) && query.length >= 3) return false
        return true
    }

    fun shouldPromote(count: Int): Boolean = count >= 2

    fun aliasKey(spoken: String): String {
        return spokenKey(spoken)
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .removePrefix("run ")
            .removePrefix("go to ")
            .trim()
    }
}

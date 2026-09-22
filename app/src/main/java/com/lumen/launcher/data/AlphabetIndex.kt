package com.lumen.launcher.data

import com.lumen.launcher.search.Routine

/**
 * Cached A–Z map for the app drawer rail.
 * Built once per section snapshot — drag only looks up letters.
 */
data class AlphabetIndex(
    val letters: List<Char>,
    val available: Set<Char>,
    val firstIndex: Map<Char, Int>,
    val previews: Map<Char, List<AppInfo>>,
    val spaceBoosted: Set<Char>
) {
    fun letterAt(fraction: Float): Char? {
        if (letters.isEmpty()) return null
        val clamped = fraction.coerceIn(0f, 0.999f)
        return letters[(clamped * letters.size).toInt()]
    }

    fun resolve(letter: Char): Char? {
        if (letter in available) return letter
        if (available.isEmpty()) return null
        val ordered = LETTERS.filter { it in available }
        if (ordered.isEmpty()) return null
        val prefer = if (letter == '#') ordered.first() else letter
        return ordered.minBy { distance(prefer, it) }
    }

    fun firstVisibleIndex(letter: Char): Int? {
        val resolved = resolve(letter) ?: return null
        return firstIndex[resolved]
    }

    companion object {
        /** Mockup order: # then A–Z. */
        val LETTERS: List<Char> = listOf('#') + ('A'..'Z').toList()

        fun letterKey(label: String): String {
            val c = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
            return if (c in 'A'..'Z') c.toString() else "#"
        }

        fun build(
            sections: List<Pair<String, List<AppInfo>>>,
            leadingItems: Int = 0,
            previewCount: Int = 3
        ): AlphabetIndex {
            val available = linkedSetOf<Char>()
            val firstIndex = linkedMapOf<Char, Int>()
            val previews = linkedMapOf<Char, List<AppInfo>>()
            var index = leadingItems.coerceAtLeast(0)
            sections.forEach { (title, apps) ->
                if (apps.isEmpty()) return@forEach
                val key = title.firstOrNull()?.uppercaseChar() ?: '#'
                val letter = if (key in 'A'..'Z' || key == '#') key else '#'
                available += letter
                firstIndex.putIfAbsent(letter, index)
                previews.putIfAbsent(letter, apps.take(previewCount))
                index += 1 + apps.size
            }
            return AlphabetIndex(
                letters = LETTERS,
                available = available,
                firstIndex = firstIndex,
                previews = previews,
                spaceBoosted = emptySet()
            )
        }

        fun buildAz(
            apps: List<AppInfo>,
            space: SpaceKind,
            recents: List<String>,
            leadingItems: Int = 0,
            previewCount: Int = 3
        ): Pair<List<Pair<String, List<AppInfo>>>, AlphabetIndex> {
            val preferred = Routine.preferredCategories(space)
            val grouped = apps.groupBy { letterKey(it.label) }
            val order = listOf("#") + ('A'..'Z').map { it.toString() }
            val boostedLetters = mutableSetOf<Char>()
            val sections = order.mapNotNull { key ->
                val bucket = grouped[key].orEmpty()
                if (bucket.isEmpty()) return@mapNotNull null
                val ranked = rankForSpace(bucket, space, preferred, recents)
                if (ranked.firstOrNull()?.let { spaceWeight(it, space, preferred, recents) } ?: 0 > 0) {
                    boostedLetters += key.first()
                }
                key to ranked
            }
            val index = build(sections, leadingItems, previewCount)
                .copy(spaceBoosted = boostedLetters)
            return sections to index
        }

        fun rankForSpace(
            apps: List<AppInfo>,
            space: SpaceKind,
            preferred: Set<AppCategory> = Routine.preferredCategories(space),
            recents: List<String> = emptyList()
        ): List<AppInfo> {
            return apps.sortedWith(
                compareByDescending<AppInfo> { spaceWeight(it, space, preferred, recents) }
                    .thenBy { it.label.lowercase() }
            )
        }

        fun spaceWeight(
            app: AppInfo,
            space: SpaceKind,
            preferred: Set<AppCategory>,
            recents: List<String>
        ): Int {
            var score = 0
            if (app.category in preferred) score += 5
            if (space == SpaceKind.Work && app.category == AppCategory.Work) score += 3
            if (space == SpaceKind.Travel && app.category == AppCategory.Travel) score += 3
            if (space == SpaceKind.Focus && app.category == AppCategory.Utilities) score += 2
            val recentRank = recents.indexOf(app.key)
            if (recentRank in 0..7) score += 3 - (recentRank / 3)
            return score
        }

        private fun distance(from: Char, to: Char): Int {
            val a = slot(from)
            val b = slot(to)
            return kotlin.math.abs(a - b)
        }

        private fun slot(letter: Char): Int = when (letter) {
            '#' -> 0
            in 'A'..'Z' -> 1 + (letter - 'A')
            else -> 0
        }
    }
}

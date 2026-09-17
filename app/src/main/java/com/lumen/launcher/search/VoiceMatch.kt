package com.lumen.launcher.search

/**
 * Match spoken app names against whatever is installed right now.
 *
 * Do not keep per-app aliases. When a package is added, its launcher label
 * is enough: "ChatGPT" also hears "chat gpt" and "chat g p t", "PhotoLab"
 * hears "photo lab", "FXNow" hears "fx now".
 *
 * Voice must not guess nearby names. Drawer search can be fuzzy;
 * "work" must not open Word.
 */
object VoiceMatch {

    fun best(query: String, labels: List<String>, minScore: Int = 860): String? {
        if (query.isBlank() || labels.isEmpty()) return null
        val scored = labels
            .distinct()
            .map { it to score(query, it) }
            .filter { it.second >= minScore }
        val top = scored.maxByOrNull { it.second } ?: return null
        val rival = scored.firstOrNull { label ->
            label.first != top.first && top.second - label.second < 40
        }
        if (rival != null && top.second < 980) return null
        return top.first
    }

    fun score(query: String, label: String): Int {
        val q = FuzzySearch.normalize(query)
        val t = FuzzySearch.normalize(label)
        if (q.isEmpty() || t.isEmpty()) return -1
        val compactQuery = q.replace(" ", "")
        val forms = spokenForms(label)
        val compactForms = forms.map { it.replace(" ", "") }.toSet()

        if (q in forms) return 1_000
        if (compactQuery in compactForms) return 980
        if (matchesSpokenInitials(q, t)) return 960

        val originalWords = t.split(' ').filter { it.length >= 4 }
        if (q in originalWords || compactQuery in originalWords) return 980

        val tokens = forms
            .flatMap { it.split(' ') }
            .filter { it.isNotBlank() }
            .toSet()
        if (compactQuery.length >= 5 && compactQuery in tokens) return 940

        val compactTarget = t.replace(" ", "")
        val distanceCap = when {
            compactQuery.length >= 9 -> 2
            compactQuery.length >= 6 -> 1
            else -> 0
        }
        if (distanceCap > 0) {
            val distance = compactForms.minOf { form ->
                FuzzySearch.levenshtein(compactQuery, form, maxDistance = distanceCap)
            }
            if (distance <= 1 && compactQuery.length >= 6) return 900
            if (distance <= 2 && compactQuery.length >= 9) return 860
        }

        if (q.length >= 5 && t.startsWith(q)) return 920
        if (q.length >= 6 && compactTarget.startsWith(compactQuery)) return 880
        return -1
    }

    internal fun spokenForms(label: String): Set<String> {
        val normalized = FuzzySearch.normalize(label)
        val compact = normalized.replace(" ", "")
        val parts = splitName(label)
        val forms = mutableSetOf(normalized, compact)
        if (parts.size >= 2) {
            val spaced = parts.joinToString(" ") { FuzzySearch.normalize(it) }.trim()
            forms += spaced
            forms += spaced.replace(" ", "")
            val spelled = parts.joinToString(" ") { part ->
                if (isAcronym(part)) part.filter { it.isLetterOrDigit() }.lowercase().toCharArray().joinToString(" ")
                else FuzzySearch.normalize(part)
            }.trim()
            forms += FuzzySearch.normalize(spelled)
        }
        if (compact.length in 2..5 && compact.all { it.isLetter() }) {
            forms += compact.toCharArray().joinToString(" ")
        }
        return forms.filter { it.isNotBlank() }.toSet()
    }

    private val initialSkip = setOf("a", "an", "the", "and")

    /**
     * People say the full name; the icon may only show the initials.
     * "bank of america" matches BOA. This is not a per-app alias.
     */
    internal fun matchesSpokenInitials(query: String, label: String): Boolean {
        val compactLabel = FuzzySearch.normalize(label).replace(" ", "")
        if (compactLabel.length !in 2..5 || !compactLabel.all { it.isLetter() }) return false
        val words = FuzzySearch.normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.size < 2) return false
        val all = words.mapNotNull { it.firstOrNull() }.joinToString("")
        val content = words.filter { it !in initialSkip }.mapNotNull { it.firstOrNull() }.joinToString("")
        val hits = setOf(all, content).filter { it.length in 2..5 }
        if (compactLabel !in hits) return false
        if (compactLabel.length >= 3) return true
        return content.length == 2
    }

    private fun isAcronym(part: String): Boolean {
        val letters = part.filter { it.isLetter() }
        return letters.length in 2..5 && letters.all { it.isUpperCase() }
    }

    private fun splitName(label: String): List<String> {
        val spaced = label
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .replace(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), " ")
            .replace(Regex("(?<=[A-Za-z])(?=[0-9])"), " ")
            .replace(Regex("(?<=[0-9])(?=[A-Za-z])"), " ")
            .replace(Regex("[._\\-/+]"), " ")
        return spaced.split(Regex("\\s+")).filter { it.isNotBlank() }
    }
}

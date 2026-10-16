package com.lumen.launcher.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.lumen.launcher.search.FuzzySearch

data class ContactMatch(
    val name: String,
    val phone: String,
    val score: Int
)

class ContactLookup(private val context: Context) {

    @Volatile
    private var cache: List<Pair<String, String>> = emptyList()

    @Volatile
    private var loadedAt = 0L

    fun matches(spoken: String, limit: Int = 4): List<ContactMatch> {
        val name = spoken.trim()
        if (name.length < 2) return emptyList()
        refresh()
        return cache
            .map { (display, phone) -> ContactMatch(display, phone, FuzzySearch.score(name, display)) }
            .filter { it.score >= 680 }
            .sortedWith(compareByDescending<ContactMatch> { it.score }.thenBy { it.name.lowercase() })
            .distinctBy { it.phone.filter(Char::isDigit) }
            .take(limit)
    }

    fun bestPrefix(rest: String): Pair<ContactMatch, String>? {
        val tokens = rest.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        var best: Pair<ContactMatch, Int>? = null
        for (n in tokens.size downTo 1) {
            val candidate = tokens.take(n).joinToString(" ")
            val hit = matches(candidate, 1).firstOrNull() ?: continue
            if (best == null || hit.score > best.first.score || (hit.score == best.first.score && n > best.second)) {
                best = hit to n
            }
        }
        val chosen = best ?: return null
        val leftover = tokens.drop(chosen.second).joinToString(" ")
        return chosen.first to leftover
    }

    fun names(limit: Int = 40): List<String> {
        refresh()
        return cache.map { it.first }.distinct().take(limit)
    }

    /** Named contacts for search empty-state People row (skip number-as-name entries). */
    fun top(limit: Int = 5): List<ContactMatch> {
        refresh()
        return cache
            .asSequence()
            .filterNot { looksLikePhoneLabel(it.first) }
            .distinctBy { it.first.lowercase() }
            .take(limit)
            .map { (name, phone) -> ContactMatch(name, phone, 1000) }
            .toList()
    }

    /** True when DISPLAY_NAME is basically a phone number (common for unnamed contacts). */
    private fun looksLikePhoneLabel(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true
        val digits = trimmed.count { it.isDigit() }
        val letters = trimmed.count { it.isLetter() }
        if (letters == 0 && digits >= 3) return true
        if (digits >= 7 && letters <= 2) return true
        if (trimmed.startsWith("+") && digits >= 6 && letters <= 1) return true
        return false
    }

    fun hasAccess(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun invalidate() {
        loadedAt = 0L
        cache = emptyList()
    }

    private fun refresh() {
        if (!hasAccess()) {
            cache = emptyList()
            return
        }
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - loadedAt < 30_000) return
        val found = mutableListOf<Pair<String, String>>()
        runCatching {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(
                    Phone.DISPLAY_NAME,
                    Phone.NUMBER,
                    Phone.NORMALIZED_NUMBER,
                    Phone.IS_SUPER_PRIMARY,
                    Phone.STARRED
                ),
                null,
                null,
                "${Phone.STARRED} DESC, ${Phone.DISPLAY_NAME} COLLATE NOCASE"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(Phone.NUMBER)
                val normalizedIdx = cursor.getColumnIndex(Phone.NORMALIZED_NUMBER)
                val primaryIdx = cursor.getColumnIndex(Phone.IS_SUPER_PRIMARY)
                val starredIdx = cursor.getColumnIndex(Phone.STARRED)
                data class Row(val name: String, val phone: String, val starred: Boolean, val primary: Boolean)
                val rows = mutableListOf<Row>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    if (name.isBlank()) continue
                    val normalized = if (normalizedIdx >= 0) cursor.getString(normalizedIdx) else null
                    val raw = if (numberIdx >= 0) cursor.getString(numberIdx) else null
                    val phone = (normalized ?: raw)?.trim().orEmpty()
                    if (phone.isBlank()) continue
                    val primary = primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1
                    val starred = starredIdx >= 0 && cursor.getInt(starredIdx) == 1
                    rows += Row(name, phone, starred, primary)
                }
                // Starred / primary first; keep all rows for voice matching.
                rows.sortedWith(
                    compareByDescending<Row> { it.starred }
                        .thenByDescending { it.primary }
                        .thenBy { it.name.lowercase() }
                ).forEach { rowsItem ->
                    found += rowsItem.name to rowsItem.phone
                }
            }
        }
        cache = found.distinctBy { (name, phone) -> name.lowercase() to phone.filter(Char::isDigit) }
        loadedAt = now
    }
}

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
                arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.NORMALIZED_NUMBER, Phone.IS_SUPER_PRIMARY),
                null,
                null,
                "${Phone.DISPLAY_NAME} COLLATE NOCASE"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(Phone.NUMBER)
                val normalizedIdx = cursor.getColumnIndex(Phone.NORMALIZED_NUMBER)
                val primaryIdx = cursor.getColumnIndex(Phone.IS_SUPER_PRIMARY)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    if (name.isBlank()) continue
                    val normalized = if (normalizedIdx >= 0) cursor.getString(normalizedIdx) else null
                    val raw = if (numberIdx >= 0) cursor.getString(numberIdx) else null
                    val phone = (normalized ?: raw)?.trim().orEmpty()
                    if (phone.isBlank()) continue
                    val primary = primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1
                    if (primary) {
                        found.add(0, name to phone)
                    } else {
                        found += name to phone
                    }
                }
            }
        }
        cache = found.distinctBy { (name, phone) -> name.lowercase() to phone.filter(Char::isDigit) }
        loadedAt = now
    }
}

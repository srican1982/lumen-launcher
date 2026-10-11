package com.lumen.launcher.widgets

object WidgetContentRules {
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val chosen = if (words.size > 1) listOf(words.first(), words.last()) else words.take(1)
        return chosen.joinToString("") { word -> String(Character.toChars(word.codePointAt(0))) }.uppercase()
            .ifBlank { "?" }
    }
    fun columns(value: Int) = value.coerceIn(1, 2)
    fun height(value: Int, kind: WidgetKind) = value.coerceIn(if (kind in setOf(WidgetKind.Quote, WidgetKind.Photos, WidgetKind.Scribble)) 96 else 132, 300)
}

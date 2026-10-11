package com.lumen.launcher.widgets

object WidgetContentRules {
    private val heightSteps = listOf(96, 132, 180, 240, 300)

    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val chosen = if (words.size > 1) listOf(words.first(), words.last()) else words.take(1)
        return chosen.joinToString("") { word -> String(Character.toChars(word.codePointAt(0))) }.uppercase()
            .ifBlank { "?" }
    }
    fun columns(value: Int) = value.coerceIn(1, 2)
    fun height(value: Int, kind: WidgetKind) = value.coerceIn(minHeight(kind), 300)
    fun minHeight(kind: WidgetKind) =
        if (kind in setOf(WidgetKind.Quote, WidgetKind.Photos, WidgetKind.Scribble)) 96 else 132

    /** Nearest allowed height step for gesture resize. */
    fun snapHeight(value: Int, kind: WidgetKind): Int {
        val allowed = heightSteps.filter { it >= minHeight(kind) }
        return allowed.minByOrNull { kotlin.math.abs(it - value) } ?: height(value, kind)
    }
}

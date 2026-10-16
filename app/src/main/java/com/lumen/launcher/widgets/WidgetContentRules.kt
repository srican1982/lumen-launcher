package com.lumen.launcher.widgets

import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews

object WidgetContentRules {
    private val heightSteps = listOf(96, 132, 180, 200, 220, 230, 240, 280, 300)

    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val chosen = if (words.size > 1) listOf(words.first(), words.last()) else words.take(1)
        return chosen.joinToString("") { word -> String(Character.toChars(word.codePointAt(0))) }.uppercase()
            .ifBlank { "?" }
    }
    fun columns(value: Int) = value.coerceIn(1, 2)
    fun height(value: Int, kind: WidgetKind) = value.coerceIn(minHeight(kind), 300)
    fun minHeight(kind: WidgetKind) = when {
        kind.isContact || kind.isTask || kind.isTrip || kind.isClock || kind.isMedia -> kind.heightDp
        kind in setOf(WidgetKind.Quote, WidgetKind.Photos, WidgetKind.Scribble) -> 96
        else -> 132
    }

    /** Nearest allowed height step for gesture resize. */
    fun snapHeight(value: Int, kind: WidgetKind): Int {
        val allowed = heightSteps.filter { it >= minHeight(kind) }
        return allowed.minWithOrNull(compareBy<Int> { kotlin.math.abs(it - value) }.thenByDescending { it }) ?: height(value, kind)
    }

    /**
     * Continuous scale factor from the widget's live size vs its designed default.
     * Circles, titles and avatars grow/shrink with the card.
     */
    fun scale(widthDp: Int, heightDp: Int, kind: WidgetKind): SizeScale {
        val refW = (if (kind.wide) 300 else 150).toFloat()
        val refH = kind.heightDp.coerceAtLeast(96).toFloat()
        val factor = ((widthDp.coerceAtLeast(80) / refW) * 0.4f +
            (heightDp.coerceAtLeast(minHeight(kind)) / refH) * 0.6f)
            .coerceIn(0.7f, 1.5f)
        return SizeScale(factor)
    }

    data class SizeScale(val factor: Float) {
        fun sp(base: Float): Float = (base * factor).coerceIn(9f, 34f)
        fun dp(base: Float): Float = (base * factor).coerceIn(base * 0.65f, base * 1.55f)
    }

    fun setDipSize(views: RemoteViews, id: Int, widthDp: Float, heightDp: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        views.setViewLayoutWidth(id, widthDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(id, heightDp, TypedValue.COMPLEX_UNIT_DIP)
    }
}

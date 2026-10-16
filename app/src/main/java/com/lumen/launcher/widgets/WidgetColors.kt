package com.lumen.launcher.widgets

/** Shared palette so gold accents stay readable on light and dark glass. */
object WidgetColors {
    /** Soft gold on dark glass. */
    const val AccentDark = 0xFFE4CB91.toInt()

    /** Deeper bronze on white / light glass — pale gold washes out there. */
    const val AccentLight = 0xFF7A5C1E.toInt()

    fun accent(light: Boolean): Int = if (light) AccentLight else AccentDark
}

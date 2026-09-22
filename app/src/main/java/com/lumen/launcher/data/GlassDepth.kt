package com.lumen.launcher.data

/**
 * How strong the frosted bubbles are. Balanced matches the Tasks page frost
 * that stays readable on both light and dark wallpapers.
 */
enum class GlassDepth(val title: String, val frostAlpha: Float) {
    Soft("Soft", 0.16f),
    Balanced("Balanced", 0.239f),
    Solid("Solid", 0.40f);

    companion object {
        fun parse(raw: String?): GlassDepth =
            entries.firstOrNull { it.name.equals(raw, true) } ?: Balanced

        fun next(current: GlassDepth): GlassDepth {
            val all = entries
            return all[(all.indexOf(current) + 1) % all.size]
        }
    }
}

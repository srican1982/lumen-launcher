package com.lumen.launcher.data

/** Whole-app look. Switch in Settings → Theme. */
enum class LumenThemeMode(val title: String) {
    /** The original lavender / violet Lumen look. */
    Violet("Lumen Violet"),
    /** Neutral white frosted glass; every app icon becomes a white symbol on glass. */
    WhiteGlass("White Glass"),
    /** Same white glass, but app logos keep their colors on the frosted tiles. */
    WhiteGlassColor("White Glass · Color icons");

    /** Both White Glass versions share the white glass look; only the icons differ. */
    val isWhiteGlass: Boolean get() = this == WhiteGlass || this == WhiteGlassColor

    companion object {
        fun parse(raw: String?): LumenThemeMode =
            entries.firstOrNull { it.name.equals(raw, true) } ?: Violet

        fun next(current: LumenThemeMode): LumenThemeMode =
            entries[(entries.indexOf(current) + 1) % entries.size]
    }
}

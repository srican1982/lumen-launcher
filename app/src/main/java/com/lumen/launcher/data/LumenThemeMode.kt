package com.lumen.launcher.data

/** Whole-app look. Switch in Settings → Theme. */
enum class LumenThemeMode(val title: String) {
    /** The original lavender / violet Lumen look. */
    Violet("Lumen Violet"),
    /** Neutral white frosted glass; every app icon becomes a white symbol on glass. */
    WhiteGlass("White Glass");

    companion object {
        fun parse(raw: String?): LumenThemeMode =
            entries.firstOrNull { it.name.equals(raw, true) } ?: Violet

        fun next(current: LumenThemeMode): LumenThemeMode =
            entries[(entries.indexOf(current) + 1) % entries.size]
    }
}

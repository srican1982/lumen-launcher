package com.lumen.launcher.data

enum class IconSkin(val title: String) {
    Original("Original icons"),
    Glass("Lumen Glass"),
    Mono("Lumen Mono"),
    WhiteGlass("White glass"),
    MatchSpace("Match current Space");

    companion object {
        fun parse(raw: String?): IconSkin =
            entries.firstOrNull { it.name.equals(raw, true) } ?: MatchSpace

        fun next(current: IconSkin): IconSkin {
            val all = entries
            return all[(all.indexOf(current) + 1) % all.size]
        }

        fun treatment(skin: IconSkin, space: SpaceKind, focusing: Boolean): IconTreatment {
            return when (skin) {
                Original -> IconTreatment.Original
                Glass -> IconTreatment.Glass
                Mono -> IconTreatment.Mono
                WhiteGlass -> IconTreatment.WhiteGlass
                MatchSpace -> when {
                    focusing || space == SpaceKind.Focus -> IconTreatment.Mono
                    space == SpaceKind.Work -> IconTreatment.Work
                    space == SpaceKind.Travel -> IconTreatment.Contrast
                    space == SpaceKind.Private -> IconTreatment.Original
                    else -> IconTreatment.Glass
                }
            }
        }
    }
}

enum class IconTreatment {
    Original, Glass, Work, Mono, Contrast,
    /** White symbol on a frosted-glass tile (colorful logos stay colored). */
    WhiteGlass
}

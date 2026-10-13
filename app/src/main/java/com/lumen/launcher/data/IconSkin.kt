package com.lumen.launcher.data

enum class IconSkin(val title: String) {
    Original("Original icons"),
    Glass("Lumen Glass"),
    Mono("Lumen Mono"),
    WhiteGlass("White glass"),
    MatchSpace("Match current Space"),
    Tinted("Tinted (iOS 18 style)"),
    Crystal("Crystal Glass"),
    Ios("iOS Style");

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
                Tinted -> IconTreatment.Tinted
                Crystal -> IconTreatment.Crystal
                Ios -> IconTreatment.Ios
                MatchSpace -> when {
                    // Focus keeps colorful original icons in the default Lumen theme.
                    focusing || space == SpaceKind.Focus -> IconTreatment.Original
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
    /** White symbol on a frosted-glass tile. */
    WhiteGlass,
    /** The app's colored logo on a frosted-glass tile. */
    GlassColor,
    /** A gold symbol on a dark tile, inspired by tinted Home Screen icons. */
    Tinted,
    /** Full-color logos raised above a transparent glass tile. */
    Crystal,
    /** Original rounded-square icon pack with colorful gradients and familiar app glyphs. */
    Ios
}

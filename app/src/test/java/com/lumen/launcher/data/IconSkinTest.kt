package com.lumen.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IconSkinTest {
    @Test fun iosStyleIsSelectableAndPersistsAcrossModes() {
        assertEquals(IconSkin.Ios, IconSkin.parse("Ios"))
        assertEquals(IconSkin.Ios, IconSkin.next(IconSkin.Crystal))
        for (space in SpaceKind.entries) assertEquals(IconTreatment.Ios, IconSkin.treatment(IconSkin.Ios, space, true))
    }
    @Test fun existingPreferencesKeepTheirStyles() {
        assertEquals(IconSkin.MatchSpace, IconSkin.parse(null))
        assertEquals(IconSkin.Original, IconSkin.parse("Original"))
        assertEquals(IconTreatment.Glass, IconSkin.treatment(IconSkin.MatchSpace, SpaceKind.Home, false))
        assertEquals(IconTreatment.Original, IconSkin.treatment(IconSkin.Original, SpaceKind.Home, false))
    }

    @Test fun tintedChoicePersistsAcrossSpaces() {
        assertEquals(IconSkin.Tinted, IconSkin.parse("Tinted"))
        for (space in SpaceKind.entries) {
            assertEquals(IconTreatment.Tinted, IconSkin.treatment(IconSkin.Tinted, space, false))
        }
    }

    @Test fun crystalIsSeparateFromTintedAndExistingStyles() {
        assertEquals(IconSkin.Crystal, IconSkin.parse("Crystal"))
        assertEquals(IconSkin.Crystal, IconSkin.next(IconSkin.Tinted))
        assertEquals(IconTreatment.Tinted, IconSkin.treatment(IconSkin.Tinted, SpaceKind.Home, false))
        for (space in SpaceKind.entries) {
            assertEquals(IconTreatment.Crystal, IconSkin.treatment(IconSkin.Crystal, space, false))
        }
    }
}

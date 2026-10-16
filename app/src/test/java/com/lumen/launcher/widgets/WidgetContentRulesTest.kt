package com.lumen.launcher.widgets

import org.junit.Assert.*
import org.junit.Test

class WidgetContentRulesTest {
    @Test fun initialsUseFirstAndLastNamesAndHandleUnicode() {
        assertEquals("AS", WidgetContentRules.initials("  Alex   Jane Smith "))
        assertEquals("É", WidgetContentRules.initials("Élodie"))
        assertEquals("李王", WidgetContentRules.initials("李 王"))
        assertEquals("?", WidgetContentRules.initials("  "))
    }
    @Test fun resizeLimitsKeepControlsVisibleAndAllowSmallContentWidgets() {
        WidgetKind.entries.forEach { kind ->
            val minimum = WidgetContentRules.minHeight(kind)
            assertEquals(minimum, WidgetContentRules.height(1, kind))
            assertEquals(240.coerceAtLeast(minimum), WidgetContentRules.height(240, kind))
            assertEquals(300, WidgetContentRules.height(999, kind))
        }
        assertEquals(1, WidgetContentRules.columns(0))
        assertEquals(2, WidgetContentRules.columns(3))
        assertEquals(220, WidgetContentRules.minHeight(WidgetKind.Contacts))
        assertEquals(180, WidgetContentRules.minHeight(WidgetKind.ContactsSingle))
        assertEquals(180, WidgetContentRules.minHeight(WidgetKind.ContactsRow))
    }

    @Test fun snapHeightPicksNearestAllowedStep() {
        assertEquals(240, WidgetContentRules.snapHeight(140, WidgetKind.Tasks))
        assertEquals(240, WidgetContentRules.snapHeight(170, WidgetKind.Tasks))
        assertEquals(96, WidgetContentRules.snapHeight(100, WidgetKind.Quote))
        assertEquals(300, WidgetContentRules.snapHeight(290, WidgetKind.Focus))
    }

    @Test fun detailedCardsCannotShrinkBelowTheirContentHeight() {
        WidgetKind.entries.filter { it.isContact || it.isTask || it.isTrip || it.isClock || it.isMedia }.forEach { kind ->
            assertTrue(WidgetContentRules.height(1, kind) >= kind.heightDp)
            assertTrue(WidgetContentRules.snapHeight(1, kind) >= kind.heightDp)
        }
    }

    @Test fun scaleGrowsWithLargerWidgetAndShrinksWithSmaller() {
        val base = WidgetContentRules.scale(300, 240, WidgetKind.TasksManager)
        val bigger = WidgetContentRules.scale(360, 300, WidgetKind.TasksManager)
        val smaller = WidgetContentRules.scale(200, 180, WidgetKind.TasksManager)
        assertTrue(bigger.factor > base.factor)
        assertTrue(smaller.factor < base.factor)
        assertTrue(bigger.dp(56f) > base.dp(56f))
        assertTrue(smaller.dp(56f) < base.dp(56f))
    }

    @Test fun pickerSectionsGroupByCategory() {
        val sections = WidgetKind.pickerSections()
        assertEquals(listOf("Clock", "Media", "Contacts", "Tasks", "Travel", "Focus", "Creative", "Phone"), sections.map { it.first.title })
        assertEquals(5, sections.first { it.first == WidgetCategory.Clock }.second.size)
        assertEquals(4, sections.first { it.first == WidgetCategory.Media }.second.size)
        assertEquals(3, sections.first { it.first == WidgetCategory.Contacts }.second.size)
        assertEquals(5, sections.first { it.first == WidgetCategory.Tasks }.second.size)
        assertEquals(WidgetKind.entries.size, sections.sumOf { it.second.size })
    }

    @Test fun lightAccentIsDarkerThanDarkAccentForContrast() {
        assertTrue(WidgetColors.AccentLight != WidgetColors.AccentDark)
        // Lower RGB luminance on light surfaces.
        fun luminance(color: Int): Int =
            ((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)
        assertTrue(luminance(WidgetColors.AccentLight) < luminance(WidgetColors.AccentDark))
    }
}

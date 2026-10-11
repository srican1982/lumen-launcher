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
            val minimum = if (kind in setOf(WidgetKind.Quote, WidgetKind.Photos, WidgetKind.Scribble)) 96 else 132
            assertEquals(minimum, WidgetContentRules.height(1, kind))
            assertEquals(240, WidgetContentRules.height(240, kind))
            assertEquals(300, WidgetContentRules.height(999, kind))
        }
        assertEquals(1, WidgetContentRules.columns(0))
        assertEquals(2, WidgetContentRules.columns(3))
    }
}

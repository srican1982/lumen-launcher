package com.lumen.launcher.widgets

import org.junit.Assert.*
import org.junit.Test

class ClockWidgetKindsTest {
    @Test fun fiveClockWidgetsSkipSoloAnalog() {
        val clocks = WidgetKind.entries.filter { it.isClock }
        assertEquals(5, clocks.size)
        assertEquals(
            listOf(
                WidgetKind.ClockWeather,
                WidgetKind.WorldClock,
                WidgetKind.ClockFeatured,
                WidgetKind.CalendarDay,
                WidgetKind.MomentClock
            ),
            clocks
        )
        assertTrue(WidgetKind.WorldClock.wide)
        assertTrue(WidgetKind.ClockFeatured.wide)
        assertFalse(WidgetKind.ClockWeather.wide)
    }

    @Test fun worldCityCatalogIncludesDefaultFour() {
        val ids = WidgetWorldCities.catalog.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("la", "nyc", "lon", "tyo")))
        assertTrue(WidgetWorldCities.catalog.size >= 20)
    }
}

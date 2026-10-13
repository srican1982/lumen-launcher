package com.lumen.launcher.widgets

import org.junit.Assert.*
import org.junit.Test

class TripWidgetKindsTest {
    @Test fun allSevenVariantsHaveStableKeysAndSupportedSizes() {
        val trips = WidgetKind.entries.filter { it.isTrip }
        assertEquals(7, trips.size)
        trips.forEach { kind ->
            assertEquals(kind, WidgetKind.parse(kind.name))
            assertEquals(kind.heightDp, WidgetContentRules.height(kind.heightDp, kind))
        }
        assertEquals(240, WidgetKind.Trip.heightDp)
        assertTrue(WidgetKind.Trip.wide)
        assertFalse(WidgetKind.TripCompact.wide)
        assertFalse(WidgetKind.TripCollage.wide)
    }
    @Test fun nativeTripVariantsCanToggleOnlyTripCommands() {
        WidgetKind.entries.filter { it.isTrip }.forEach {
            assertTrue(WidgetCommandReceiver.allowed(it, "StartTrip"))
            assertTrue(WidgetCommandReceiver.allowed(it, "EndTrip"))
            assertFalse(WidgetCommandReceiver.allowed(it, "CompleteTask"))
        }
    }
}

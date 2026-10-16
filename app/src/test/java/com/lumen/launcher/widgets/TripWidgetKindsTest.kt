package com.lumen.launcher.widgets

import org.junit.Assert.*
import org.junit.Test

class TripWidgetKindsTest {
    @Test fun threeAppleStyleTravelWidgetsHaveStableKeys() {
        val trips = WidgetKind.entries.filter { it.isTrip }
        assertEquals(3, trips.size)
        assertEquals(
            listOf(WidgetKind.Trip, WidgetKind.TripComplete, WidgetKind.TravelDocs),
            trips
        )
        trips.forEach { kind ->
            assertEquals(kind, WidgetKind.parse(kind.name))
            assertEquals(WidgetCategory.Travel, kind.category)
            assertEquals(kind.heightDp, WidgetContentRules.height(kind.heightDp, kind))
        }
        assertTrue(WidgetKind.Trip.wide)
        assertFalse(WidgetKind.TripComplete.wide)
        assertFalse(WidgetKind.TravelDocs.wide)
    }

    @Test fun tripWidgetsOnlyAllowTripToggleCommands() {
        WidgetKind.entries.filter { it.isTrip }.forEach {
            assertTrue(WidgetCommandReceiver.allowed(it, "StartTrip"))
            assertTrue(WidgetCommandReceiver.allowed(it, "EndTrip"))
            assertFalse(WidgetCommandReceiver.allowed(it, "CompleteTask"))
        }
    }
}

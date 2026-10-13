package com.lumen.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerStackEffectTest {
    @Test fun effectOnlyStartsInBottomQuarter() {
        assertEquals(0f, drawerStackAmount(500f, 100, 1100), 0f)
        assertEquals(0f, drawerStackAmount(850f, 100, 1100), 0f)
        assertEquals(0.5f, drawerStackAmount(975f, 100, 1100), 0.0001f)
        assertEquals(1f, drawerStackAmount(1100f, 100, 1100), 0f)
    }

    @Test fun clampedAndSafeForEmptyViewport() {
        assertEquals(0f, drawerStackAmount(-100f, 0, 1000), 0f)
        assertEquals(1f, drawerStackAmount(1200f, 0, 1000), 0f)
        assertEquals(0f, drawerStackAmount(100f, 100, 100), 0f)
        assertEquals(0f, drawerStackAmount(Float.NaN, 0, 1000), 0f)
        assertTrue(drawerStackAmount(900f, 0, 1000) < drawerStackAmount(950f, 0, 1000))
    }
}

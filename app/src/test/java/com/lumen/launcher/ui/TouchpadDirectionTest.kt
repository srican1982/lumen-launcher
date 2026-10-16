package com.lumen.launcher.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class TouchpadDirectionTest {
    @Test fun returningToCenterDisarmsSelection() {
        assertEquals(TouchpadSwipe.Right, touchpadDirection(Offset(70f, 5f), 28f))
        assertNull(touchpadDirection(Offset(6f, 4f), 28f))
        assertNull(touchpadDirection(Offset.Zero, 28f))
    }

    @Test fun directionsMatchVisibleCardinalHints() {
        assertEquals(TouchpadSwipe.Up, touchpadDirection(Offset(10f, -50f), 28f))
        assertEquals(TouchpadSwipe.Down, touchpadDirection(Offset(-10f, 50f), 28f))
        assertEquals(TouchpadSwipe.Left, touchpadDirection(Offset(-50f, -10f), 28f))
        assertEquals(TouchpadSwipe.Right, touchpadDirection(Offset(50f, 10f), 28f))
    }

    @Test fun thresholdIsRadialAndSelectionCanSwitchWhileHeld() {
        assertNull(touchpadDirection(Offset(19f, 19f), 28f))
        assertEquals(TouchpadSwipe.Down, touchpadDirection(Offset(20f, 20f), 28f))
        assertEquals(TouchpadSwipe.Right, touchpadDirection(Offset(28f, 0f), 28f))
        assertEquals(TouchpadSwipe.Left, touchpadDirection(Offset(-28f, 0f), 28f))
    }

    @Test fun leftAndRightWinOverSlightUpwardDrift() {
        assertEquals(TouchpadSwipe.Left, touchpadDirection(Offset(-50f, -35f), 16f))
        assertEquals(TouchpadSwipe.Right, touchpadDirection(Offset(50f, -35f), 16f))
        assertEquals(TouchpadSwipe.Up, touchpadDirection(Offset(10f, -50f), 16f))
    }

    @Test fun dockRestOnRingSelectsVaultFromPadCenter() {
        val lift = 180f
        assertEquals(
            TouchpadSwipe.Down,
            touchpadDockSelection(touchpadDockDelta(Offset.Zero, lift), 16f)
        )
        assertEquals(
            TouchpadSwipe.Down,
            touchpadDockSelection(touchpadDockDelta(Offset(4f, -12f), lift), 16f)
        )
    }

    @Test fun dockVaultSelectsAcrossWholeBottomCardAfterVisitingCamera() {
        val lift = 180f
        // Still above the press origin (on the upper part of Vault) but below pad center.
        assertEquals(
            TouchpadSwipe.Down,
            touchpadDockSelection(
                touchpadDockDelta(Offset(0f, -40f), lift),
                16f,
                armed = TouchpadSwipe.Up
            )
        )
        // Mid-Vault should not require diving into the last 15% of the card.
        assertEquals(
            TouchpadSwipe.Down,
            touchpadDockSelection(
                touchpadDockDelta(Offset(0f, -80f), lift),
                16f,
                armed = TouchpadSwipe.Up
            )
        )
        // Still clearly on Camera (above pad center) stays Up.
        assertEquals(
            TouchpadSwipe.Up,
            touchpadDockSelection(
                touchpadDockDelta(Offset(0f, -240f), lift),
                16f,
                armed = TouchpadSwipe.Up
            )
        )
    }

    @Test fun dockSlideAwaySelectsOtherCards() {
        val lift = 180f
        assertEquals(
            TouchpadSwipe.Up,
            touchpadDockSelection(touchpadDockDelta(Offset(0f, -300f), lift), 16f)
        )
        assertEquals(
            TouchpadSwipe.Left,
            touchpadDockSelection(touchpadDockDelta(Offset(-160f, -180f), lift), 16f)
        )
        assertEquals(
            TouchpadSwipe.Right,
            touchpadDockSelection(touchpadDockDelta(Offset(160f, -180f), lift), 16f)
        )
    }
}

package com.lumen.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class MeMotionGeometryTest {
    @Test fun widgetConstraintsStayConstantDuringTheEntireRoundTrip() {
        val collapsed = 189
        val expanded = 613
        val spaceViewport = 1257
        for (frame in (0..1000) + (1000 downTo 0)) {
            val p = frame / 1000f
            val viewport = spaceViewport - MeMotionGeometry.expansion(expanded, collapsed, p)
            assertEquals(spaceViewport, MeMotionGeometry.contentHeight(viewport, expanded, collapsed, p, false))
            assertEquals(spaceViewport - expanded + collapsed,
                MeMotionGeometry.contentHeight(viewport, expanded, collapsed, p, true))
        }
    }
    @Test fun headerAndViewportTradeHeightWithoutMovingTheDock() {
        val collapsed = 72
        val expanded = 247
        val screen = 800
        val nav = 70
        val dock = 100
        for (frame in 0..120) {
            val header = collapsed + MeMotionGeometry.expansion(expanded, collapsed, frame / 120f)
            val viewport = screen - header - nav - dock
            assertEquals(screen - dock, header + nav + viewport)
        }
    }
    @Test fun smallScreensNeverProduceNegativeContentConstraints() {
        assertEquals(0, MeMotionGeometry.contentHeight(20, 290, 72, 0f, true))
        assertEquals(218, MeMotionGeometry.expansion(290, 72, 2f))
        assertEquals(0, MeMotionGeometry.expansion(290, 72, -1f))
    }
}

package com.lumen.launcher.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBoardLayoutTest {
    @Test fun everyDropSnapsToFixedDivisions() {
        val aligned = WidgetBoardLayout.align(WidgetPosition(.3f, 205f), 170f, 132f, 350f, emptyList())
        assertEquals(WidgetPosition(0f, 192f), aligned.position)
        val right = WidgetBoardLayout.align(WidgetPosition(.8f, 141f), 170f, 132f, 350f, emptyList())
        assertEquals(WidgetPosition(1f, 144f), right.position)
    }
    @Test fun occupiedDropSwapsEqualSizedWidgets() {
        val items = listOf(WidgetBoardSize(1, 1, 132), WidgetBoardSize(2, 1, 132))
        val initial = mapOf(1 to WidgetPosition(0f, 0f), 2 to WidgetPosition(1f, 0f))
        val result = WidgetBoardLayout.settle(items, 360f, initial, 1, WidgetPosition(1f, 0f))
        assertEquals(initial[2], result[1]); assertEquals(initial[1], result[2])
    }
    @Test fun mixedSizesAndLegacyOverlapsAreResolvedAndStable() {
        val items = listOf(WidgetBoardSize(1, 1, 132), WidgetBoardSize(2, 2, 240), WidgetBoardSize(3, 1, 180))
        val initial = items.associate { it.id to WidgetPosition(.3f, 25f) }
        val result = WidgetBoardLayout.settle(items, 360f, initial)
        assertEquals(result, WidgetBoardLayout.settle(items, 360f, result))
        val moved = WidgetBoardLayout.settle(items, 360f, result, 1, result.getValue(2))
        for (a in items) for (b in items) if (a.id < b.id) {
            val pa = moved.getValue(a.id); val pb = moved.getValue(b.id)
            val sameColumns = a.columns == 2 || b.columns == 2 || pa.xFraction == pb.xFraction
            val overlapsY = pa.yDp < pb.yDp + b.heightDp && pb.yDp < pa.yDp + a.heightDp
            org.junit.Assert.assertFalse(sameColumns && overlapsY)
            assertEquals(0f, pa.yDp % WidgetBoardLayout.ROW, .001f)
        }
    }
    @Test fun swappedLayoutRemainsSeparatedWhenRenderedAgain() {
        val items = listOf(WidgetBoardSize(1, 1, 132), WidgetBoardSize(2, 2, 180))
        val initial = mapOf(1 to WidgetPosition(0f, 48f), 2 to WidgetPosition(0f, 192f))
        val editing = WidgetBoardLayout.settle(items, 360f, initial, 1, initial.getValue(2))
        val normal = WidgetBoardLayout.settle(items, 360f, editing)
        assertEquals(editing, normal)
        val a = normal.getValue(1); val b = normal.getValue(2)
        org.junit.Assert.assertTrue(a.yDp + 132 <= b.yDp || b.yDp + 180 <= a.yDp)
    }
    @Test fun fullWidthCardStartsBelowTallestHalfWidthCard() {
        val layout = WidgetBoardLayout.defaults(listOf(
            WidgetBoardSize(1, 1, 132), WidgetBoardSize(2, 1, 240),
            WidgetBoardSize(3, 2, 96), WidgetBoardSize(4, 1, 132)
        ), 360f)
        assertEquals(WidgetPosition(0f, 0f), layout[1])
        assertEquals(WidgetPosition(1f, 0f), layout[2])
        assertEquals(WidgetPosition(0f, 250f), layout[3])
        assertEquals(WidgetPosition(0f, 356f), layout[4])
    }
    @Test fun fullWidthCardCannotOverlapAnUnpairedCard() {
        val layout = WidgetBoardLayout.defaults(listOf(
            WidgetBoardSize(1, 1, 180), WidgetBoardSize(2, 2, 132)
        ), 360f)
        assertEquals(WidgetPosition(0f, 190f), layout[2])
    }
}

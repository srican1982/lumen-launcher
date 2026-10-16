package com.lumen.launcher.widgets

import org.junit.Assert.*
import org.junit.Test

class HomeBoardLayoutTest {
    private val items = listOf(HomeBoardLayout.Item("widget", 2, 2)) +
        (1..8).map { HomeBoardLayout.Item("app$it", 1, 1) }
    @Test fun appsFillCellsBesideHalfWidthWidget() {
        val layout = HomeBoardLayout.arrange(items, 4, emptyMap())
        assertEquals(HomeBoardLayout.Cell(2, 0), layout["app1"])
        assertEquals(HomeBoardLayout.Cell(3, 0), layout["app2"])
        assertEquals(HomeBoardLayout.Cell(2, 1), layout["app3"])
    }
    @Test fun movingWidgetReflowsAppsWithoutOverlapAndRestoresPersistedLayout() {
        val original = HomeBoardLayout.arrange(items, 4, emptyMap())
        val moved = HomeBoardLayout.arrange(items, 4, original, "widget", HomeBoardLayout.Cell(2, 1))
        assertEquals(HomeBoardLayout.Cell(2, 1), moved["widget"])
        val occupied = mutableSetOf<Pair<Int, Int>>()
        items.forEach { item ->
            val cell = moved.getValue(item.key)
            for (y in cell.row until cell.row + item.rows) for (x in cell.column until cell.column + item.columns)
                assertTrue(occupied.add(x to y))
        }
        assertEquals(moved, HomeBoardLayout.arrange(items, 4, moved))
    }
    @Test fun draggingAppIntoWidgetMovesWidgetToFreeCells() {
        val original = HomeBoardLayout.arrange(items, 4, emptyMap())
        val moved = HomeBoardLayout.arrange(items, 4, original, "app1", HomeBoardLayout.Cell(0, 0))
        assertEquals(HomeBoardLayout.Cell(0, 0), moved["app1"])
        assertNotEquals(original["widget"], moved["widget"])
    }
    @Test fun fineRowsLeaveOnlySmallGapBelowWorldClock() {
        val rowStep = 12
        val widgetHeight = 240
        val widgetRows = kotlin.math.ceil((widgetHeight + 8).toDouble() / rowStep).toInt()
        val layout = HomeBoardLayout.arrange(listOf(HomeBoardLayout.Item("clock", 4, widgetRows),
            HomeBoardLayout.Item("app", 1, 9)), 4, emptyMap())
        val gap = layout.getValue("app").row * rowStep - widgetHeight
        assertTrue(gap in 8..19)
    }

    @Test fun removingWidgetClosesGapAndKeepsAppReadingOrder() {
        val remaining = listOf(HomeBoardLayout.Item("first", 1, 9), HomeBoardLayout.Item("second", 1, 9), HomeBoardLayout.Item("add", 1, 9))
        val saved = mapOf("second" to HomeBoardLayout.Cell(0, 21), "first" to HomeBoardLayout.Cell(1, 21), "add" to HomeBoardLayout.Cell(2, 21))
        val compacted = HomeBoardLayout.compact(remaining, 4, saved)
        assertEquals(HomeBoardLayout.Cell(0, 0), compacted["second"])
        assertEquals(HomeBoardLayout.Cell(1, 0), compacted["first"])
        assertEquals(HomeBoardLayout.Cell(2, 0), compacted["add"])
    }
    @Test fun compactingMixedItemsPacksAppsBesideRemainingWidget() {
        val remaining = listOf(HomeBoardLayout.Item("widget", 2, 18)) +
            (1..4).map { HomeBoardLayout.Item("app$it", 1, 9) }
        val saved = remaining.associate { it.key to HomeBoardLayout.Cell(0, 30) }
        val compacted = HomeBoardLayout.compact(remaining, 4, saved)
        assertEquals(HomeBoardLayout.Cell(0, 0), compacted["widget"])
        assertEquals(HomeBoardLayout.Cell(2, 0), compacted["app1"])
        assertEquals(HomeBoardLayout.Cell(2, 9), compacted["app3"])
        assertEquals(compacted, HomeBoardLayout.arrange(remaining, 4, compacted))
    }

    @Test fun appsBelowUnevenHalfWidgetKeepSharedHorizontalRows() {
        val mixed = listOf(HomeBoardLayout.Item("widget", 2, 23)) +
            (1..10).map { HomeBoardLayout.Item("app$it", 1, 10, rowStep = 10) }
        val layout = HomeBoardLayout.arrange(mixed, 4, emptyMap())
        assertEquals(HomeBoardLayout.Cell(2, 20), layout["app5"])
        assertEquals(HomeBoardLayout.Cell(0, 30), layout["app7"])
        assertEquals(HomeBoardLayout.Cell(3, 30), layout["app10"])
        assertTrue(mixed.drop(1).all { layout.getValue(it.key).row % 10 == 0 })
        assertEquals(layout, HomeBoardLayout.arrange(mixed, 4, layout))
    }

    @Test fun fullWidthWidgetStartsAnAlignedAppGridWithoutLargeGap() {
        val mixed = listOf(HomeBoardLayout.Item("widget", 4, 23)) +
            (1..8).map { HomeBoardLayout.Item("app$it", 1, 10, rowStep = 10) }
        val layout = HomeBoardLayout.arrange(mixed, 4, emptyMap())
        assertEquals(23, layout.getValue("app1").row)
        assertEquals(23, layout.getValue("app4").row)
        assertEquals(33, layout.getValue("app5").row)
        assertEquals(layout, HomeBoardLayout.arrange(mixed, 4, layout))
    }

    @Test fun existingStaggeredAppPositionsAreRealignedWithoutOverlap() {
        val apps = (1..4).map { HomeBoardLayout.Item("app$it", 1, 10, rowStep = 10) }
        val saved = apps.mapIndexed { index, item -> item.key to HomeBoardLayout.Cell(index, if (index < 2) 23 else 20) }.toMap()
        val layout = HomeBoardLayout.arrange(apps, 4, saved)
        assertTrue(layout.values.all { it.row % 10 == 0 })
        assertEquals(layout.size, layout.values.toSet().size)
    }

}

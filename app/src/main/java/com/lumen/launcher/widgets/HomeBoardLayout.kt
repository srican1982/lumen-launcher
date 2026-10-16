package com.lumen.launcher.widgets

/** One collision-free cell grid for both app shortcuts and native widgets. */
object HomeBoardLayout {
    data class Item(val key: String, val columns: Int, val rows: Int, val rowStep: Int = 1)
    data class Cell(val column: Int, val row: Int)
    /** Pack gaps after removal in the user's existing visual reading order. */
    fun compact(items: List<Item>, columns: Int, saved: Map<String, Cell>): Map<String, Cell> {
        val ordered = items.sortedWith(compareBy<Item> { if (it.key == "add") 1 else 0 }
            .thenBy { saved[it.key]?.row ?: Int.MAX_VALUE }
            .thenBy { saved[it.key]?.column ?: Int.MAX_VALUE })
        return arrange(ordered, columns, emptyMap())
    }
    fun arrange(items: List<Item>, columns: Int, saved: Map<String, Cell>, moving: String? = null,
                desired: Cell? = null): Map<String, Cell> {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val result = linkedMapOf<String, Cell>()
        val fullWidthEnds = mutableListOf<Int>()
        val ordered = items.sortedBy { if (it.key == moving) 0 else 1 }
        ordered.forEach { item ->
            val width = item.columns.coerceIn(1, columns)
            val rows = item.rows.coerceAtLeast(1)
            // Widgets retain fine resize increments. App tiles share row baselines,
            // restarted below full-width widgets, rather than packing each column separately.
            fun aligned(row: Int): Boolean {
                val anchor = fullWidthEnds.filter { it <= row }.maxOrNull() ?: 0
                return (row - anchor) % item.rowStep.coerceAtLeast(1) == 0
            }
            fun fits(cell: Cell) = aligned(cell.row) && cell.column >= 0 && cell.column + width <= columns && cell.row >= 0 &&
                (cell.row until cell.row + rows).all { y -> (cell.column until cell.column + width).all { x -> x to y !in occupied } }
            val preferred = (if (item.key == moving) desired else saved[item.key])?.let {
                var row = it.row.coerceAtLeast(0)
                while (!aligned(row)) row++
                Cell(it.column.coerceIn(0, columns - width), row)
            }
            var chosen = preferred?.takeIf(::fits)
            if (chosen == null) {
                var row = 0
                while (chosen == null) {
                    chosen = (0..columns - width).map { Cell(it, row) }.firstOrNull(::fits)
                    row++
                }
            }
            val cell = requireNotNull(chosen)
            result[item.key] = cell
            if (width == columns && item.rowStep == 1) fullWidthEnds += cell.row + rows
            for (y in cell.row until cell.row + rows) for (x in cell.column until cell.column + width) occupied += x to y
        }
        return result
    }
}

package com.lumen.launcher.widgets

import kotlin.math.roundToInt

data class WidgetPosition(val xFraction: Float, val yDp: Float)
data class WidgetBoardSize(val id: Int, val columns: Int, val heightDp: Int)
data class WidgetBoardRect(val left: Float, val top: Float, val width: Float, val height: Float)
data class WidgetAlignment(val position: WidgetPosition, val guideX: Float?, val guideY: Float?)

/** Two columns and 48dp row divisions; every persisted placement is collision-free. */
object WidgetBoardLayout {
    const val ROW = 48f
    fun align(position: WidgetPosition, width: Float, height: Float, boardWidth: Float,
              others: List<WidgetBoardRect>, threshold: Float = 8f): WidgetAlignment {
        val x = if (width >= boardWidth) 0f else if (position.xFraction >= .5f) 1f else 0f
        val y = (position.yDp.coerceAtLeast(0f) / ROW).roundToInt() * ROW
        return WidgetAlignment(WidgetPosition(x, y), x * (boardWidth - width), y)
    }

    fun settle(items: List<WidgetBoardSize>, width: Float, saved: Map<Int, WidgetPosition>, moving: Int? = null,
               desired: WidgetPosition? = null): Map<Int, WidgetPosition> {
        val sizes = items.associateBy { it.id }
        val initial = defaults(items, width) + saved
        val requested = initial.toMutableMap()
        var target: Int? = null
        if (moving != null && desired != null && moving in sizes) {
            val size = sizes.getValue(moving)
            val w = if (size.columns == 2) width else (width - 10f) / 2f
            val snap = align(desired, w, size.heightDp.toFloat(), width, emptyList()).position
            val x = snap.xFraction * (width - w)
            target = items.filter { it.id != moving }.minByOrNull { item ->
                val p = initial.getValue(item.id)
                val ow = if (item.columns == 2) width else (width - 10f) / 2f
                val ox = p.xFraction * (width - ow)
                if (x < ox + ow && x + w > ox && snap.yDp < p.yDp + item.heightDp && snap.yDp + size.heightDp > p.yDp)
                    kotlin.math.abs(snap.yDp - p.yDp) else Float.POSITIVE_INFINITY
            }?.id?.takeIf { id ->
                val p = initial.getValue(id); val other = sizes.getValue(id)
                val ow = if (other.columns == 2) width else (width - 10f) / 2f
                val ox = p.xFraction * (width - ow)
                x < ox + ow && x + w > ox && snap.yDp < p.yDp + other.heightDp && snap.yDp + size.heightDp > p.yDp
            }
            requested[moving] = target?.let { initial.getValue(it) } ?: snap
            if (target != null) requested[target] = initial.getValue(moving)
        }
        val order = (listOfNotNull(moving, target) + items.map { it.id }).distinct().filter { it in sizes }
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val result = linkedMapOf<Int, WidgetPosition>()
        order.forEach { id ->
            val size = sizes.getValue(id)
            val cols = size.columns.coerceIn(1, 2)
            val rows = kotlin.math.ceil((size.heightDp + 10f) / ROW).toInt()
            val p = requested.getValue(id)
            val wanted = (p.yDp.coerceIn(0f, 50000f) / ROW).roundToInt()
            val maxRow = maxOf(wanted, result.values.maxOfOrNull { (it.yDp / ROW).toInt() } ?: 0) + items.sumOf { kotlin.math.ceil((it.heightDp + 10f) / ROW).toInt() }
            var bestRow = 0; var bestCol = 0; var distance = Float.POSITIVE_INFINITY
            for (row in 0..maxRow) for (col in 0..(2 - cols)) {
                if ((row until row + rows).any { r -> (col until col + cols).any { c -> c to r in occupied } }) continue
                val dx = col * (width + 10f) / 2f - (if (cols == 2) 0f else p.xFraction * (width + 10f) / 2f)
                val dy = (row - wanted) * ROW
                val d = dx * dx + dy * dy
                if (d < distance) { distance = d; bestRow = row; bestCol = col }
            }
            result[id] = WidgetPosition(bestCol.toFloat(), bestRow * ROW)
            for (r in bestRow until bestRow + rows) for (c in bestCol until bestCol + cols) occupied.add(c to r)
        }
        return result
    }
    fun defaults(items: List<WidgetBoardSize>, widthDp: Float): Map<Int, WidgetPosition> {
        val result = linkedMapOf<Int, WidgetPosition>()
        var y = 0f
        var right = false
        var rowHeight = 0
        items.forEach { item ->
            if (item.columns == 2 && right) {
                y += rowHeight + 10f
                right = false
                rowHeight = 0
            }
            result[item.id] = WidgetPosition(if (right && widthDp > 0) 1f else 0f, y)
            rowHeight = maxOf(rowHeight, item.heightDp)
            if (item.columns == 2 || right) {
                y += rowHeight + 10f
                right = false
                rowHeight = 0
            } else right = true
        }
        return result
    }
}

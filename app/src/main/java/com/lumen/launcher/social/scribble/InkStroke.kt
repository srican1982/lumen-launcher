package com.lumen.launcher.social.scribble

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

data class InkStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val erase: Boolean
)

enum class ScribbleBrushStyle { Sketch, Marker, Glow }

enum class ScribbleExportBackground { Transparent, Light, Dark, LumenGradient }

object StrokeSmoothing {
    fun filterPoints(points: List<Offset>, minStep: Float = 2.5f): List<Offset> {
        if (points.size < 2) return points
        val out = ArrayList<Offset>(points.size)
        out.add(points.first())
        var last = points.first()
        for (i in 1 until points.size) {
            val p = points[i]
            if ((p - last).getDistance() >= minStep) {
                out.add(p)
                last = p
            }
        }
        if (out.last() != points.last()) out.add(points.last())
        return out
    }

    fun toPath(points: List<Offset>): Path {
        val filtered = filterPoints(points)
        val path = Path()
        if (filtered.isEmpty()) return path
        if (filtered.size == 1) {
            path.moveTo(filtered[0].x, filtered[0].y)
            return path
        }
        path.moveTo(filtered[0].x, filtered[0].y)
        for (i in 1 until filtered.size) {
            val prev = filtered[i - 1]
            val cur = filtered[i]
            val midX = (prev.x + cur.x) / 2f
            val midY = (prev.y + cur.y) / 2f
            if (i == 1) {
                path.lineTo(midX, midY)
            } else {
                path.quadraticTo(prev.x, prev.y, midX, midY)
            }
        }
        val last = filtered.last()
        path.lineTo(last.x, last.y)
        return path
    }

    fun bounds(points: List<Offset>, pad: Float): androidx.compose.ui.geometry.Rect? {
        if (points.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        points.forEach { p ->
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }
        return androidx.compose.ui.geometry.Rect(
            minX - pad,
            minY - pad,
            maxX + pad,
            maxY + pad
        )
    }
}

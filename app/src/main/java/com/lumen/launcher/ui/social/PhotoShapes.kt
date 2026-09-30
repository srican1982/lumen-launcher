package com.lumen.launcher.ui.social

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

internal enum class PhotoShape { Pen, Circle, Square, Rectangle, Arrow, Line }

internal fun photoShapePath(points: List<Offset>, tool: PhotoShape): Path {
    if (tool == PhotoShape.Pen) return com.lumen.launcher.social.scribble.StrokeSmoothing.toPath(points)
    val path = Path()
    if (points.size < 2) return path
    val start = points.first()
    var end = points.last()
    if (tool == PhotoShape.Circle || tool == PhotoShape.Square) {
        val side = min(abs(end.x - start.x), abs(end.y - start.y))
        end = Offset(start.x + side * sign(end.x - start.x), start.y + side * sign(end.y - start.y))
    }
    val bounds = Rect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
    when (tool) {
        PhotoShape.Circle -> path.addOval(bounds)
        PhotoShape.Square, PhotoShape.Rectangle -> path.addRect(bounds)
        else -> {
            path.moveTo(start.x, start.y); path.lineTo(end.x, end.y)
            if (tool == PhotoShape.Arrow) {
                val delta = end - start
                val length = delta.getDistance()
                if (length > 0f) {
                    val unit = delta / length
                    val head = length * .22f
                    val base = end - unit * head
                    val normal = Offset(-unit.y, unit.x) * head * .55f
                    path.moveTo((base + normal).x, (base + normal).y)
                    path.lineTo(end.x, end.y)
                    path.lineTo((base - normal).x, (base - normal).y)
                }
            }
        }
    }
    return path
}

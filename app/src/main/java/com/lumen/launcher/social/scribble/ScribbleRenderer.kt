package com.lumen.launcher.social.scribble

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

object ScribbleRenderer {
    fun drawStroke(
        scope: DrawScope,
        stroke: InkStroke,
        style: ScribbleBrushStyle,
        live: Boolean = false
    ) {
        val path = StrokeSmoothing.toPath(stroke.points)
        if (stroke.erase) {
            scope.drawPath(
                path,
                color = Color.Transparent,
                style = Stroke(stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Clear
            )
            return
        }
        val baseWidth = when (style) {
            ScribbleBrushStyle.Sketch -> stroke.width
            ScribbleBrushStyle.Marker -> stroke.width * 1.35f
            ScribbleBrushStyle.Glow -> stroke.width
        }
        if (style == ScribbleBrushStyle.Glow) {
            scope.drawPath(
                path,
                color = stroke.color.copy(alpha = 0.35f),
                style = Stroke(baseWidth * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            scope.drawPath(
                path,
                color = stroke.color.copy(alpha = 0.55f),
                style = Stroke(baseWidth * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        if (style == ScribbleBrushStyle.Marker) {
            scope.drawPath(
                path,
                color = stroke.color.copy(alpha = 0.45f),
                style = Stroke(baseWidth * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        scope.drawPath(
            path,
            color = stroke.color,
            style = Stroke(baseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    fun widthFor(style: ScribbleBrushStyle, erasing: Boolean): Float = when {
        erasing -> 28f
        style == ScribbleBrushStyle.Marker -> 9f
        style == ScribbleBrushStyle.Glow -> 7f
        else -> 6f
    }
}

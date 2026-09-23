package com.lumen.launcher.social.scribble

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import kotlin.math.min

object ScribbleExport {
    private const val EXPORT_MAX = 1080

    fun render(
        strokes: List<InkStroke>,
        brushStyle: ScribbleBrushStyle,
        background: ScribbleExportBackground
    ): Bitmap {
        val allPoints = strokes.flatMap { it.points }
        val bounds = StrokeSmoothing.bounds(allPoints, pad = 28f)
            ?: return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val bw = bounds.width.coerceAtLeast(40f)
        val bh = bounds.height.coerceAtLeast(40f)
        val scale = min(EXPORT_MAX / bw, EXPORT_MAX / bh).coerceAtMost(4f)
        val outW = (bw * scale).toInt().coerceIn(256, EXPORT_MAX)
        val outH = (bh * scale).toInt().coerceIn(256, EXPORT_MAX)

        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        paintBackground(canvas, outW, outH, background)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            val mapped = stroke.points.map { p ->
                Offset((p.x - bounds.left) * scale, (p.y - bounds.top) * scale)
            }
            val path = StrokeSmoothing.toPath(mapped).asAndroidPath()
            val w = ScribbleRenderer.widthFor(brushStyle, stroke.erase) * scale
            paint.strokeWidth = w
            if (stroke.erase) {
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                canvas.drawPath(path, paint)
                paint.xfermode = null
            } else {
                when (brushStyle) {
                    ScribbleBrushStyle.Glow -> {
                        paint.color = stroke.color.copy(alpha = 0.35f).toArgb()
                        paint.strokeWidth = w * 2.2f
                        canvas.drawPath(path, paint)
                        paint.color = stroke.color.copy(alpha = 0.55f).toArgb()
                        paint.strokeWidth = w * 1.4f
                        canvas.drawPath(path, paint)
                    }
                    ScribbleBrushStyle.Marker -> {
                        paint.color = stroke.color.copy(alpha = 0.45f).toArgb()
                        paint.strokeWidth = w * 1.15f
                        canvas.drawPath(path, paint)
                    }
                    ScribbleBrushStyle.Sketch -> Unit
                }
                paint.color = stroke.color.toArgb()
                paint.strokeWidth = w
                canvas.drawPath(path, paint)
            }
        }
        return bmp
    }

    private fun paintBackground(canvas: AndroidCanvas, w: Int, h: Int, bg: ScribbleExportBackground) {
        when (bg) {
            ScribbleExportBackground.Transparent -> Unit
            ScribbleExportBackground.Light -> canvas.drawColor(Color(0xFFF4F0FA).toArgb())
            ScribbleExportBackground.Dark -> canvas.drawColor(Color(0xFF14121C).toArgb())
            ScribbleExportBackground.LumenGradient -> {
                val paint = Paint().apply {
                    shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        Color(0xFF3D2468).toArgb(),
                        Color(0xFF120A24).toArgb(),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
        }
    }
}

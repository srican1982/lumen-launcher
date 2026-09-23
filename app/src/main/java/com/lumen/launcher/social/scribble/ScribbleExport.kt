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

    /** Sticker line weight: ~4% of the export size, i.e. roughly 18px once fitted into 512px. */
    private const val STICKER_LINE_FRACTION = 0.04f

    private fun scaleFor(width: Float, height: Float): Float {
        val bw = width.coerceAtLeast(40f)
        val bh = height.coerceAtLeast(40f)
        return min(EXPORT_MAX / bw, EXPORT_MAX / bh).coerceAtMost(4f)
    }

    private fun stickerBoost(style: ScribbleBrushStyle, scale: Float): Float {
        val penWidth = ScribbleRenderer.widthFor(style, erasing = false) * scale
        return (EXPORT_MAX * STICKER_LINE_FRACTION / penWidth).coerceAtLeast(1f)
    }

    fun render(
        strokes: List<InkStroke>,
        brushStyle: ScribbleBrushStyle,
        background: ScribbleExportBackground,
        sticker: Boolean = false
    ): Bitmap {
        val allPoints = strokes.flatMap { it.points }
        var bounds = StrokeSmoothing.bounds(allPoints, pad = 28f)
            ?: return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        var scale = scaleFor(bounds.width, bounds.height)
        // Stickers get shrunk to 512px later, which would leave hairline strokes under a
        // fat die-cut border. Give them a minimum line weight relative to the output size.
        var boost = 1f
        if (sticker) {
            boost = stickerBoost(brushStyle, scale)
            // Thicker lines need more padding so round caps are not clipped at the edges.
            bounds = StrokeSmoothing.bounds(allPoints, pad = 28f * boost) ?: bounds
            scale = scaleFor(bounds.width, bounds.height)
            boost = stickerBoost(brushStyle, scale)
        }

        val bw = bounds.width.coerceAtLeast(40f)
        val bh = bounds.height.coerceAtLeast(40f)
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
            val w = ScribbleRenderer.widthFor(brushStyle, stroke.erase) * scale * boost
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

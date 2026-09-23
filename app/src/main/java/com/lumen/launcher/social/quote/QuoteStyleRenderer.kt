package com.lumen.launcher.social.quote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class QuoteStyle { Bubble, Sticker, Minimal, Glass }

enum class QuoteAspect(val width: Int, val height: Int) {
    Square(1080, 1080),
    Story(1080, 1920),
    Landscape(1920, 1080)
}

enum class QuoteBackgroundKind { PurpleGradient, LumenDark, Light, SocialBlue, Transparent }

object QuoteStyleRenderer {
    /** Lower-res live preview for the Quote composer UI. */
    fun renderPreview(
        text: String,
        style: QuoteStyle,
        background: QuoteBackgroundKind
    ): Bitmap {
        val full = render(text, style, QuoteAspect.Square, background)
        return Bitmap.createScaledBitmap(full, 540, 540, true)
    }

    fun render(
        text: String,
        style: QuoteStyle,
        aspect: QuoteAspect,
        background: QuoteBackgroundKind
    ): Bitmap {
        val w = aspect.width
        val h = aspect.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, w, h, background, style)
        val quote = text.trim().ifBlank { "Your quote" }
        val lines = wrapLines(quote, maxChars = if (aspect == QuoteAspect.Landscape) 32 else 22)
        when (style) {
            QuoteStyle.Bubble -> drawBubble(canvas, w, h, lines)
            QuoteStyle.Sticker -> drawSticker(canvas, w, h, lines)
            QuoteStyle.Minimal -> drawMinimal(canvas, w, h, lines)
            QuoteStyle.Glass -> drawGlass(canvas, w, h, lines)
        }
        if (background != QuoteBackgroundKind.Transparent && style != QuoteStyle.Minimal) {
            drawWatermark(canvas, w, h)
        }
        return bmp
    }

    private fun wrapLines(text: String, maxChars: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (next.length <= maxChars) line = next
            else {
                if (line.isNotEmpty()) lines.add(line)
                line = word
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.ifEmpty { listOf(text) }
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, bg: QuoteBackgroundKind, style: QuoteStyle) {
        if (bg == QuoteBackgroundKind.Transparent && style == QuoteStyle.Sticker) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (bg) {
            QuoteBackgroundKind.Transparent -> Unit
            QuoteBackgroundKind.Light -> canvas.drawColor(Color(0xFFF8F6FC).toArgb())
            QuoteBackgroundKind.LumenDark -> {
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color(0xFF2A1450).toArgb(), Color(0xFF0E0818).toArgb(),
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            QuoteBackgroundKind.PurpleGradient -> {
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    Color(0xFF7C3AED).toArgb(), Color(0xFF2A1848).toArgb(),
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            QuoteBackgroundKind.SocialBlue -> canvas.drawColor(Color(0xFF4FA3E3).toArgb())
        }
    }

    /**
     * Playful sticker text: white fill, thick colored rim, stacked extrusion, soft shadow
     * (inspired by bold social sticker lettering — not a brand copy).
     */
    private fun drawBubble(canvas: Canvas, w: Int, h: Int, lines: List<String>) {
        val body = lines.joinToString("\n")
        val size = autoTextSize(lines, w, 118f, 52f)
        val layoutW = (w * 0.88f).toInt()
        val typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = size
            this.typeface = typeface
        }
        val layout = staticLayout(body, fillPaint, layoutW)
        val x = (w - layout.width) / 2f
        val y = (h - layout.height) / 2f

        canvas.save()
        canvas.translate(x, y)

        val shadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0x44000000).toArgb()
            textSize = size
            this.typeface = typeface
            setShadowLayer(size * 0.14f, size * 0.05f, size * 0.09f, Color(0x99000000).toArgb())
        }
        staticLayout(body, shadowPaint, layoutW).draw(canvas)
        shadowPaint.clearShadowLayer()

        val extrusionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF1E3A8A).toArgb()
            textSize = size
            this.typeface = typeface
        }
        val extrusionSteps = 8
        for (step in extrusionSteps downTo 1) {
            canvas.save()
            canvas.translate(step * 1.35f, step * 2.1f)
            staticLayout(body, extrusionPaint, layoutW).draw(canvas)
            canvas.restore()
        }

        val rimPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF7C3AED).toArgb()
            textSize = size
            this.typeface = typeface
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = size * 0.26f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        staticLayout(body, rimPaint, layoutW).draw(canvas)

        val highlightRim = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFB794F4).toArgb()
            textSize = size
            this.typeface = typeface
            style = Paint.Style.STROKE
            strokeWidth = size * 0.09f
            strokeJoin = Paint.Join.ROUND
        }
        staticLayout(body, highlightRim, layoutW).draw(canvas)

        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawSticker(canvas: Canvas, w: Int, h: Int, lines: List<String>) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = autoTextSize(lines, w, 84f, 40f)
            setShadowLayer(12f, 0f, 6f, Color(0x99000000).toArgb())
        }
        val outline = TextPaint(textPaint).apply {
            color = Color(0xFF7C3AED).toArgb()
            style = Paint.Style.STROKE
            strokeWidth = textPaint.textSize * 0.12f
            clearShadowLayer()
        }
        val layout = staticLayout(lines.joinToString("\n"), textPaint, (w * 0.8f).toInt())
        val x = (w - layout.width) / 2f
        val y = (h - layout.height) / 2f
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.drawText(lines.joinToString("\n"), 0f, 0f, outline)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawMinimal(canvas: Canvas, w: Int, h: Int, lines: List<String>) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF1E1B2E).toArgb()
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = autoTextSize(lines, w, 72f, 36f)
        }
        val layout = staticLayout(lines.joinToString("\n"), textPaint, (w * 0.7f).toInt())
        val x = (w - layout.width) / 2f
        val y = (h - layout.height) / 2f
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawGlass(canvas: Canvas, w: Int, h: Int, lines: List<String>) {
        val panelW = w * 0.78f
        val panelH = h * 0.42f
        val left = (w - panelW) / 2f
        val top = (h - panelH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0x66FFFFFF).toArgb()
        }
        canvas.drawRoundRect(left, top, left + panelW, top + panelH, 48f, 48f, paint)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
            textSize = autoTextSize(lines, w, 64f, 34f)
        }
        val layout = staticLayout(lines.joinToString("\n"), textPaint, (panelW * 0.85f).toInt())
        canvas.save()
        canvas.translate(left + (panelW - layout.width) / 2f, top + (panelH - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawWatermark(canvas: Canvas, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0x55FFFFFF).toArgb()
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
        }
        canvas.drawText("Lumen", w - 140f, h - 48f, paint)
    }

    private fun autoTextSize(lines: List<String>, width: Int, max: Float, min: Float): Float {
        val longest = lines.maxOf { it.length }.coerceAtLeast(1)
        val target = (width * 0.75f / longest * 1.8f).coerceIn(min, max)
        return target
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .build()
    }
}

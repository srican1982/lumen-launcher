package com.lumen.launcher.social.quote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.lumen.launcher.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Text designs for Quote. */
enum class QuoteStyle(val label: String) {
    Bubble("Bubble"),
    Sticker("Sticker"),
    Candy("Candy"),
    Retro("Retro"),
    Comic("Comic"),
    Neon("Neon"),
    Minimal("Minimal"),
    Glass("Glass")
}

enum class QuoteAspect(val width: Int, val height: Int) {
    Square(1080, 1080),
    Story(1080, 1920),
    Landscape(1920, 1080)
}

enum class QuoteBackgroundKind { PurpleGradient, LumenDark, Light, SocialBlue, Transparent }

/**
 * Where the text sits: offsets are fractions of the image size (-0.5..0.5, 0 = centered),
 * rotation is in degrees, scale multiplies the auto-fitted size.
 */
data class QuoteTextTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val scale: Float = 1f
) {
    val isDefault: Boolean get() = this == QuoteTextTransform()
}

/** Optional shape behind the text; it can be filled with a color or a photo. */
enum class QuoteShape(val label: String) {
    None("None"),
    Circle("Circle"),
    Heart("Heart"),
    Star("Star"),
    Speech("Speech"),
    Rounded("Rounded")
}

/** Shape outlines, shared by the renderer and the UI chip icons. */
object QuoteShapes {
    /** The shape's outline, fitted into [r] (expected square). */
    fun path(shape: QuoteShape, r: RectF): Path {
        val p = Path()
        fun x(f: Float) = r.left + f * r.width()
        fun y(f: Float) = r.top + f * r.height()
        when (shape) {
            QuoteShape.None -> Unit
            QuoteShape.Circle -> p.addOval(r, Path.Direction.CW)
            QuoteShape.Rounded -> p.addRoundRect(r, r.width() * 0.22f, r.height() * 0.22f, Path.Direction.CW)
            QuoteShape.Heart -> {
                p.moveTo(x(0.5f), y(0.93f))
                p.cubicTo(x(0.16f), y(0.72f), x(0.0f), y(0.52f), x(0.02f), y(0.31f))
                p.cubicTo(x(0.04f), y(0.11f), x(0.22f), y(0.03f), x(0.35f), y(0.05f))
                p.cubicTo(x(0.44f), y(0.07f), x(0.5f), y(0.16f), x(0.5f), y(0.21f))
                p.cubicTo(x(0.5f), y(0.16f), x(0.56f), y(0.07f), x(0.65f), y(0.05f))
                p.cubicTo(x(0.78f), y(0.03f), x(0.96f), y(0.11f), x(0.98f), y(0.31f))
                p.cubicTo(x(1.0f), y(0.52f), x(0.84f), y(0.72f), x(0.5f), y(0.93f))
                p.close()
            }
            QuoteShape.Star -> {
                val cx = r.centerX()
                val cy = r.top + r.height() * 0.53f
                val outer = r.width() * 0.5f
                val inner = outer * 0.56f
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) outer else inner
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val px = cx + (cos(a) * rad).toFloat()
                    val py = cy + (sin(a) * rad).toFloat()
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
            }
            QuoteShape.Speech -> {
                val body = Path().apply {
                    addRoundRect(RectF(x(0.02f), y(0.06f), x(0.98f), y(0.76f)), r.width() * 0.16f, r.height() * 0.16f, Path.Direction.CW)
                }
                val tail = Path().apply {
                    moveTo(x(0.22f), y(0.70f))
                    lineTo(x(0.14f), y(0.96f))
                    lineTo(x(0.44f), y(0.72f))
                    close()
                }
                body.op(tail, Path.Op.UNION)
                p.set(body)
            }
        }
        return p
    }

    /** Where text fits comfortably inside the shape. */
    fun textBox(shape: QuoteShape, r: RectF): RectF {
        fun box(l: Float, t: Float, rr: Float, b: Float) =
            RectF(r.left + l * r.width(), r.top + t * r.height(), r.left + rr * r.width(), r.top + b * r.height())
        return when (shape) {
            QuoteShape.None -> RectF(r)
            QuoteShape.Circle -> box(0.16f, 0.22f, 0.84f, 0.78f)
            QuoteShape.Rounded -> box(0.09f, 0.14f, 0.91f, 0.86f)
            QuoteShape.Heart -> box(0.16f, 0.20f, 0.84f, 0.62f)
            QuoteShape.Star -> box(0.26f, 0.38f, 0.74f, 0.70f)
            QuoteShape.Speech -> box(0.09f, 0.12f, 0.91f, 0.70f)
        }
    }
}

object QuoteStyleRenderer {
    @Volatile
    private var cachedTypeface: Typeface? = null

    /** Lumen's rounded display font (Outfit SemiBold), bundled — no download needed. */
    private fun displayTypeface(context: Context): Typeface {
        cachedTypeface?.let { return it }
        val loaded = ResourcesCompat.getFont(context.applicationContext, R.font.outfit_semibold)
            ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        cachedTypeface = loaded
        return loaded
    }

    /** Lower-res live preview (kept for older callers). */
    fun renderPreview(
        context: Context,
        text: String,
        style: QuoteStyle,
        background: QuoteBackgroundKind
    ): Bitmap = renderSized(context, text, style, 540, 540, background, QuoteShape.None, null, watermark = true)

    /** Full-size export. */
    fun render(
        context: Context,
        text: String,
        style: QuoteStyle,
        aspect: QuoteAspect,
        background: QuoteBackgroundKind,
        shape: QuoteShape = QuoteShape.None,
        shapePhoto: Bitmap? = null,
        transform: QuoteTextTransform = QuoteTextTransform()
    ): Bitmap = renderSized(context, text, style, aspect.width, aspect.height, background, shape, shapePhoto, watermark = true, transform = transform)

    /** Small square thumbnail for the design picker. Everything scales, so it matches the real result. */
    fun renderThumb(
        context: Context,
        text: String,
        style: QuoteStyle,
        background: QuoteBackgroundKind,
        shape: QuoteShape,
        shapePhoto: Bitmap?,
        size: Int = 240
    ): Bitmap = renderSized(context, text, style, size, size, background, shape, shapePhoto, watermark = false)

    fun renderSized(
        context: Context,
        text: String,
        style: QuoteStyle,
        w: Int,
        h: Int,
        background: QuoteBackgroundKind,
        shape: QuoteShape,
        shapePhoto: Bitmap?,
        watermark: Boolean,
        transform: QuoteTextTransform = QuoteTextTransform()
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, w, h, background)

        val unit = min(w, h).toFloat()
        val box: RectF = if (shape != QuoteShape.None) {
            val s = unit * 0.86f
            val shapeRect = RectF((w - s) / 2f, (h - s) / 2f, (w + s) / 2f, (h + s) / 2f)
            drawShape(canvas, shape, shapeRect, shapePhoto, unit)
            QuoteShapes.textBox(shape, shapeRect)
        } else {
            RectF(w * 0.05f, h * 0.10f, w * 0.95f, h * 0.90f)
        }

        val quote = text.trim().ifBlank { "Your quote" }
        val maxChars = when {
            shape != QuoteShape.None -> 14
            w > h -> 30
            else -> 20
        }
        val lines = wrapLines(quote, maxChars)
        val body = lines.joinToString("\n")
        val face = displayTypeface(context)
        val bgIsLight = background == QuoteBackgroundKind.Light && shape == QuoteShape.None

        // User adjustments: move, rotate and resize the text around the text area's center.
        canvas.save()
        canvas.translate(transform.offsetX * w, transform.offsetY * h)
        canvas.rotate(transform.rotation, box.centerX(), box.centerY())
        canvas.scale(transform.scale, transform.scale, box.centerX(), box.centerY())
        when (style) {
            QuoteStyle.Bubble -> drawBubble(canvas, box, lines, body, face, unit)
            QuoteStyle.Sticker -> drawSticker(canvas, box, lines, body, face, unit)
            QuoteStyle.Candy -> drawCandy(canvas, box, lines, body, face, unit)
            QuoteStyle.Retro -> drawRetro(canvas, box, lines, body, face, unit)
            QuoteStyle.Comic -> drawComic(canvas, box, lines, body, face, unit)
            QuoteStyle.Neon -> drawNeon(canvas, box, lines, body, face, unit)
            QuoteStyle.Minimal -> drawMinimal(canvas, box, lines, body, unit, bgIsLight)
            QuoteStyle.Glass -> drawGlass(canvas, box, lines, body, face, unit)
        }
        canvas.restore()
        if (watermark && background != QuoteBackgroundKind.Transparent) drawWatermark(canvas, w, h, unit)
        return bmp
    }

    // ───────────────────────── backgrounds & shapes ─────────────────────────

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, bg: QuoteBackgroundKind) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (bg) {
            QuoteBackgroundKind.Transparent -> Unit
            QuoteBackgroundKind.Light -> canvas.drawColor(Color(0xFFF8F6FC).toArgb())
            QuoteBackgroundKind.LumenDark -> {
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color(0xFF2A1450).toArgb(), Color(0xFF0E0818).toArgb(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            QuoteBackgroundKind.PurpleGradient -> {
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color(0xFF7C3AED).toArgb(), Color(0xFF2A1848).toArgb(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            QuoteBackgroundKind.SocialBlue -> drawSky(canvas, w, h)
        }
    }

    /** Soft sky gradient with blurred clouds (Sky background). */
    private fun drawSky(canvas: Canvas, w: Int, h: Int) {
        val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color(0xFF4AABF2).toArgb(), Color(0xFF8ED2FF).toArgb(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sky)
        val unit = min(w, h).toFloat()
        val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            maskFilter = BlurMaskFilter(unit * 0.035f, BlurMaskFilter.Blur.NORMAL)
        }
        fun puff(cx: Float, cy: Float, r: Float, alpha: Int) {
            cloud.alpha = alpha
            canvas.drawCircle(cx - r * 0.9f, cy + r * 0.15f, r * 0.75f, cloud)
            canvas.drawCircle(cx, cy - r * 0.2f, r, cloud)
            canvas.drawCircle(cx + r * 1.0f, cy + r * 0.1f, r * 0.8f, cloud)
            canvas.drawOval(cx - r * 1.7f, cy, cx + r * 1.8f, cy + r * 0.9f, cloud)
        }
        puff(w * 0.10f, h - unit * 0.06f, unit * 0.11f, 150)
        puff(w * 0.88f, h - unit * 0.13f, unit * 0.10f, 130)
        puff(w * 0.06f, h * 0.22f, unit * 0.07f, 70)
    }

    /** Shape with soft shadow, color or photo fill, and a white sticker edge. */
    private fun drawShape(canvas: Canvas, shape: QuoteShape, r: RectF, photo: Bitmap?, unit: Float) {
        val path = QuoteShapes.path(shape, r)
        val rounded = if (shape == QuoteShape.Star) CornerPathEffect(unit * 0.035f) else null

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40000000
            maskFilter = BlurMaskFilter(unit * 0.03f, BlurMaskFilter.Blur.NORMAL)
            pathEffect = rounded
        }
        canvas.save()
        canvas.translate(0f, unit * 0.018f)
        canvas.drawPath(path, shadow)
        canvas.restore()

        if (photo != null) {
            // Rounded star corners also apply to the clip by drawing the fill through a paint shader.
            val src = centerCrop(photo, r.width() / r.height())
            val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = android.graphics.BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    val m = android.graphics.Matrix()
                    m.setRectToRect(RectF(src), r, android.graphics.Matrix.ScaleToFit.FILL)
                    setLocalMatrix(m)
                }
                pathEffect = rounded
            }
            canvas.drawPath(path, fill)
            // Light scrim so the text stays readable on busy photos.
            val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000; pathEffect = rounded }
            canvas.drawPath(path, scrim)
        } else {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(r.left, r.top, r.right, r.bottom, Color(0xFFFF9ED8).toArgb(), Color(0xFF9F67F5).toArgb(), Shader.TileMode.CLAMP)
                pathEffect = rounded
            }
            canvas.drawPath(path, fill)
        }

        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = unit * 0.022f
            strokeJoin = Paint.Join.ROUND
            pathEffect = rounded
        }
        canvas.drawPath(path, edge)
    }

    private fun centerCrop(photo: Bitmap, targetRatio: Float): Rect {
        val ratio = photo.width.toFloat() / photo.height
        return if (ratio > targetRatio) {
            val cw = (photo.height * targetRatio).toInt()
            val left = (photo.width - cw) / 2
            Rect(left, 0, left + cw, photo.height)
        } else {
            val ch = (photo.width / targetRatio).toInt()
            val top = (photo.height - ch) / 2
            Rect(0, top, photo.width, top + ch)
        }
    }

    // ───────────────────────── text designs ─────────────────────────

    private fun paint(size: Float, face: Typeface, block: TextPaint.() -> Unit = {}) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            block()
        }

    private fun stroke(size: Float, face: Typeface, color: Int, width: Float, fill: Boolean = true) =
        paint(size, face) {
            this.color = color
            style = if (fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    /** Draws [body] centered in [box] with [p], optionally offset. All layers share the same layout. */
    private fun layer(canvas: Canvas, box: RectF, body: String, p: TextPaint, dx: Float = 0f, dy: Float = 0f) {
        val layout = staticLayout(body, p, box.width().toInt().coerceAtLeast(1))
        canvas.save()
        canvas.translate(box.left + dx, box.centerY() - layout.height / 2f + dy)
        layout.draw(canvas)
        canvas.restore()
    }

    /** Big playful white letters, thick purple rim, deep extrusion. */
    private fun drawBubble(canvas: Canvas, box: RectF, lines: List<String>, body: String, face: Typeface, unit: Float) {
        val size = fitTextSize(lines, face, box, unit, rim = 0.30f)
        layer(canvas, box, body, paint(size, face) {
            color = 0x44000000
            setShadowLayer(size * 0.14f, size * 0.05f, size * 0.09f, 0x99000000.toInt())
        })
        val extrusion = stroke(size, face, Color(0xFF3B1C7A).toArgb(), size * 0.26f)
        for (step in 8 downTo 1) layer(canvas, box, body, extrusion, step * size * 0.011f, step * size * 0.018f)
        layer(canvas, box, body, stroke(size, face, Color(0xFF7C3AED).toArgb(), size * 0.26f))
        layer(canvas, box, body, stroke(size, face, Color(0xFFB794F4).toArgb(), size * 0.09f, fill = false))
        layer(canvas, box, body, paint(size, face) { color = android.graphics.Color.WHITE })
    }

    /** Die-cut sticker: purple letters inside a thick white outline. */
    private fun drawSticker(canvas: Canvas, box: RectF, lines: List<String>, body: String, face: Typeface, unit: Float) {
        val size = fitTextSize(lines, face, box, unit, rim = 0.36f)
        layer(canvas, box, body, stroke(size, face, 0x55000000, size * 0.34f).apply {
            maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
        }, 0f, size * 0.06f)
        layer(canvas, box, body, stroke(size, face, android.graphics.Color.WHITE, size * 0.34f))
        layer(canvas, box, body, paint(size, face) { color = Color(0xFF7C3AED).toArgb() })
    }

    /** Candy: pink→purple→blue gradient letters with a white rim. */
    private fun drawCandy(canvas: Canvas, box: RectF, lines: List<String>, body: String, face: Typeface, unit: Float) {
        val size = fitTextSize(lines, face, box, unit, rim = 0.26f)
        layer(canvas, box, body, stroke(size, face, 0x40000000, size * 0.24f).apply {
            maskFilter = BlurMaskFilter(size * 0.1f, BlurMaskFilter.Blur.NORMAL)
        }, 0f, size * 0.07f)
        layer(canvas, box, body, stroke(size, face, android.graphics.Color.WHITE, size * 0.24f))
        val probe = staticLayout(body, paint(size, face), box.width().toInt().coerceAtLeast(1))
        layer(canvas, box, body, paint(size, face) {
            shader = LinearGradient(
                0f, 0f, 0f, probe.height.toFloat(),
                intArrayOf(Color(0xFFFF7AC6).toArgb(), Color(0xFFA855F7).toArgb(), Color(0xFF60A5FA).toArgb()),
                null,
                Shader.TileMode.CLAMP
            )
        })
    }

    /** 70s retro: cream letters over stacked orange / pink / purple offsets. */
    private fun drawRetro(canvas: Canvas, box: RectF, lines: List<String>, body: String, face: Typeface, unit: Float) {
        val size = fitTextSize(lines, face, box, unit, rim = 0.30f)
        val u = size * 0.05f
        val outline = Color(0xFF3B0764).toArgb()
        listOf(Color(0xFF6D28D9), Color(0xFFEC4899), Color(0xFFF97316)).forEachIndexed { i, c ->
            val d = (3 - i) * u
            layer(canvas, box, body, stroke(size, face, outline, size * 0.05f), d, d)
            layer(canvas, box, body, paint(size, face) { color = c.toArgb() }, d, d)
        }
        layer(canvas, box, body, stroke(size, face, outline, size * 0.05f))
        layer(canvas, box, body, paint(size, face) { color = Color(0xFFFFF4DC).toArgb() })
    }

    /** Comic: tilted yellow letters, heavy black outline and hard shadow. */
    private fun drawComic(canvas: Canvas, box: RectF, lines: List<String>, body: String, face: Typeface, unit: Float) {
        val size = fitTextSize(lines, face, box, unit, rim = 0.34f) * 0.94f
        canvas.save()
        canvas.rotate(-5f, box.centerX(), box.centerY())
        val skew: TextPaint.() -> Unit = { textSkewX = -0.14f }
        layer(canvas, box, body, stroke(size, face, android.graphics.Color.BLACK, size * 0.16f).apply(skew), size * 0.07f, size * 0.08f)
        layer(canvas, box, body, stroke(size, face, android.graphics.Color.BLACK, size * 0.16f).apply(skew))
        layer(canvas, box, body, paint(size, face) { color = Color(0xFFFFE14D).toArgb(); textSkewX = -0.14f })
        canvas.restore()
    }

    /** Neon sign: hot-pink glow around a bright white core. */
    private fun drawNeon(canvas: Canvas, box: RectF, lines: List<String>, body: String, face: Typeface, unit: Float) {
        val size = fitTextSize(lines, face, box, unit, rim = 0.30f)
        val pink = Color(0xFFFF3DCB).toArgb()
        layer(canvas, box, body, stroke(size, face, pink, size * 0.16f, fill = false).apply {
            maskFilter = BlurMaskFilter(size * 0.22f, BlurMaskFilter.Blur.NORMAL)
        })
        layer(canvas, box, body, stroke(size, face, pink, size * 0.10f, fill = false).apply {
            maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
        })
        layer(canvas, box, body, stroke(size, face, Color(0xFFFF8FE3).toArgb(), size * 0.06f, fill = false))
        layer(canvas, box, body, paint(size, face) { color = android.graphics.Color.WHITE })
    }

    /** Elegant serif with a small accent quote mark. Dark on light backgrounds, white elsewhere. */
    private fun drawMinimal(canvas: Canvas, box: RectF, lines: List<String>, body: String, unit: Float, bgIsLight: Boolean) {
        val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val size = fitTextSize(lines, serif, box, unit, rim = 0.05f) * 0.85f
        val textColor = if (bgIsLight) Color(0xFF1E1B2E).toArgb() else android.graphics.Color.WHITE
        val probe = staticLayout(body, paint(size, serif), box.width().toInt().coerceAtLeast(1))
        val mark = paint(size * 1.1f, serif) { color = Color(0xFFB57BF5).toArgb(); textAlign = Paint.Align.CENTER }
        canvas.drawText("“", box.centerX(), box.centerY() - probe.height / 2f - size * 0.05f, mark)
        layer(canvas, box, body, paint(size, serif) {
            color = textColor
            if (!bgIsLight) setShadowLayer(size * 0.08f, 0f, size * 0.03f, 0x66000000)
        })
    }

    /** Frosted glass card sized to the text, white letters. */
    private fun drawGlass(canvas: Canvas, box: RectF, lines: List<String>, body: String, face: Typeface, unit: Float) {
        val size = fitTextSize(lines, face, box, unit, rim = 0.8f) * 0.8f
        val probe = staticLayout(body, paint(size, face), box.width().toInt().coerceAtLeast(1))
        var textW = 0f
        for (i in 0 until probe.lineCount) textW = maxOf(textW, probe.getLineWidth(i))
        val padX = size * 0.8f
        val padY = size * 0.6f
        val panel = RectF(
            box.centerX() - textW / 2f - padX,
            box.centerY() - probe.height / 2f - padY,
            box.centerX() + textW / 2f + padX,
            box.centerY() + probe.height / 2f + padY
        )
        val radius = size * 0.7f
        canvas.drawRoundRect(panel, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33000000
            maskFilter = BlurMaskFilter(size * 0.3f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawRoundRect(panel, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(panel.left, panel.top, panel.right, panel.bottom, 0x66FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP)
        })
        canvas.drawRoundRect(panel, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = unit * 0.004f
        })
        layer(canvas, box, body, paint(size, face) {
            color = android.graphics.Color.WHITE
            setShadowLayer(size * 0.06f, 0f, size * 0.02f, 0x55000000)
        })
    }

    private fun drawWatermark(canvas: Canvas, w: Int, h: Int, unit: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            textSize = unit * 0.026f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Lumen", w - unit * 0.045f, h - unit * 0.045f, paint)
    }

    // ───────────────────────── layout helpers ─────────────────────────

    /**
     * Measures real text width so short quotes fill the space.
     * [rim] = extra width the design adds around letters, as a fraction of text size.
     */
    private fun fitTextSize(lines: List<String>, face: Typeface, box: RectF, unit: Float, rim: Float): Float {
        val probe = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = 100f
        }
        val widest = lines.maxOf { probe.measureText(it) }.coerceAtLeast(1f)
        // width(size) = widest*size/100 + rim*size  ≤ box width
        val byWidth = box.width() * 0.94f / (widest / 100f + rim)
        val byHeight = box.height() * 0.9f / (lines.size * 1.22f + rim)
        return minOf(byWidth, byHeight).coerceIn(unit * 0.045f, unit * 0.28f)
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
        // Avoid a lonely one-word last line when possible.
        if (lines.size >= 2 && !lines.last().contains(' ')) {
            val prev = lines[lines.size - 2].split(' ')
            if (prev.size >= 2) {
                val moved = prev.last()
                val merged = "$moved ${lines.last()}"
                if (merged.length <= maxChars) {
                    lines[lines.size - 2] = prev.dropLast(1).joinToString(" ")
                    lines[lines.size - 1] = merged
                }
            }
        }
        return lines.ifEmpty { listOf(text) }
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()
}

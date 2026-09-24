package com.lumen.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconCache(private val context: Context) {

    private val cache = LruCache<String, ImageBitmap>(256)

    suspend fun get(packageName: String, activityName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = "$packageName/$activityName#v9"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            val bitmap = (adaptive?.let { renderDimensional(it, SIZE) } ?: renderSculpted(drawable, SIZE)).asImageBitmap()
            cache.put(key, bitmap)
            bitmap
        }
    }

    /**
     * White Glass icon: a white symbol on a frosted tile. Very colorful logos
     * (Play Store, Chrome…) keep their colors on the glass instead of becoming a white blob.
     */
    suspend fun getFrosted(packageName: String, activityName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = "$packageName/$activityName#frost1"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            val bitmap = runCatching { renderFrosted(drawable, adaptive, SIZE) }
                .getOrElse { (adaptive?.let { renderDimensional(it, SIZE) } ?: renderSculpted(drawable, SIZE)) }
                .asImageBitmap()
            cache.put(key, bitmap)
            bitmap
        }
    }

    private fun loadDrawable(packageName: String, activityName: String): Drawable? {
        val pm = context.packageManager
        return try {
            if (activityName.isNotBlank()) {
                pm.getActivityIcon(ComponentName(packageName, activityName))
            } else {
                pm.getApplicationIcon(packageName)
            }
        } catch (_: Exception) {
            runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
        }
    }

    private fun loadAdaptive(packageName: String, activityName: String): AdaptiveIconDrawable? {
        if (Build.VERSION.SDK_INT < 26) return null
        val pm = context.packageManager
        return try {
            if (activityName.isNotBlank()) {
                val info = pm.getActivityInfo(ComponentName(packageName, activityName), 0)
                info.loadUnbadgedIcon(pm) as? AdaptiveIconDrawable
            } else {
                null
            } ?: pm.getApplicationIcon(packageName) as? AdaptiveIconDrawable
        } catch (_: Exception) {
            null
        }
    }

    private fun renderDimensional(icon: AdaptiveIconDrawable, size: Int): Bitmap {
        val extra = (size * 18f / 72f).toInt()
        val pop = (size * 0.06f).toInt()
        val bgBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fgBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bgBmp).let { canvas ->
            icon.background?.setBounds(-extra, -extra, size + extra, size + extra)
            icon.background?.draw(canvas)
        }
        Canvas(fgBmp).let { canvas ->
            icon.foreground?.setBounds(-extra - pop, -extra - pop, size + extra + pop, size + extra + pop)
            icon.foreground?.draw(canvas)
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        canvas.clipPath(squircle(size))
        canvas.drawBitmap(bgBmp, 0f, 0f, null)
        val shadow = fgBmp.extractAlpha()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x3A000000
            maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(shadow, 0f, size * 0.04f, shadowPaint)
        canvas.drawBitmap(fgBmp, 0f, -size * 0.02f, null)
        drawGlass(canvas, size)
        canvas.restore()
        bgBmp.recycle()
        fgBmp.recycle()
        shadow.recycle()
        return out
    }

    private fun renderSculpted(drawable: Drawable, size: Int): Bitmap {
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(src).let { canvas ->
            val padded = drawable.mutate()
            padded.setBounds(0, 0, size, size)
            padded.draw(canvas)
        }
        val filled = fillOpaque(src, size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        canvas.clipPath(squircle(size))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(filled, 0f, 0f, paint)
        val alpha = filled.extractAlpha()
        val depth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22000000
            maskFilter = BlurMaskFilter(size * 0.05f, BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawBitmap(alpha, 0f, size * 0.035f, depth)
        canvas.drawBitmap(filled, 0f, -size * 0.008f, paint)
        drawGlass(canvas, size)
        canvas.restore()
        if (filled !== src) filled.recycle()
        src.recycle()
        alpha.recycle()
        return out
    }

    private fun fillOpaque(src: Bitmap, size: Int): Bitmap {
        val inset = opaqueInset(src)
        if (inset < size * 0.04f) return src
        val inner = size - 2 * inset
        if (inner <= 0) return src
        val filled = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(filled)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = size.toFloat() / inner
        canvas.scale(scale, scale, size / 2f, size / 2f)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return filled
    }

    private fun opaqueInset(src: Bitmap): Int {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((pixels[row + x] ushr 24) < 24) continue
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX) return 0
        return minOf(minX, minY, w - 1 - maxX, h - 1 - maxY).coerceAtLeast(0)
    }

    private fun drawGlass(canvas: Canvas, size: Int) {
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.012f
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(0x55FFFFFF.toInt(), 0x14FFFFFF, 0x1A000000),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val inset = size * 0.012f
        canvas.drawRoundRect(inset, inset, size - inset, size - inset, size * 0.30f, size * 0.30f, rim)
    }

    // ───────────────────────── White Glass icons ─────────────────────────

    private fun renderFrosted(drawable: Drawable, adaptive: AdaptiveIconDrawable?, size: Int): Bitmap {
        var glyph: Bitmap? = null
        var colored: Bitmap? = null
        var coloredIsFullIcon = false

        // 1) Android 13+ themed (monochrome) icon: the app's own one-color symbol.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            adaptive?.monochrome?.let { glyph = tintWhite(renderLayer(it, size)) }
        }
        // 2) The logo layer of a two-layer (adaptive) icon.
        if (glyph == null && adaptive?.foreground != null) {
            val fg = renderLayer(adaptive.foreground, size)
            val st = analyze(fg, opaqueOnly = true)
            when {
                st.coverage > 0.62f -> Unit                  // artwork fills the tile: treat like a flat icon below
                st.colorful -> colored = fg                  // multicolor logo: keep its colors
                st.dominantShare >= 0.85f -> glyph = tintWhite(fg)
                else -> glyph = contrastGlyph(fg, st.dominant, scale = 1f)
                    .takeIf { coverageOf(it) > 0.015f } ?: tintWhite(fg)
            }
        }
        // 3) Flat single-picture icon: separate the symbol from its plain background.
        if (glyph == null && colored == null) {
            val full = adaptive?.let { renderDimensional(it, size) } ?: renderSculpted(drawable, size)
            val st = analyze(full, opaqueOnly = true)
            val candidate = if (!st.colorful) contrastGlyph(full, st.dominant, scale = 0.78f) else null
            val cov = candidate?.let { coverageOf(it) } ?: 0f
            if (candidate != null && cov in 0.02f..0.5f) {
                glyph = candidate
            } else {
                colored = full
                coloredIsFullIcon = true
            }
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        canvas.clipPath(squircle(size))
        // Frosted white tile
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(0x66FFFFFF, 0x38FFFFFF, 0x2EFFFFFF),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        })
        // Soft top sheen
        canvas.drawRect(0f, 0f, size.toFloat(), size * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, size * 0.5f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })

        val g = glyph
        val c = colored
        if (g != null) {
            val shadow = g.extractAlpha()
            canvas.drawBitmap(shadow, 0f, size * 0.022f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x40000000
                maskFilter = BlurMaskFilter(size * 0.03f, BlurMaskFilter.Blur.NORMAL)
            })
            canvas.drawBitmap(g, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            shadow.recycle()
        } else if (c != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (coloredIsFullIcon) {
                // Whole colorful icon, smaller, sitting on the glass.
                val inset = size * 0.14f
                canvas.save()
                canvas.clipPath(squircle(size, inset))
                canvas.drawBitmap(c, null, RectF(inset, inset, size - inset, size - inset), paint)
                canvas.restore()
            } else {
                canvas.drawBitmap(c, 0f, 0f, paint)
            }
        }
        drawGlass(canvas, size)
        canvas.restore()
        return out
    }

    /** Draws one adaptive-icon layer the same way the normal icon does (72 of 108 units visible). */
    private fun renderLayer(layer: Drawable, size: Int): Bitmap {
        val extra = (size * 18f / 72f).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val d = layer.mutate()
        d.setBounds(-extra, -extra, size + extra, size + extra)
        d.draw(Canvas(bmp))
        return bmp
    }

    /** Same shape, filled white. */
    private fun tintWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        })
        return out
    }

    private class IconStats(val coverage: Float, val dominant: Int, val dominantShare: Float, val colorful: Boolean)

    /** Coverage, most common color and whether the pixels span several strong hues. */
    private fun analyze(src: Bitmap, opaqueOnly: Boolean): IconStats {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val bins = HashMap<Int, Int>()
        val hueBins = IntArray(12)
        var opaque = 0
        val hsv = FloatArray(3)
        for (c in px) {
            if (opaqueOnly && (c ushr 24) < 128) continue
            opaque++
            val key = ((c shr 20) and 0xF shl 8) or ((c shr 12) and 0xF shl 4) or ((c shr 4) and 0xF)
            bins[key] = (bins[key] ?: 0) + 1
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[1] > 0.35f && hsv[2] > 0.25f) hueBins[((hsv[0] / 30f).toInt()).coerceIn(0, 11)]++
        }
        if (opaque == 0) return IconStats(0f, android.graphics.Color.WHITE, 1f, false)
        val top = bins.maxByOrNull { it.value }!!
        val k = top.key
        val dominant = android.graphics.Color.rgb(((k shr 8) and 0xF) * 17, ((k shr 4) and 0xF) * 17, (k and 0xF) * 17)
        val strongHues = hueBins.count { it > opaque * 0.06f }
        return IconStats(
            coverage = opaque.toFloat() / (w * h),
            dominant = dominant,
            dominantShare = top.value.toFloat() / opaque,
            colorful = strongHues >= 3
        )
    }

    /** White symbol made of every pixel that clearly differs from [background]. */
    private fun contrastGlyph(src: Bitmap, background: Int, scale: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val br = android.graphics.Color.red(background)
        val bg = android.graphics.Color.green(background)
        val bb = android.graphics.Color.blue(background)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) {
                px[i] = 0
                continue
            }
            val dr = android.graphics.Color.red(c) - br
            val dg = android.graphics.Color.green(c) - bg
            val db = android.graphics.Color.blue(c) - bb
            val dist = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
            val strength = ((dist - 48f) / 70f).coerceIn(0f, 1f)
            val outA = (strength * a).toInt()
            px[i] = (outA shl 24) or 0xFFFFFF
        }
        val glyph = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        glyph.setPixels(px, 0, w, 0, 0, w, h)
        if (scale >= 0.999f) return glyph
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inset = w * (1f - scale) / 2f
        Canvas(out).drawBitmap(glyph, null, RectF(inset, inset, w - inset, h - inset), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        glyph.recycle()
        return out
    }

    private fun coverageOf(bmp: Bitmap): Float {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return px.count { (it ushr 24) > 100 }.toFloat() / px.size
    }

    private fun squircle(size: Int, inset: Float = 0f): Path {
        val radius = (size - 2f * inset) * 0.30f
        return Path().apply {
            addRoundRect(
                RectF(inset, inset, size - inset, size - inset),
                radius,
                radius,
                Path.Direction.CW
            )
        }
    }

    private companion object {
        const val SIZE = 256
    }
}

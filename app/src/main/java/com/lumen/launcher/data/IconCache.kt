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

    suspend fun getCrystal(packageName: String, activityName: String, strength: Float = 1f): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = "$packageName/$activityName#crystal1-$strength"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            val bitmap = renderFrostedColor(drawable, adaptive, SIZE, strength,
                stripMapsPlate = packageName == "com.google.android.apps.maps", crystal = true).asImageBitmap()
            cache.put(key, bitmap)
            bitmap
        }
    }

    suspend fun getTinted(packageName: String, activityName: String, strength: Float = 1f): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = "$packageName/$activityName#tinted1-$strength"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            val label = loadLabel(packageName, activityName)
            val bitmap = runCatching { renderFrosted(drawable, adaptive, SIZE, label, strength = strength, tinted = true) }
                .getOrElse { renderFrosted(drawable, null, SIZE, label, letterOnly = true, strength = strength, tinted = true) }
                .asImageBitmap()
            cache.put(key, bitmap)
            bitmap
        }
    }

    suspend fun getIos(packageName: String, activityName: String, strength: Float = 1f): ImageBitmap? {
        val key = "$packageName/$activityName#ios1-$strength"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            renderIos(drawable, adaptive, packageName, strength).asImageBitmap().also { cache.put(key, it) }
        }
    }

    private fun renderIos(drawable: Drawable, adaptive: AdaptiveIconDrawable?, packageName: String, strength: Float): Bitmap {
        val size = SIZE.toFloat()
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, size, size)
        val path = Path().apply { addRoundRect(bounds, size * .22f, size * .22f, Path.Direction.CW) }
        canvas.clipPath(path)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val kind = when (packageName) {
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer" -> "phone"
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging" -> "messages"
            "com.google.android.gm", "com.android.email", "com.samsung.android.email.provider" -> "mail"
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2", "com.sec.android.app.camera" -> "camera"
            else -> "original"
        }
        if (kind == "original") {
            paint.color = android.graphics.Color.WHITE; canvas.drawRect(bounds, paint)
            if (adaptive != null) {
                canvas.drawBitmap(renderLayer(adaptive.background, SIZE), 0f, 0f, paint)
                val foreground = renderLayer(adaptive.foreground, SIZE)
                canvas.drawBitmap(foreground, null, RectF(-size * .06f, -size * .06f, size * 1.06f, size * 1.06f), paint)
            } else {
                val source = renderLayer(drawable, SIZE)
                canvas.drawBitmap(source, null, bounds, paint)
            }
        } else {
            val colors = when (kind) {
                "mail" -> intArrayOf(0xFF5AC8FA.toInt(), 0xFF087AFF.toInt())
                "camera" -> intArrayOf(0xFFF0F1F4.toInt(), 0xFFB9BDC6.toInt())
                else -> intArrayOf(0xFF65E875.toInt(), 0xFF16BA52.toInt())
            }
            paint.shader = LinearGradient(0f, 0f, 0f, size, colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, paint); paint.shader = null
            paint.color = if (kind == "camera") 0xFF292B31.toInt() else android.graphics.Color.WHITE
            when (kind) {
                "messages" -> {
                    canvas.drawOval(RectF(size * .18f, size * .22f, size * .82f, size * .71f), paint)
                    canvas.drawPath(Path().apply { moveTo(size * .29f, size * .60f); lineTo(size * .24f, size * .79f); lineTo(size * .47f, size * .67f); close() }, paint)
                }
                "mail" -> {
                    canvas.drawRoundRect(RectF(size * .16f, size * .27f, size * .84f, size * .73f), size * .04f, size * .04f, paint)
                    paint.color = 0xFF168AFF.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .025f
                    canvas.drawPath(Path().apply { moveTo(size * .18f, size * .30f); lineTo(size * .50f, size * .54f); lineTo(size * .82f, size * .30f) }, paint)
                }
                "camera" -> {
                    canvas.drawRoundRect(RectF(size * .15f, size * .32f, size * .85f, size * .76f), size * .055f, size * .055f, paint)
                    canvas.drawRoundRect(RectF(size * .32f, size * .25f, size * .61f, size * .36f), size * .03f, size * .03f, paint)
                    paint.color = 0xFFD8DCE3.toInt(); canvas.drawCircle(size * .50f, size * .54f, size * .17f, paint)
                    paint.color = 0xFF303C52.toInt(); canvas.drawCircle(size * .50f, size * .54f, size * .125f, paint)
                    paint.color = 0xFF6C9DD3.toInt(); canvas.drawCircle(size * .46f, size * .50f, size * .035f, paint)
                }
                "phone" -> {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .115f; paint.strokeCap = Paint.Cap.ROUND
                    canvas.drawPath(Path().apply { moveTo(size * .31f, size * .29f); cubicTo(size * .20f, size * .47f, size * .50f, size * .80f, size * .71f, size * .67f) }, paint)
                    canvas.drawLine(size * .29f, size * .29f, size * .38f, size * .39f, paint)
                    canvas.drawLine(size * .62f, size * .60f, size * .72f, size * .67f, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT
        paint.shader = LinearGradient(0f, 0f, 0f, size, intArrayOf(((strength.coerceIn(0f, 2f) * 18).toInt() shl 24) or 0xFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        return bitmap
    }

    suspend fun get(packageName: String, activityName: String, strength: Float = 1f): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = "$packageName/$activityName#v10-" + strength
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            val bitmap = (adaptive?.let { renderDimensional(it, SIZE, strength) } ?: renderSculpted(drawable, SIZE, strength)).asImageBitmap()
            cache.put(key, bitmap)
            bitmap
        }
    }

    /**
     * White Glass icon: a white symbol on a frosted tile. Very colorful logos
     * (Play Store, Chrome…) keep their colors on the glass instead of becoming a white blob.
     */
    suspend fun getFrosted(packageName: String, activityName: String, strength: Float = 1f): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = "$packageName/$activityName#frost4-" + strength
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            val label = loadLabel(packageName, activityName)
            // If converting ever fails, fall back to the white first-letter tile — never a colored icon.
            val bitmap = runCatching { renderFrosted(drawable, adaptive, SIZE, label, strength = strength) }
                .recoverCatching { renderFrosted(drawable, null, SIZE, label, letterOnly = true, strength = strength) }
                .getOrElse { renderSculpted(drawable, SIZE, strength) }
                .asImageBitmap()
            cache.put(key, bitmap)
            bitmap
        }
    }

    /** White Glass · Color icons: the app's colored logo sitting on a frosted white tile. */
    suspend fun getFrostedColor(packageName: String, activityName: String, strength: Float = 1f): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = "$packageName/$activityName#frostcolor6-" + strength
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val drawable = loadDrawable(packageName, activityName) ?: return@withContext null
            val adaptive = loadAdaptive(packageName, activityName) ?: drawable as? AdaptiveIconDrawable
            val bitmap = runCatching {
                renderFrostedColor(drawable, adaptive, SIZE, strength, stripMapsPlate = packageName == "com.google.android.apps.maps")
            }
                .getOrElse { adaptive?.let { renderDimensional(it, SIZE, strength) } ?: renderSculpted(drawable, SIZE, strength) }
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

    private fun renderDimensional(icon: AdaptiveIconDrawable, size: Int, strength: Float = 1f): Bitmap {
        val extra = (size * 18f / 72f).toInt()
        val pop = (size * 0.06f * strength).toInt()
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
            color = glassAlpha(0x3A000000, strength)
            maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(shadow, 0f, size * 0.04f * strength, shadowPaint)
        canvas.drawBitmap(fgBmp, 0f, -size * 0.02f * strength, null)
        drawGlass(canvas, size, strength)
        canvas.restore()
        bgBmp.recycle()
        fgBmp.recycle()
        shadow.recycle()
        return out
    }

    private fun renderSculpted(drawable: Drawable, size: Int, strength: Float = 1f): Bitmap {
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
            color = glassAlpha(0x22000000, strength)
            maskFilter = BlurMaskFilter(size * 0.05f, BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawBitmap(alpha, 0f, size * 0.035f * strength, depth)
        canvas.drawBitmap(filled, 0f, -size * 0.008f * strength, paint)
        drawGlass(canvas, size, strength)
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

    private fun glassAlpha(color: Int, strength: Float): Int =
        (((color ushr 24) * strength).toInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    private fun drawGlass(canvas: Canvas, size: Int, strength: Float = 1f) {
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.012f
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(glassAlpha(0x55FFFFFF, strength), glassAlpha(0x14FFFFFF, strength), glassAlpha(0x1A000000, strength)),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val inset = size * 0.012f
        canvas.drawRoundRect(inset, inset, size - inset, size - inset, size * 0.30f, size * 0.30f, rim)
    }

    // ───────────────────────── White Glass icons ─────────────────────────

    /** Every icon becomes a white symbol on frosted glass — no colored logos. */
    private fun renderFrosted(
        drawable: Drawable,
        adaptive: AdaptiveIconDrawable?,
        size: Int,
        label: String,
        letterOnly: Boolean = false,
        strength: Float = 1f,
        tinted: Boolean = false
    ): Bitmap {
        var glyph: Bitmap? = if (letterOnly) monogram(label, size) else null

        // 1) The app's official one-color (themed) icon, Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            adaptive?.monochrome?.let { mono -> glyph = monochromeGlyph(mono, size) }
        }
        // 2) The logo layer of a two-layer icon.
        if (glyph == null && adaptive?.foreground != null) {
            val fg = renderLayer(adaptive.foreground, size)
            val st = analyze(fg, opaqueOnly = true)
            if (st.coverage in 0.02f..0.6f) {
                glyph = if (st.dominantShare >= 0.40f) {
                    // One main color: if something sits on it (a letter on a circle), keep just that;
                    // if not, it's a one-color logo, so use its whole shape.
                    whiteSymbol(fg, background = st.dominant, scale = 1f)
                        .takeIf { coverageOf(it) > 0.015f } ?: whiteSymbol(fg, background = null, scale = 1f)
                } else {
                    // Multicolor logo: white shape with thin cuts where the colors meet.
                    whiteSymbol(fg, background = null, scale = 1f)
                }
            }
            fg.recycle()
        }
        // 3) Flat icon: lift the logo off its plain background.
        if (glyph == null && !letterOnly) {
            val full = adaptive?.let { renderDimensional(it, size) } ?: renderSculpted(drawable, size)
            val st = analyze(full, opaqueOnly = true)
            if (st.dominantShare >= 0.30f) {
                val candidate = whiteSymbol(full, background = st.dominant, scale = 0.80f)
                if (coverageOf(candidate) in 0.02f..0.55f) glyph = candidate
            }
            full.recycle()
        }
        // 4) Pure artwork with no clear logo: the app's first letter.
        val g = glyph ?: monogram(label, size)

        val tile = if (tinted) tintedTile(g, size, strength) else frostedTile(g, size, strength)
        g.recycle()
        return tile
    }

    private fun tintedTile(glyph: Bitmap, size: Int, strength: Float): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        canvas.clipPath(squircle(size))
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, size.toFloat(),
                intArrayOf(0xFF353C40.toInt(), 0xFF121B20.toInt()), null, Shader.TileMode.CLAMP)
        })
        canvas.drawBitmap(glyph, null, RectF(size * 0.06f, size * 0.06f, size * 0.94f, size * 0.94f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.PorterDuffColorFilter(0xFFE4CB91.toInt(), PorterDuff.Mode.SRC_IN)
            })
        drawGlass(canvas, size, strength)
        canvas.restore()
        return out
    }

    /**
     * White symbol from a colored image.
     * [background] = null: the whole shape turns white. Otherwise only pixels that clearly
     * differ from that color are kept (logo lifted off its plate / background).
     * Where two different colors meet inside the symbol, a thin gap is cut so multicolor
     * logos (Chrome, Play Store) keep their segments instead of becoming a blob.
     */
    private fun whiteSymbol(src: Bitmap, background: Int?, scale: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val alpha = IntArray(w * h)
        for (i in px.indices) {
            val a = px[i] ushr 24
            alpha[i] = if (a == 0) 0 else if (background == null) a else {
                val d = colorDistance(px[i], background)
                (((d - 48f) / 70f).coerceIn(0f, 1f) * a).toInt()
            }
        }
        val out = IntArray(w * h)
        val gap = maxOf(2, w / 110)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val a = alpha[i]
                if (a == 0) continue
                var maxD = 0f
                if (x + gap < w && alpha[i + gap] > 60) maxD = maxOf(maxD, colorDistance(px[i], px[i + gap]))
                if (x - gap >= 0 && alpha[i - gap] > 60) maxD = maxOf(maxD, colorDistance(px[i], px[i - gap]))
                if (y + gap < h && alpha[i + gap * w] > 60) maxD = maxOf(maxD, colorDistance(px[i], px[i + gap * w]))
                if (y - gap >= 0 && alpha[i - gap * w] > 60) maxD = maxOf(maxD, colorDistance(px[i], px[i - gap * w]))
                val cut = ((maxD - 90f) / 60f).coerceIn(0f, 1f)
                val outA = (a * (1f - 0.9f * cut)).toInt()
                out[i] = (outA shl 24) or 0xFFFFFF
            }
        }
        val glyph = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        glyph.setPixels(out, 0, w, 0, 0, w, h)
        if (scale >= 0.999f) return glyph
        val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inset = w * (1f - scale) / 2f
        Canvas(scaled).drawBitmap(glyph, null, RectF(inset, inset, w - inset, h - inset), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        glyph.recycle()
        return scaled
    }

    private fun colorDistance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    /** Last resort: the app's first letter in white, in Lumen's font. */
    private fun monogram(label: String, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val letter = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = size * 0.48f
            textAlign = Paint.Align.CENTER
            typeface = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, com.lumen.launcher.R.font.outfit_semibold) }
                .getOrNull() ?: android.graphics.Typeface.DEFAULT_BOLD
        }
        val fm = paint.fontMetrics
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        Canvas(out).drawText(letter, size / 2f, y, paint)
        return out
    }

    private fun loadLabel(packageName: String, activityName: String): String = runCatching {
        val pm = context.packageManager
        if (activityName.isNotBlank()) {
            pm.getActivityInfo(ComponentName(packageName, activityName), 0).loadLabel(pm).toString()
        } else {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }
    }.getOrDefault(packageName.substringAfterLast('.'))

    /**
     * White Glass · Color icons: the app's colored logo on a frosted glass tile
     * (like Play Store's triangle or Affirm's blue "a" sitting on glass).
     * If the logo itself is white (its color lives in the background layer, e.g. WhatsApp),
     * it is painted in that background color so it stays colorful.
     */
    private fun renderFrostedColor(drawable: Drawable, adaptive: AdaptiveIconDrawable?, size: Int, strength: Float = 1f, stripMapsPlate: Boolean = false, crystal: Boolean = false): Bitmap {
        var logo: Bitmap? = null
        // 1) Two-layer icon: the logo layer.
        if (adaptive?.foreground != null) {
            val fg = renderLayer(adaptive.foreground, size)
            if (analyze(fg, opaqueOnly = true).coverage in 0.02f..0.6f) {
                // White is often the logo itself. Only Maps needs its pale plate removed.
                val cleaned = if (stripMapsPlate) stripPaleBackground(fg) else fg
                logo = if (isPale(cleaned)) {
                    val bgColor = adaptive.background?.let { layerColor(it, size) }
                    if (bgColor != null) recolor(cleaned, bgColor) else cleaned
                } else {
                    cleaned
                }
                if (cleaned !== fg) fg.recycle()
            } else {
                fg.recycle()
            }
        }
        if (logo == null) {
            val full = adaptive?.let { renderDimensional(it, size) } ?: renderSculpted(drawable, size)
            val st = analyze(full, opaqueOnly = true)
            // 2) Flat icon on a plain background: lift the logo off it (white logo takes the background color).
            if (st.dominantShare >= 0.30f) {
                val candidate = colorSymbol(full, st.dominant, scale = 0.80f)
                if (coverageOf(candidate) in 0.02f..0.55f) {
                    logo = if (isPale(candidate) && !isPaleColor(st.dominant)) recolor(candidate, st.dominant) else candidate
                } else {
                    candidate.recycle()
                }
            }
            // 3) Pure artwork: inset on glass, but still strip a white plate if present (Maps).
            if (logo == null) {
                val cleaned = if (stripMapsPlate) stripPaleBackground(full) else full
                val mini = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(mini)
                val inset = size * 0.16f
                c.clipPath(squircle(size, inset))
                c.drawBitmap(cleaned, null, RectF(inset, inset, size - inset, size - inset), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                if (cleaned !== full) cleaned.recycle()
                logo = mini
            }
            full.recycle()
        }
        val tile = if (crystal) crystalTile(logo!!, size, strength) else frostedTile(logo!!, size, strength)
        logo.recycle()
        return tile
    }

    private fun crystalTile(content: Bitmap, size: Int, strength: Float): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        canvas.clipPath(squircle(size))
        val depth = strength.coerceIn(0f, 2f)
        // Keep the wallpaper visible. Reflections belong to the tile, below the logo.
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(glassAlpha(0x30FFFFFF, depth), glassAlpha(0x08FFFFFF, depth), glassAlpha(0x0CFFFFFF, depth)),
                floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        })
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size * 0.7f, size.toFloat(),
                intArrayOf(0x00FFFFFF, glassAlpha(0x28FFFFFF, depth), 0x00FFFFFF),
                floatArrayOf(0.18f, 0.24f, 0.38f), Shader.TileMode.CLAMP)
        })
        val raised = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(raised).drawBitmap(content, null,
            RectF(size * 0.05f, size * 0.025f, size * 0.95f, size * 0.925f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val shadow = raised.extractAlpha()
        canvas.drawBitmap(shadow, 0f, size * 0.025f * depth, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glassAlpha(0x35000000, depth)
            maskFilter = BlurMaskFilter(size * 0.035f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawBitmap(raised, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        drawGlass(canvas, size, depth)
        shadow.recycle()
        raised.recycle()
        canvas.restore()
        return out
    }

    /**
     * Removes near-white / light-grey plate pixels so logos like Maps keep only the
     * colorful mark when placed on a frosted White Glass · Color tile.
     */
    private fun stripPaleBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var opaque = 0
        var pale = 0
        for (c in px) {
            if ((c ushr 24) < 128) continue
            opaque++
            if (isPaleColor(c)) pale++
        }
        // Only strip when a clear pale plate dominates (e.g. Maps white square).
        if (opaque == 0 || pale < opaque * 0.35f) return src
        // Keep the original artwork if cleanup would leave an empty or tiny mark.
        if (opaque - pale < px.size * 0.02f) return src
        for (i in px.indices) {
            val c = px[i]
            if ((c ushr 24) < 16) continue
            if (isPaleColor(c)) {
                px[i] = 0
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Keeps the original colors of every pixel that clearly differs from [background]. */
    private fun colorSymbol(src: Bitmap, background: Int, scale: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            val keep = ((colorDistance(c, background) - 48f) / 70f).coerceIn(0f, 1f)
            px[i] = ((keep * a).toInt() shl 24) or (c and 0xFFFFFF)
        }
        val symbol = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        symbol.setPixels(px, 0, w, 0, 0, w, h)
        if (scale >= 0.999f) return symbol
        val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inset = w * (1f - scale) / 2f
        Canvas(scaled).drawBitmap(symbol, null, RectF(inset, inset, w - inset, h - inset), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        symbol.recycle()
        return scaled
    }

    /** True when most visible pixels are white / very light grey (a white logo). */
    private fun isPale(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var opaque = 0
        var pale = 0
        for (c in px) {
            if ((c ushr 24) < 128) continue
            opaque++
            if (isPaleColor(c)) pale++
        }
        return opaque > 0 && pale > opaque * 0.75f
    }

    private fun isPaleColor(c: Int): Boolean {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        return hsv[1] < 0.2f && hsv[2] > 0.8f
    }

    /** Main color of an icon layer (e.g. WhatsApp's green background). Null if it's pale too. */
    private fun layerColor(layer: Drawable, size: Int): Int? {
        val bmp = renderLayer(layer, size)
        val st = analyze(bmp, opaqueOnly = true)
        bmp.recycle()
        return st.dominant.takeUnless { isPaleColor(it) }
    }

    /** Same shape, filled with [color]. */
    private fun recolor(src: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
        })
        src.recycle()
        return out
    }

    /** Frosted white tile with [content] on top (soft shadow, glass rim). */
    private fun frostedTile(content: Bitmap, size: Int, strength: Float): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        canvas.clipPath(squircle(size))
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(glassAlpha(0x66FFFFFF, strength), glassAlpha(0x38FFFFFF, strength), glassAlpha(0x2EFFFFFF, strength)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        })
        canvas.drawRect(0f, 0f, size.toFloat(), size * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, size * 0.5f, glassAlpha(0x2EFFFFFF, strength), 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        val shadow = content.extractAlpha()
        canvas.drawBitmap(shadow, 0f, size * 0.022f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glassAlpha(0x40000000, strength)
            maskFilter = BlurMaskFilter(size * 0.03f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawBitmap(content, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        shadow.recycle()
        drawGlass(canvas, size, strength)
        canvas.restore()
        return out
    }

    /**
     * White version of a themed icon. Some makers (e.g. Samsung) size this layer differently;
     * if the symbol runs into the tile edges it is redrawn smaller so it isn't cut off.
     */
    private fun monochromeGlyph(mono: Drawable, size: Int): Bitmap {
        val standard = tintWhite(renderLayer(mono, size))
        if (!touchesEdges(standard)) return standard
        val fitted = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val d = mono.mutate()
        val inset = (size * 0.2f).toInt()
        d.setBounds(inset, inset, size - inset, size - inset)
        d.draw(Canvas(fitted))
        return tintWhite(fitted)
    }

    private fun touchesEdges(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        val margin = (w * 0.04f).toInt().coerceAtLeast(2)
        var hits = 0
        for (i in 0 until w step 2) {
            if ((bmp.getPixel(i, margin) ushr 24) > 100) hits++
            if ((bmp.getPixel(i, h - 1 - margin) ushr 24) > 100) hits++
        }
        for (j in 0 until h step 2) {
            if ((bmp.getPixel(margin, j) ushr 24) > 100) hits++
            if ((bmp.getPixel(w - 1 - margin, j) ushr 24) > 100) hits++
        }
        return hits > 6
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

    private class IconStats(
        val coverage: Float,
        val dominant: Int,
        val dominantShare: Float,
        val colorful: Boolean,
        /** Almost no saturated pixels: a black / white / grey icon. */
        val grayscale: Boolean
    )

    /** Coverage, most common color and whether the pixels span several strong hues. */
    private fun analyze(src: Bitmap, opaqueOnly: Boolean): IconStats {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val bins = HashMap<Int, Int>()
        val hueBins = IntArray(12)
        var opaque = 0
        var saturated = 0
        val hsv = FloatArray(3)
        for (c in px) {
            if (opaqueOnly && (c ushr 24) < 128) continue
            opaque++
            val key = ((c shr 20) and 0xF shl 8) or ((c shr 12) and 0xF shl 4) or ((c shr 4) and 0xF)
            bins[key] = (bins[key] ?: 0) + 1
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[1] > 0.35f && hsv[2] > 0.25f) hueBins[((hsv[0] / 30f).toInt()).coerceIn(0, 11)]++
            if (hsv[1] > 0.25f && hsv[2] > 0.2f) saturated++
        }
        if (opaque == 0) return IconStats(0f, android.graphics.Color.WHITE, 1f, false, true)
        val top = bins.maxByOrNull { it.value }!!
        val k = top.key
        val dominant = android.graphics.Color.rgb(((k shr 8) and 0xF) * 17, ((k shr 4) and 0xF) * 17, (k and 0xF) * 17)
        val strongHues = hueBins.count { it > opaque * 0.06f }
        return IconStats(
            coverage = opaque.toFloat() / (w * h),
            dominant = dominant,
            dominantShare = top.value.toFloat() / opaque,
            colorful = strongHues >= 3,
            grayscale = saturated < opaque * 0.08f
        )
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

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

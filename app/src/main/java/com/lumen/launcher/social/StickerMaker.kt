package com.lumen.launcher.social

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Turns any transparent-background creation into a sticker:
 * trimmed to its content, wrapped in a die-cut border, centered in a
 * 512x512 transparent square, encoded as WebP (the WhatsApp/Telegram sticker format).
 * Fully offline; no AI.
 */
object StickerMaker {
    const val SIZE = 512
    private const val MARGIN = 16
    private const val BORDER = 10f
    private const val MAX_BYTES = 100 * 1024

    fun make(source: Bitmap): Bitmap {
        val trimmed = trim(source)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val room = SIZE - 2 * (MARGIN + BORDER)
        val scale = min(room / trimmed.width, room / trimmed.height)
        val w = trimmed.width * scale
        val h = trimmed.height * scale
        val dst = RectF((SIZE - w) / 2f, (SIZE - h) / 2f, (SIZE + w) / 2f, (SIZE + h) / 2f)

        // Die-cut border: stamp the content's alpha mask in a ring around it.
        val mask = Bitmap.createScaledBitmap(trimmed, w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), true)
            .extractAlpha()
        val borderColor = if (edgeIsLight(trimmed)) 0xFF6D28D9.toInt() else 0xFFFFFFFF.toInt()

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
        stampRing(canvas, mask, dst.left, dst.top + 4f, BORDER, shadow)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor }
        stampRing(canvas, mask, dst.left, dst.top, BORDER, border)

        canvas.drawBitmap(trimmed, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun encodeWebp(sticker: Bitmap): ByteArray {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val lossless = compress(sticker, Bitmap.CompressFormat.WEBP_LOSSLESS, 100)
            if (lossless.size <= MAX_BYTES) return lossless
            var q = 90
            var bytes = compress(sticker, Bitmap.CompressFormat.WEBP_LOSSY, q)
            while (bytes.size > MAX_BYTES && q > 40) {
                q -= 10
                bytes = compress(sticker, Bitmap.CompressFormat.WEBP_LOSSY, q)
            }
            return bytes
        }
        @Suppress("DEPRECATION")
        return compress(sticker, Bitmap.CompressFormat.WEBP, 90)
    }

    private fun compress(bmp: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().use { s ->
            bmp.compress(format, quality, s)
            s.toByteArray()
        }

    private fun stampRing(canvas: Canvas, mask: Bitmap, x: Float, y: Float, radius: Float, paint: Paint) {
        val steps = 24
        for (r in listOf(radius, radius * 0.5f)) {
            for (i in 0 until steps) {
                val a = (i * 2 * Math.PI / steps)
                canvas.drawBitmap(mask, x + (cos(a) * r).toFloat(), y + (sin(a) * r).toFloat(), paint)
            }
        }
        canvas.drawBitmap(mask, x, y, paint)
    }

    /** Crops away fully transparent space around the content. */
    private fun trim(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > 8) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return src
        val r = Rect(minX, minY, maxX + 1, maxY + 1)
        return Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
    }

    /**
     * Looks at the outermost visible pixels. White scribbles get a dark border,
     * colored/dark edges (e.g. the Bubble quote rim) get the classic white one.
     */
    private fun edgeIsLight(src: Bitmap): Boolean {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        fun alpha(x: Int, y: Int) =
            if (x < 0 || y < 0 || x >= w || y >= h) 0 else px[y * w + x] ushr 24
        var sum = 0.0
        var n = 0
        val step = maxOf(1, min(w, h) / 200)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = px[y * w + x]
                if ((c ushr 24) > 128 &&
                    (alpha(x - 3, y) < 16 || alpha(x + 3, y) < 16 || alpha(x, y - 3) < 16 || alpha(x, y + 3) < 16)
                ) {
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    n++
                }
                x += step
            }
            y += step
        }
        return n > 0 && sum / n > 0.6
    }
}

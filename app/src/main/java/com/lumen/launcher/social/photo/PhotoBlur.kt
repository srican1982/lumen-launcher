package com.lumen.launcher.social.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A blur area on a photo. Edges are fractions of the image (0..1) so the same box maps
 * exactly onto the on-screen preview and the full-resolution export.
 * [strength] is 0..1 (shown to the user as 0–100); 1 makes the area unrecognizable.
 */
data class BlurBox(
    val id: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val strength: Float = 0.8f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun moved(dx: Float, dy: Float): BlurBox {
        val nx = (left + dx).coerceIn(0f, 1f - width)
        val ny = (top + dy).coerceIn(0f, 1f - height)
        return copy(left = nx, top = ny, right = nx + width, bottom = ny + height)
    }

    /** Drags one corner (0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left). */
    fun resized(corner: Int, dx: Float, dy: Float): BlurBox {
        val movesLeft = corner == 0 || corner == 3
        val movesTop = corner == 0 || corner == 1
        val l = if (movesLeft) (left + dx).coerceIn(0f, right - MIN_SIZE) else left
        val r = if (!movesLeft) (right + dx).coerceIn(left + MIN_SIZE, 1f) else right
        val t = if (movesTop) (top + dy).coerceIn(0f, bottom - MIN_SIZE) else top
        val b = if (!movesTop) (bottom + dy).coerceIn(top + MIN_SIZE, 1f) else bottom
        return copy(left = l, top = t, right = r, bottom = b)
    }

    companion object {
        const val MIN_SIZE = 0.04f
    }
}

/**
 * Precomputed, increasingly blurred copies of a photo. Each level is the photo shrunk by
 * [FACTORS] and softened; drawing a level stretched back over the photo gives the blur.
 * Built once per photo, so moving/resizing boxes costs nothing.
 */
class BlurLevels private constructor(val levels: List<Bitmap>) {

    /** Which two levels to mix for a strength, and how much of the stronger one. 0 = original photo. */
    data class Mix(val lower: Int, val upper: Int, val fraction: Float)

    fun mix(strength: Float): Mix {
        val pos = strength.coerceIn(0f, 1f) * levels.size
        val lower = floor(pos).toInt().coerceIn(0, levels.size)
        val upper = min(lower + 1, levels.size)
        return Mix(lower, upper, pos - lower)
    }

    /** Burns [boxes] into [out] (a mutable, full-resolution copy of the original photo). */
    fun applyTo(out: Bitmap, boxes: List<BlurBox>) {
        if (boxes.isEmpty()) return
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val full = RectF(0f, 0f, w, h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        boxes.forEach { box ->
            val m = mix(box.strength)
            canvas.save()
            canvas.clipRect(box.left * w, box.top * h, box.right * w, box.bottom * h)
            if (m.lower > 0) {
                paint.alpha = 255
                canvas.drawBitmap(levels[m.lower - 1], null, full, paint)
            }
            if (m.upper > m.lower && m.fraction > 0f) {
                paint.alpha = (m.fraction * 255).toInt()
                canvas.drawBitmap(levels[m.upper - 1], null, full, paint)
            }
            canvas.restore()
        }
    }

    companion object {
        /** Shrink factors, weakest to strongest. The last one makes faces unrecognizable. */
        private val FACTORS = intArrayOf(3, 6, 12, 24, 48, 96)

        fun build(photo: Bitmap): BlurLevels = BlurLevels(
            FACTORS.map { f ->
                val sw = max(2, photo.width / f)
                val sh = max(2, photo.height / f)
                val small = Bitmap.createScaledBitmap(photo, sw, sh, true)
                    .copy(Bitmap.Config.ARGB_8888, true)
                boxBlur(small, radius = 2, passes = 2)
                small
            }
        )

        /** Simple separable box blur on a small bitmap (removes blocky edges when stretched). */
        private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
            val w = bmp.width
            val h = bmp.height
            val src = IntArray(w * h)
            val tmp = IntArray(w * h)
            bmp.getPixels(src, 0, w, 0, 0, w, h)
            repeat(passes) {
                blurLine(src, tmp, w, h, horizontal = true, r = radius)
                blurLine(tmp, src, w, h, horizontal = false, r = radius)
            }
            bmp.setPixels(src, 0, w, 0, 0, w, h)
        }

        private fun blurLine(input: IntArray, output: IntArray, w: Int, h: Int, horizontal: Boolean, r: Int) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) {
                for (i in 0 until inner) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, inner - 1)
                        val c = if (horizontal) input[o * w + j] else input[j * w + o]
                        a += c ushr 24
                        rr += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                        n++
                    }
                    val idx = if (horizontal) o * w + i else i * w + o
                    output[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
    }
}

package com.lumen.launcher.ui

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lumen.launcher.data.GlassDepth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tasks uses a single black frost that works on light and dark photos.
 * Depth scales that frost; tone only tweaks the veil and edge, not the recipe.
 */
enum class BackdropTone { Dark, Light }

@Immutable
data class GlassColors(
    val cardTop: Color,
    val cardBottom: Color,
    val airyTop: Color,
    val airyBottom: Color,
    val filmTop: Color,
    val filmBottom: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val card: Color,
    val pill: Color,
    val well: Color,
    val veil: Color,
    val padTop: Color,
    val padMid: Color,
    val padBottom: Color
) {
    companion object {
        /** Tasks page frost — black at ~24% (0x3D000000). */
        val TasksFrost = Color(0x3D000000)

        val OnDark = of(GlassDepth.Balanced, BackdropTone.Dark)
        val OnLight = of(GlassDepth.Balanced, BackdropTone.Light)

        fun of(depth: GlassDepth, tone: BackdropTone): GlassColors {
            val a = depth.frostAlpha.coerceIn(0.10f, 0.55f)
            val top = (a + 0.04f).coerceIn(0.10f, 0.58f)
            val bottom = (a - 0.04f).coerceIn(0.08f, 0.50f)
            val airyTop = (a - 0.02f).coerceIn(0.08f, 0.48f)
            val airyBottom = (a - 0.08f).coerceIn(0.06f, 0.40f)
            val veil = if (tone == BackdropTone.Light) {
                Color.Black.copy(alpha = (a * 0.45f).coerceIn(0.06f, 0.22f))
            } else {
                Color.Transparent
            }
            return GlassColors(
                cardTop = Color.Black.copy(alpha = top),
                cardBottom = Color.Black.copy(alpha = bottom),
                airyTop = Color.Black.copy(alpha = airyTop),
                airyBottom = Color.Black.copy(alpha = airyBottom),
                filmTop = Color.Black.copy(alpha = top),
                filmBottom = Color.Black.copy(alpha = bottom),
                strokeTop = Color.White.copy(alpha = if (tone == BackdropTone.Light) 0.40f else 0.32f),
                strokeBottom = Color.White.copy(alpha = if (tone == BackdropTone.Light) 0.14f else 0.10f),
                card = Color.Black.copy(alpha = a),
                pill = Color.Black.copy(alpha = a),
                well = Color.Black.copy(alpha = (a * 0.55f).coerceIn(0.06f, 0.28f)),
                veil = veil,
                padTop = Color.Black.copy(alpha = top),
                padMid = Color.Black.copy(alpha = a),
                padBottom = Color.Black.copy(alpha = bottom)
            )
        }
    }
}

val LocalGlass = staticCompositionLocalOf { GlassColors.OnDark }

@Composable
fun rememberGlassColors(
    wallpaperPath: String?,
    depth: GlassDepth = GlassDepth.Balanced
): GlassColors {
    val context = LocalContext.current
    val stamp = remember(wallpaperPath) {
        wallpaperPath?.let { File(it).takeIf(File::exists)?.lastModified() } ?: 0L
    }
    var tone by remember { mutableStateOf(BackdropTone.Dark) }
    LaunchedEffect(wallpaperPath, stamp) {
        tone = withContext(Dispatchers.IO) { backdropTone(context, wallpaperPath) }
    }
    return GlassColors.of(depth, tone)
}

private const val LIGHT_MEAN = 0.42f
private const val BRIGHT_PIXEL = 0.58f
private const val BRIGHT_SHARE = 0.22f

/** A photo can average dark and still leave most cards sitting on glare. */
internal data class Backdrop(val mean: Float, val brightShare: Float)

internal fun toneOf(backdrop: Backdrop): BackdropTone {
    val light = backdrop.mean > LIGHT_MEAN || backdrop.brightShare > BRIGHT_SHARE
    return if (light) BackdropTone.Light else BackdropTone.Dark
}

private fun backdropTone(context: Context, path: String?): BackdropTone {
    val backdrop = wallpaperBackdrop(context, path) ?: return BackdropTone.Dark
    return toneOf(backdrop)
}

private fun wallpaperBackdrop(context: Context, path: String?): Backdrop? {
    spaceWallpaperBackdrop(path)?.let { return it }
    return systemWallpaperBackdrop(context)
}

private fun spaceWallpaperBackdrop(path: String?): Backdrop? {
    if (path.isNullOrBlank()) return null
    val file = File(path).takeIf { it.exists() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val widest = maxOf(bounds.outWidth, bounds.outHeight)
    if (widest <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = maxOf(1, widest / 64)
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    return sample(bitmap).also { bitmap.recycle() }
}

private fun systemWallpaperBackdrop(context: Context): Backdrop? {
    val manager = runCatching { WallpaperManager.getInstance(context) }.getOrNull() ?: return null
    systemWallpaperBitmap(manager)?.let { return it }
    return systemWallpaperColors(manager)
}

private fun systemWallpaperBitmap(manager: WallpaperManager): Backdrop? = runCatching {
    val bitmap = (manager.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
    val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    sample(small).also { if (small != bitmap) small.recycle() }
}.getOrNull()

private fun systemWallpaperColors(manager: WallpaperManager): Backdrop? = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@runCatching null
    val colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    ) {
        return@runCatching Backdrop(mean = 1f, brightShare = 1f)
    }
    val tones = listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
        .map { luminance(it.toArgb()) }
    if (tones.isEmpty()) return@runCatching null
    Backdrop(
        mean = tones.max(),
        brightShare = tones.count { it > BRIGHT_PIXEL }.toFloat() / tones.size
    )
}.getOrNull()

private fun sample(bitmap: Bitmap): Backdrop {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return Backdrop(mean = 0f, brightShare = 0f)
    val full = sampleRegion(bitmap, 0, height)
    val upper = sampleRegion(bitmap, 0, maxOf(1, (height * 0.55f).toInt()))
    return Backdrop(
        mean = maxOf(full.mean, upper.mean),
        brightShare = maxOf(full.brightShare, upper.brightShare)
    )
}

private fun sampleRegion(bitmap: Bitmap, top: Int, bottom: Int): Backdrop {
    val width = bitmap.width
    val height = (bottom - top).coerceAtLeast(1)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, top, width, height)
    var total = 0.0
    var bright = 0
    for (pixel in pixels) {
        val value = luminance(pixel)
        total += value
        if (value > BRIGHT_PIXEL) bright++
    }
    return Backdrop(
        mean = (total / pixels.size).toFloat(),
        brightShare = bright.toFloat() / pixels.size
    )
}

private fun luminance(argb: Int): Float {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return 0.299f * r + 0.587f * g + 0.114f * b
}

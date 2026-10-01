package com.lumen.launcher.travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.data.IconTreatment
import com.lumen.launcher.ui.LocalIconGlassStrength
import com.lumen.launcher.ui.LocalIconTreatment

/**
 * Trip Albums — pinned Social shortcut.
 *
 * Matches how Maps reads on the home grid:
 * - Normal / Glass / Glass Color → solid white tile + chromatic bloom
 * - White Glass → frosted glass tile + white bloom
 */
@Composable
fun TripAlbumsIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val treatment = LocalIconTreatment.current
    val glassStrength = LocalIconGlassStrength.current
    val shape = RoundedCornerShape(22.dp)
    val whiteGlass = treatment == IconTreatment.WhiteGlass
    val s = (glassStrength * 2f).coerceIn(0.35f, 1.6f)

    // Solid white plate (Maps-style) unless White Glass, which stays frosted.
    val tileBrush = if (whiteGlass) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (0.40f * s).coerceIn(0.22f, 0.55f)),
                Color.White.copy(alpha = (0.22f * s).coerceIn(0.12f, 0.36f)),
                Color.White.copy(alpha = (0.18f * s).coerceIn(0.10f, 0.30f))
            )
        )
    } else {
        Brush.verticalGradient(listOf(Color.White, Color.White))
    }
    val rim = if (whiteGlass) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(0.55f), Color.Black.copy(0.08f))
        )
    }
    val elevation = if (whiteGlass) 0.dp else 8.dp

    val spectrum = listOf(
        Color(0xFF5AC8FA),
        Color(0xFF40C8E0),
        Color(0xFF34C759),
        Color(0xFFFFD60A),
        Color(0xFFFF9F0A),
        Color(0xFFFF3B30),
        Color(0xFFFF2D55),
        Color(0xFFAF52DE)
    )
    val petals = if (whiteGlass) {
        List(8) { Color.White.copy(alpha = 0.96f) }
    } else {
        spectrum
    }
    val hubColor = Color.White
    val hubShadow = Color.Black.copy(alpha = if (whiteGlass) 0.18f else 0.14f)

    Box(
        modifier
            .size(size)
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation,
                        shape,
                        ambientColor = Color(0x44000000),
                        spotColor = Color(0x33000000)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(tileBrush)
            .border(0.85.dp, rim, shape),
        contentAlignment = Alignment.Center
    ) {
        if (whiteGlass) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.18f * s),
                            0.45f to Color.Transparent
                        )
                    )
            )
        }
        Canvas(Modifier.fillMaxSize(0.82f)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val outer = minOf(this.size.width, this.size.height) * 0.48f
            val hub = outer * 0.18f

            for (i in petals.indices.reversed()) {
                val angle = i * (360f / petals.size)
                rotate(degrees = angle, pivot = Offset(cx, cy)) {
                    drawPath(bloomPetal(Offset(cx, cy), outer, hub), petals[i])
                }
            }

            drawCircle(
                color = hubShadow,
                radius = hub * 1.12f,
                center = Offset(cx, cy + outer * 0.018f)
            )
            drawCircle(
                color = hubColor,
                radius = hub,
                center = Offset(cx, cy)
            )
        }
    }
}

private fun bloomPetal(center: Offset, outer: Float, hub: Float): Path {
    val tip = Offset(center.x, center.y - outer)
    val base = Offset(center.x, center.y - hub * 0.15f)
    val mid = center.y - (outer + hub) * 0.48f
    val half = outer * 0.28f
    return Path().apply {
        moveTo(base.x, base.y)
        cubicTo(
            center.x - half * 0.55f, center.y - hub * 0.85f,
            center.x - half, mid,
            tip.x, tip.y
        )
        cubicTo(
            center.x + half, mid,
            center.x + half * 0.55f, center.y - hub * 0.85f,
            base.x, base.y
        )
        close()
    }
}

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.ui.LocalGlass

/**
 * Trip Albums icon — chromatic bloom on Lumen glass.
 * Same family as rainbow photo marks (layered petals + center), but unique:
 * six soft teardrops, travel-leaning hues, cream hub — not an 8-petal copy.
 */
@Composable
fun TripAlbumsIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .size(size)
            .shadow(6.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x33C084FC))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        glass.filmTop.copy(alpha = 0.95f),
                        glass.filmBottom.copy(alpha = 0.9f)
                    )
                )
            )
            .border(
                0.85.dp,
                Brush.verticalGradient(listOf(glass.strokeTop, glass.strokeBottom)),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize(0.8f)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val outer = minOf(this.size.width, this.size.height) * 0.47f
            val hub = outer * 0.2f

            val petals = listOf(
                Color(0xFF64D2FF),
                Color(0xFF30D158),
                Color(0xFFFFD60A),
                Color(0xFFFF9F0A),
                Color(0xFFFF453A),
                Color(0xFFBF5AF2)
            )

            for (i in petals.indices.reversed()) {
                val angle = i * (360f / petals.size) - 12f
                rotate(degrees = angle, pivot = Offset(cx, cy)) {
                    val path = softPetal(Offset(cx, cy), outer, hub)
                    drawPath(
                        path,
                        brush = Brush.verticalGradient(
                            listOf(petals[i], petals[i].copy(alpha = 0.82f)),
                            startY = cy - outer,
                            endY = cy
                        )
                    )
                    drawPath(
                        path,
                        color = Color.White.copy(alpha = 0.18f),
                        style = Stroke(width = outer * 0.03f)
                    )
                }
            }

            // Soft lifted hub
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = hub * 1.15f,
                center = Offset(cx, cy + outer * 0.02f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFFCF7), Color(0xFFF2E8D8)),
                    center = Offset(cx, cy)
                ),
                radius = hub,
                center = Offset(cx, cy)
            )
        }
    }
}

/** Soft teardrop pointing up — rounder and wider than classic photo-app petals. */
private fun softPetal(center: Offset, outer: Float, hub: Float): Path {
    val tip = Offset(center.x, center.y - outer)
    val baseY = center.y - hub * 0.2f
    val midY = center.y - (outer + hub) * 0.45f
    val half = outer * 0.36f
    return Path().apply {
        moveTo(center.x, baseY)
        cubicTo(
            center.x - half * 0.4f, center.y - hub * 0.7f,
            center.x - half, midY,
            tip.x, tip.y
        )
        cubicTo(
            center.x + half, midY,
            center.x + half * 0.4f, center.y - hub * 0.7f,
            center.x, baseY
        )
        close()
    }
}

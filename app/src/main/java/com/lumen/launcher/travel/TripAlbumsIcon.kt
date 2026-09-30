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
import com.lumen.launcher.ui.LocalGlass
import com.lumen.launcher.ui.theme.LumenPalette

/**
 * Trip Albums — pinned Social shortcut.
 * Chromatic bloom on a white tile; rim/shadow adapt for White Glass and dark themes.
 */
@Composable
fun TripAlbumsIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val whiteGlass = LumenPalette.whiteGlass
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(22.dp)
    val tile = if (whiteGlass) Color.White.copy(alpha = 0.96f) else Color.White
    val rim = if (whiteGlass) {
        Brush.verticalGradient(listOf(glass.strokeTop, glass.strokeBottom))
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(0.55f), Color.Black.copy(0.06f))
        )
    }
    val shadowAmbient = if (whiteGlass) Color(0x22000000) else Color(0x44000000)
    val shadowSpot = if (whiteGlass) Color(0x28C084FC) else Color(0x33000000)

    Box(
        modifier
            .size(size)
            .shadow(if (whiteGlass) 5.dp else 8.dp, shape, ambientColor = shadowAmbient, spotColor = shadowSpot)
            .clip(shape)
            .background(tile)
            .border(0.9.dp, rim, shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize(0.82f)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val outer = minOf(this.size.width, this.size.height) * 0.48f
            val hub = outer * 0.18f

            // Eight-petal spectrum (clockwise from top), soft overlap — album mark, not a 1:1 copy.
            val petals = listOf(
                Color(0xFF5AC8FA), // blue
                Color(0xFF40C8E0), // cyan
                Color(0xFF34C759), // green
                Color(0xFFFFD60A), // yellow
                Color(0xFFFF9F0A), // orange
                Color(0xFFFF3B30), // red
                Color(0xFFFF2D55), // pink
                Color(0xFFAF52DE)  // purple
            )

            for (i in petals.indices.reversed()) {
                val angle = i * (360f / petals.size)
                rotate(degrees = angle, pivot = Offset(cx, cy)) {
                    drawPath(bloomPetal(Offset(cx, cy), outer, hub), petals[i])
                }
            }

            // Lifted white hub
            drawCircle(
                color = Color.Black.copy(alpha = if (whiteGlass) 0.10f else 0.14f),
                radius = hub * 1.12f,
                center = Offset(cx, cy + outer * 0.018f)
            )
            drawCircle(
                color = Color.White,
                radius = hub,
                center = Offset(cx, cy)
            )
        }
    }
}

/** Pointed leaf petal aimed up — rounded sides, soft tip. */
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

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.ui.LocalGlass

/**
 * Modern Trip Albums icon — soft glass tile with a refined stacked-frame mark
 * in champagne gold / lilac, matching Lumen’s Social grid.
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
            .shadow(8.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x44C084FC))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        glass.filmTop.copy(alpha = 0.92f),
                        glass.filmBottom.copy(alpha = 0.88f)
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
        // Quiet inner glow
        Box(
            Modifier
                .size(size * 0.78f)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x33E8C98A), Color(0x18C084FC), Color.Transparent)
                    )
                )
        )
        Canvas(Modifier.fillMaxSize(0.62f)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = (w * 0.075f).coerceIn(2.2.dp.toPx(), 3.4.dp.toPx())
            val gold = Color(0xFFE8D5A3)
            val lilac = Color(0xFFD4B4F8)
            val ink = Color.White.copy(alpha = 0.92f)

            // Back frame — soft lilac, slightly offset
            val back = RoundRect(
                left = w * 0.18f,
                top = h * 0.08f,
                right = w * 0.92f,
                bottom = h * 0.72f,
                cornerRadius = CornerRadius(w * 0.12f)
            )
            drawPath(
                Path().apply { addRoundRect(back) },
                color = lilac.copy(alpha = 0.55f),
                style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Front frame — champagne gold
            val front = RoundRect(
                left = w * 0.08f,
                top = h * 0.22f,
                right = w * 0.82f,
                bottom = h * 0.88f,
                cornerRadius = CornerRadius(w * 0.12f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0x33FFFFFF), Color(0x18C084FC)),
                    start = Offset(front.left, front.top),
                    end = Offset(front.right, front.bottom)
                ),
                topLeft = Offset(front.left, front.top),
                size = Size(front.width, front.height),
                cornerRadius = CornerRadius(w * 0.12f)
            )
            drawPath(
                Path().apply { addRoundRect(front) },
                brush = Brush.linearGradient(listOf(gold, lilac.copy(alpha = 0.85f))),
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Tiny horizon line inside the front frame — travel cue
            val midY = front.top + front.height * 0.58f
            drawLine(
                color = ink.copy(alpha = 0.55f),
                start = Offset(front.left + front.width * 0.18f, midY),
                end = Offset(front.right - front.width * 0.18f, midY),
                strokeWidth = stroke * 0.55f,
                cap = StrokeCap.Round
            )
            // Soft sun
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(gold.copy(alpha = 0.95f), gold.copy(alpha = 0.15f)),
                    center = Offset(front.left + front.width * 0.68f, front.top + front.height * 0.34f)
                ),
                radius = w * 0.07f,
                center = Offset(front.left + front.width * 0.68f, front.top + front.height * 0.34f)
            )
        }
    }
}

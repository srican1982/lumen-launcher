package com.lumen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Resting dock shelf from the Lumen mock: soft frosted capsule, quiet rim,
 * and a warm curved gold underglow beneath the center ring.
 */
@Composable
internal fun DockGlassSurface(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = h / 2f
        val k = r * .5522848f
        val gold = Color(0xFFE7C27A)
        val goldHot = Color(0xFFFFD88D)
        // Slight center notch so the raised ring reads as sitting on the glass.
        val frame = Path().apply {
            moveTo(r, 0f)
            lineTo(w * .40f, 0f)
            cubicTo(w * .44f, 0f, w * .46f, -6.dp.toPx(), w * .5f, -6.dp.toPx())
            cubicTo(w * .54f, -6.dp.toPx(), w * .56f, 0f, w * .60f, 0f)
            lineTo(w - r, 0f)
            cubicTo(w - r + k, 0f, w, r - k, w, r)
            cubicTo(w, r + k, w - r + k, h, w - r, h)
            lineTo(w * .60f, h)
            cubicTo(w * .55f, h, w * .54f, h + 3.dp.toPx(), w * .5f, h + 3.dp.toPx())
            cubicTo(w * .46f, h + 3.dp.toPx(), w * .45f, h, w * .40f, h)
            lineTo(r, h)
            cubicTo(r - k, h, 0f, r + k, 0f, r)
            cubicTo(0f, r - k, r - k, 0f, r, 0f)
            close()
        }
        // Frosted glass fill — translucent, warm, not heavy.
        drawPath(
            frame,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(0.20f),
                    Color(0xFF8A5A42).copy(0.16f),
                    Color(0xFF1C1418).copy(0.38f)
                )
            )
        )
        // Quiet inner sheen.
        drawPath(
            frame,
            Brush.horizontalGradient(
                listOf(
                    Color.White.copy(0.06f),
                    Color.Transparent,
                    Color.White.copy(0.05f)
                )
            )
        )
        // Thin lit rim.
        drawPath(
            frame,
            Brush.verticalGradient(
                listOf(
                    Color(0xFFFFF0CA).copy(0.42f),
                    Color.White.copy(0.14f),
                    gold.copy(0.28f)
                )
            ),
            style = Stroke(0.85.dp.toPx())
        )

        // Curved gold underglow under the ring — mock’s soft smile of light.
        val glowY = h + 10.dp.toPx()
        drawOval(
            Brush.radialGradient(
                listOf(Color(0xFFFFC34D).copy(0.38f), goldHot.copy(0.12f), Color.Transparent),
                Offset(w * 0.5f, glowY),
                w * 0.28f
            ),
            topLeft = Offset(w * 0.22f, h + 2.dp.toPx()),
            size = Size(w * 0.56f, 22.dp.toPx())
        )
        val smile = Path().apply {
            moveTo(w * 0.22f, glowY)
            cubicTo(w * 0.36f, glowY + 7.dp.toPx(), w * 0.64f, glowY + 7.dp.toPx(), w * 0.78f, glowY)
        }
        drawPath(
            smile,
            Brush.horizontalGradient(
                listOf(Color.Transparent, goldHot.copy(0.55f), gold.copy(0.7f), goldHot.copy(0.55f), Color.Transparent)
            ),
            style = Stroke(1.35.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            smile,
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(0.18f), Color.Transparent)
            ),
            style = Stroke(0.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

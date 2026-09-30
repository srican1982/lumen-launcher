package com.lumen.launcher.travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Trip Albums — pinned Social shortcut icon.
 * Sky + mountains + white plane + tilted mini photo (matches product art).
 */
@Composable
fun TripAlbumsIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Canvas(
        modifier
            .size(size)
            .shadow(8.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x442A7DE1))
            .clip(shape)
    ) {
        val w = this.size.width
        val h = this.size.height

        // Sky gradient
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF4DA3FF),
                    0.45f to Color(0xFF6EC8F5),
                    0.72f to Color(0xFF7ED6D0),
                    1f to Color(0xFF4FA8A0)
                )
            )
        )

        // Far ridge
        val far = Path().apply {
            moveTo(0f, h * 0.72f)
            lineTo(w * 0.18f, h * 0.58f)
            lineTo(w * 0.34f, h * 0.68f)
            lineTo(w * 0.52f, h * 0.52f)
            lineTo(w * 0.70f, h * 0.66f)
            lineTo(w * 0.86f, h * 0.56f)
            lineTo(w, h * 0.64f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(far, Color(0xFF2F7A78))

        // Near mountains
        val near = Path().apply {
            moveTo(0f, h * 0.86f)
            lineTo(w * 0.12f, h * 0.70f)
            lineTo(w * 0.28f, h * 0.82f)
            lineTo(w * 0.48f, h * 0.62f)
            lineTo(w * 0.62f, h * 0.78f)
            lineTo(w * 0.80f, h * 0.68f)
            lineTo(w, h * 0.80f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            near,
            brush = Brush.verticalGradient(
                listOf(Color(0xFF1F5C5A), Color(0xFF164845)),
                startY = h * 0.58f,
                endY = h
            )
        )
        // Snow / ridge highlights
        drawPath(
            Path().apply {
                moveTo(w * 0.42f, h * 0.64f)
                lineTo(w * 0.48f, h * 0.62f)
                lineTo(w * 0.52f, h * 0.66f)
                close()
            },
            Color(0xFFB8E8E4).copy(alpha = 0.55f)
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.74f, h * 0.70f)
                lineTo(w * 0.80f, h * 0.68f)
                lineTo(w * 0.84f, h * 0.72f)
                close()
            },
            Color(0xFFB8E8E4).copy(alpha = 0.4f)
        )

        // Soft plane shadow
        translate(left = w * 0.02f, top = h * 0.03f) {
            drawPath(planePath(w, h), Color.Black.copy(alpha = 0.16f))
        }
        // White airplane
        drawPath(planePath(w, h), Color.White)

        // Tilted mini photo — bottom right
        val photo = w * 0.34f
        val px = w * 0.58f
        val py = h * 0.58f
        rotate(degrees = 14f, pivot = Offset(px + photo / 2f, py + photo / 2f)) {
            // Soft shadow under photo
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(px + photo * 0.04f, py + photo * 0.06f),
                size = Size(photo, photo),
                cornerRadius = CornerRadius(photo * 0.14f)
            )
            // White frame
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(px, py),
                size = Size(photo, photo),
                cornerRadius = CornerRadius(photo * 0.14f)
            )
            val inset = photo * 0.11f
            val ix = px + inset
            val iy = py + inset
            val iw = photo - inset * 2f
            val ih = photo - inset * 2.15f
            // Inner landscape
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF7EC8F8), Color(0xFFB8E4F8)),
                    startY = iy,
                    endY = iy + ih
                ),
                topLeft = Offset(ix, iy),
                size = Size(iw, ih),
                cornerRadius = CornerRadius(photo * 0.08f)
            )
            // Sun
            drawCircle(
                color = Color(0xFFFFD84A),
                radius = iw * 0.16f,
                center = Offset(ix + iw * 0.72f, iy + ih * 0.28f)
            )
            // Mini mountains in photo
            val hills = Path().apply {
                moveTo(ix, iy + ih)
                lineTo(ix, iy + ih * 0.62f)
                lineTo(ix + iw * 0.32f, iy + ih * 0.38f)
                lineTo(ix + iw * 0.55f, iy + ih * 0.58f)
                lineTo(ix + iw * 0.78f, iy + ih * 0.42f)
                lineTo(ix + iw, iy + ih * 0.55f)
                lineTo(ix + iw, iy + ih)
                close()
            }
            drawPath(hills, Color(0xFF2A5F9E))
        }

        // Subtle outer rim
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            size = Size(w, h),
            cornerRadius = CornerRadius(22.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/** Commercial jet silhouette angled up-right, sized to icon bounds. */
private fun planePath(w: Float, h: Float): Path {
    // Local coords around center-left, then we position via absolute points.
    val cx = w * 0.42f
    val cy = h * 0.42f
    val s = minOf(w, h)
    // Nose points up-right
    return Path().apply {
        // fuselage
        moveTo(cx - s * 0.22f, cy + s * 0.06f) // tail left
        cubicTo(
            cx - s * 0.10f, cy + s * 0.02f,
            cx + s * 0.08f, cy - s * 0.06f,
            cx + s * 0.26f, cy - s * 0.14f // nose
        )
        cubicTo(
            cx + s * 0.18f, cy - s * 0.02f,
            cx + s * 0.02f, cy + s * 0.08f,
            cx - s * 0.16f, cy + s * 0.12f
        )
        close()
        // main wing
        moveTo(cx - s * 0.02f, cy + s * 0.02f)
        lineTo(cx + s * 0.04f, cy - s * 0.02f)
        lineTo(cx - s * 0.02f, cy + s * 0.20f)
        lineTo(cx - s * 0.10f, cy + s * 0.18f)
        close()
        // far wing tip
        moveTo(cx + s * 0.02f, cy - s * 0.02f)
        lineTo(cx + s * 0.10f, cy - s * 0.08f)
        lineTo(cx + s * 0.18f, cy - s * 0.02f)
        lineTo(cx + s * 0.08f, cy + s * 0.02f)
        close()
        // tail fin
        moveTo(cx - s * 0.20f, cy + s * 0.04f)
        lineTo(cx - s * 0.26f, cy - s * 0.06f)
        lineTo(cx - s * 0.16f, cy + s * 0.02f)
        close()
    }
}

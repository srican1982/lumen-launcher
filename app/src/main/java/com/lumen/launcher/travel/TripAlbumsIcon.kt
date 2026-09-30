package com.lumen.launcher.travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Elegant Trip Albums home icon — stacked soft polaroids on a dusk-violet tile. */
@Composable
fun TripAlbumsIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .size(size)
            .shadow(10.dp, shape, ambientColor = Color(0x66C084FC), spotColor = Color(0x55E8C98A))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3A2458), Color(0xFF1C1230), Color(0xFF2A183F))
                )
            )
            .border(
                0.9.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.42f), Color(0xFFE8C98A).copy(0.28f), Color.White.copy(0.08f))
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size * 0.72f)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x55E8C98A), Color(0x22C084FC), Color.Transparent)
                    )
                )
        )
        PolaroidFrame(
            modifier = Modifier
                .size(size * 0.46f)
                .offset(x = -(size * 0.08f), y = size * 0.02f)
                .rotate(-14f),
            fill = Brush.verticalGradient(listOf(Color(0xFF6B4A9A), Color(0xFF3D2860))),
            stroke = Color.White.copy(0.35f)
        )
        PolaroidFrame(
            modifier = Modifier
                .size(size * 0.48f)
                .offset(x = size * 0.07f, y = -(size * 0.03f))
                .rotate(10f),
            fill = Brush.verticalGradient(listOf(Color(0xFF8B6BB8), Color(0xFF4A3278))),
            stroke = Color.White.copy(0.4f)
        )
        Box(
            Modifier
                .size(size * 0.5f)
                .offset(y = size * 0.04f)
                .rotate(-2f)
                .shadow(6.dp, RoundedCornerShape(5.dp))
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.94f))
                .padding(start = 5.dp, top = 5.dp, end = 5.dp, bottom = 9.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val photo = Size(this.size.width, this.size.height)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFD8C4F5), Color(0xFFF2E6C9), Color(0xFFB8D4E8))
                    ),
                    size = photo,
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                val hill = Path().apply {
                    moveTo(0f, photo.height * 0.62f)
                    quadraticBezierTo(photo.width * 0.28f, photo.height * 0.42f, photo.width * 0.52f, photo.height * 0.58f)
                    quadraticBezierTo(photo.width * 0.78f, photo.height * 0.72f, photo.width, photo.height * 0.55f)
                    lineTo(photo.width, photo.height)
                    lineTo(0f, photo.height)
                    close()
                }
                drawPath(hill, Color(0xFF6A5A8E))
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFF5D98A), Color(0x00F5D98A)),
                        center = Offset(photo.width * 0.72f, photo.height * 0.28f)
                    ),
                    radius = photo.width * 0.18f,
                    center = Offset(photo.width * 0.72f, photo.height * 0.28f)
                )
                drawRoundRect(
                    color = Color(0xFFE8C98A).copy(alpha = 0.55f),
                    size = photo,
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun PolaroidFrame(
    modifier: Modifier,
    fill: Brush,
    stroke: Color
) {
    Box(
        modifier
            .shadow(4.dp, RoundedCornerShape(5.dp))
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(0.88f))
            .border(0.6.dp, stroke, RoundedCornerShape(5.dp))
            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 8.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(fill)
        )
    }
}

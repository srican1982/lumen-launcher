package com.lumen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Soft, scalable blue-violet light, with no bitmap stretching on wide displays. */
@Composable
internal fun HomeAuroraBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF02081E), Color(0xFF052861), Color(0xFF122D78))))
        fun light(x: Float, y: Float, radius: Float, color: Color) {
            val center = Offset(size.width * x, size.height * y)
            val r = size.height * radius
            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * .4f), Color.Transparent), center, r), r, center)
        }
        light(.60f, .04f, .24f, Color(0xAA008AF2))
        light(.02f, .49f, .34f, Color(0xAAE449CF))
        light(.53f, .72f, .16f, Color(0xBB9226FA))
        light(.45f, .64f, .08f, Color(0xAADF2CF1))
        light(.94f, .82f, .19f, Color(0x996875FF))
        light(.00f, .92f, .21f, Color(0xAACC9071))
    }
}

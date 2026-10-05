package com.lumen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Soft color fields stay behind Social content and never intercept gestures. */
@Composable
internal fun SocialBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF420D38), Color(0xFF791B63), Color(0xFF5431AD), Color(0xFF084BA0))))
        fun glow(x: Float, y: Float, radius: Float, color: Color) {
            drawRect(Brush.radialGradient(listOf(color, color.copy(alpha = .65f), Color.Transparent),
                center = Offset(w * x, h * y), radius = radius))
        }
        glow(.96f, .12f, w * .94f, Color(0xFFFF6429))
        glow(.08f, .32f, w * .78f, Color(0xFFDC175D))
        glow(.94f, .46f, w * 1.08f, Color(0xFFFF963E))
        glow(.30f, .56f, w * .9f, Color(0xFFC93BA4))
        glow(.03f, .77f, w * .83f, Color(0xFF6250DF))
        glow(.93f, .89f, w * 1.15f, Color(0xFF00BDD6))
        // A quiet header and lower dock keep white labels legible over the color.
        drawRect(Brush.verticalGradient(0f to Color(0x330B0923), .38f to Color.Transparent, 1f to Color(0x22041C3D)))
    }
}

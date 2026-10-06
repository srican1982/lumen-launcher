package com.lumen.launcher.ui.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lumen.launcher.R

/** Full-screen Focus atmosphere — lake photo with a soft fade into navy (no hard band). */
@Composable
internal fun FocusRainBackdrop(active: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "Focus rain")
    val phase by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "Falling rain"
    )
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(if (active) R.drawable.focus_moonlit_lake else R.drawable.focus_rain_lake),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd
        )
        if (!active) {
            Canvas(Modifier.fillMaxSize()) {
                repeat(48) { index ->
                    val x = ((index * 0.618034f + phase * 0.025f) % 1f) * size.width
                    val y = ((index * 0.317f + phase) % 1f) * size.height
                    drawLine(
                        Color(0xFFB8D7F4).copy(alpha = 0.12f),
                        Offset(x, y),
                        Offset(x - 3f, y + 16f),
                        strokeWidth = 1f
                    )
                }
            }
        }
        // Soft vignette: photo stays clear up top, dissolves into navy through the mid/lower UI.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to FocusInk.copy(alpha = 0.18f),
                            0.28f to FocusInk.copy(alpha = 0.12f),
                            0.48f to FocusInk.copy(alpha = 0.35f),
                            0.62f to FocusInk.copy(alpha = 0.62f),
                            0.76f to FocusInk.copy(alpha = 0.88f),
                            1.00f to FocusInk.copy(alpha = 0.98f)
                        )
                    )
                )
        )
    }
}

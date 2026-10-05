package com.lumen.launcher.ui.focus

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lumen.launcher.R

@Composable
internal fun FocusRainBackdrop(active: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "Focus rain")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "Falling rain")
    Box(Modifier.fillMaxWidth().height(if (active) 540.dp else 420.dp)) {
        Image(painterResource(if(active) R.drawable.focus_moonlit_lake else R.drawable.focus_rain_lake), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = androidx.compose.ui.Alignment.CenterEnd)
        if (!active) Canvas(Modifier.fillMaxSize()) {
            repeat(48) { index ->
                val x = ((index * 0.618034f + phase * 0.025f) % 1f) * size.width
                val y = ((index * 0.317f + phase) % 1f) * size.height
                drawLine(Color(0xFFB8D7F4).copy(alpha = 0.12f), Offset(x, y), Offset(x - 3f, y + 16f), strokeWidth = 1f)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to FocusInk.copy(alpha = .3f), .55f to FocusInk.copy(alpha = .2f), 1f to FocusInk)))
    }
}

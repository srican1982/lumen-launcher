package com.lumen.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun FittingGreeting(greeting: String) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 28.sp)
        val width = measurer.measure(greeting, style, softWrap = false).size.width
        val fitted = (28f * constraints.maxWidth / width.coerceAtLeast(1)).coerceIn(22f, 28f)
        // At larger accessibility sizes, wrap instead of truncating or shrinking indefinitely.
        Text(greeting, color = Lumen.Text, style = style.copy(fontSize = fitted.sp), maxLines = 2)
    }
}

internal fun Modifier.softGridEdges(canScrollUp: () -> Boolean, canScrollDown: () -> Boolean): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        if (canScrollUp()) {
            val fade = minOf(18.dp.toPx(), size.height / 2)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
                size = Size(size.width, fade), blendMode = BlendMode.DstIn)
        }
        if (canScrollDown()) {
            val fade = minOf(36.dp.toPx(), size.height / 2)
            drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - fade, size.height),
                topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade), blendMode = BlendMode.DstIn)
        }
    }

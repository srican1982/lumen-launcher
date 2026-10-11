package com.lumen.launcher.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumen.launcher.ui.rememberNavBottomPadding

/** The same bright glass surround for the album list and individual albums. */
@Composable
internal fun TripGlassFrame(content: @Composable () -> Unit) {
    val navBottom = rememberNavBottomPadding(56.dp)
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxSize().statusBarsPadding()
        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = navBottom + 12.dp)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .40f), Color(0xFFAAB9DC).copy(alpha = .32f))))
        .border(1.dp, Color.White.copy(alpha = .55f), shape)
        .padding(10.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(Color(0xFF151D2B))) {
        content()
    }
}

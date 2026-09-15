package com.lumen.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.R

@Composable
fun LumenMark(
    size: Dp,
    modifier: Modifier = Modifier,
    glow: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (glow) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0x66F3D7A0),
                                Color(0x22E7C27A),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_lumen_mark),
            contentDescription = "Lumen",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * 0.86f)
        )
    }
}

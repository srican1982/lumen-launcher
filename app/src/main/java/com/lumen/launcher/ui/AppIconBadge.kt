package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.badge.NotificationBadgeMode
import com.lumen.launcher.ui.theme.Outfit

@Composable
fun BoxScope.AppNotificationBadge(
    count: Int,
    mode: NotificationBadgeMode,
    iconSize: Dp
) {
    if (mode == NotificationBadgeMode.Off || count <= 0) return
    val scale = (iconSize.value / 56f).coerceIn(0.72f, 1.35f)
    val dotSize = (10f * scale).dp
    val pillMin = (18f * scale).dp
    val fontSize = (10f * scale).sp
    val offsetX = iconSize * 0.14f
    val offsetY = iconSize * -0.10f

    when (mode) {
        NotificationBadgeMode.Dot -> {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = offsetX, y = offsetY)
                    .shadow(2.dp, CircleShape, clip = false)
                    .defaultMinSize(dotSize, dotSize)
                    .background(Color(0xFFE53935), CircleShape)
            )
        }
        NotificationBadgeMode.Number -> {
            val label = if (count >= 100) "99+" else count.toString()
            val wide = label.length >= 3
            val shape = if (wide) RoundedCornerShape(999.dp) else CircleShape
            val horizontal = if (wide) (5f * scale).dp else (4f * scale).dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = offsetX, y = offsetY)
                    .shadow(2.dp, shape, clip = false)
                    .defaultMinSize(if (wide) pillMin * 1.35f else pillMin, pillMin)
                    .background(Color(0xFFE53935), shape)
                    .padding(horizontal = horizontal, vertical = (1f * scale).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = fontSize,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        NotificationBadgeMode.Off -> Unit
    }
}

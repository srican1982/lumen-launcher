package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.badge.NotificationBadgeMode
import com.lumen.launcher.ui.theme.Outfit

/** Apple's notification red. */
val BadgeRed = Color(0xFFFF3B30)

/** iPhone-style badge on an app icon: red circle / pill with a white number, on the top-right corner. */
@Composable
fun BoxScope.AppNotificationBadge(
    count: Int,
    mode: NotificationBadgeMode,
    iconSize: Dp
) {
    if (mode == NotificationBadgeMode.Off || count <= 0) return
    val badge = iconSize * 0.40f
    val corner = Modifier
        .align(Alignment.TopEnd)
        .offset(x = iconSize * 0.12f, y = -(iconSize * 0.12f))
    when (mode) {
        NotificationBadgeMode.Dot -> Box(
            corner
                .size(iconSize * 0.24f)
                .shadow(1.5.dp, CircleShape, clip = false)
                .background(BadgeRed, CircleShape)
        )
        NotificationBadgeMode.Number -> CountBadge(count, badge, corner)
        NotificationBadgeMode.Off -> Unit
    }
}

/**
 * The red count badge itself (also used on the TouchPad notifications tile).
 * One digit = circle; more = pill. 100+ shows "99+".
 */
@Composable
fun CountBadge(count: Int, height: Dp, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val label = if (count >= 100) "99+" else count.toString()
    val density = LocalDensity.current
    val fontSize = with(density) { (height * 0.66f).toSp() }
    Box(
        modifier
            .height(height)
            .defaultMinSize(minWidth = height)
            .shadow(1.5.dp, RoundedCornerShape(height / 2), clip = false)
            .background(BadgeRed, RoundedCornerShape(height / 2))
            .padding(horizontal = if (label.length > 1) height * 0.24f else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

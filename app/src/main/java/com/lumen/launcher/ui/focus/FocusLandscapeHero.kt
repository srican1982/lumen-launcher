package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun FocusLandscapeHero(
    taskLabel: String? = null,
    onPickTask: () -> Unit = {}
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Focus",
                        style = androidx.compose.ui.text.TextStyle(brush = FocusTitleGradient),
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        lineHeight = 32.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Silence distractions.\nStay connected to what\ntruly matters.",
                        color = FocusMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = Outfit
                    )
                }
                Column(Modifier.widthIn(max = 142.dp).padding(start = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A quieter you\nA brighter tomorrow.",
                        color = FocusAccent.copy(alpha = 0.9f),
                        fontFamily = Outfit,
                        fontStyle = FontStyle.Italic,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Row(
                        Modifier.heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(FocusCard.copy(alpha = 0.8f))
                            .border(1.dp, FocusAccent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .clickable(onClick = onPickTask)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.Checklist, null, tint = FocusAccent, modifier = Modifier.size(18.dp))
                        Text(
                            taskLabel?.takeIf { it.isNotBlank() } ?: "Pick task",
                            color = Color.White, fontFamily = Outfit, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                listOf(
                    Icons.Outlined.NotificationsOff to "Blocks\nnotifications",
                    Icons.Outlined.People to "Allows selected\npeople",
                    Icons.Outlined.Shield to "Peace of\nmind"
                ).forEach { (icon, label) ->
                    Column(Modifier.weight(1f)) {
                        Icon(icon, null, tint = FocusIconTint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(label, color = FocusMuted, fontSize = 10.sp, lineHeight = 12.sp, fontFamily = Outfit)
                    }
                }

            }
        }
    }
}

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
            .height(188.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.lumen.launcher.R.drawable.focus_moonlit_lake),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        FocusInk.copy(alpha = 0.25f),
                        FocusInk.copy(alpha = 0.55f),
                        FocusInk.copy(alpha = 0.9f)
                    )
                )
            )
        )
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
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 32.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Silence distractions. Stay connected to what truly matters.",
                        color = FocusMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = Outfit
                    )
                }
                Text(
                    "A quieter you\nA brighter tomorrow.",
                    color = FocusAccent.copy(alpha = 0.9f),
                    fontFamily = Outfit,
                    fontStyle = FontStyle.Italic,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
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
                val chipShape = RoundedCornerShape(18.dp)
                Row(
                    Modifier
                        .clip(chipShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.32f), chipShape)
                        .clickable(onClick = onPickTask)
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Outlined.Checklist, null, tint = FocusAccent, modifier = Modifier.size(15.dp))
                    Text(
                        if (taskLabel.isNullOrBlank()) "Pick task" else taskLabel,
                        color = Color.White,
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 96.dp)
                    )
                }
            }
        }
    }
}

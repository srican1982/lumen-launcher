package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FocusLandscapeHero() {
    Box(Modifier.fillMaxWidth().height(190.dp)) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.lumen.launcher.R.drawable.focus_moonlit_lake),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Column(Modifier.padding(top = 4.dp)) {
            Text(
                "Deep Focus",
                style = TextStyle(brush = FocusTitleGradient),
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Silence distractions.\nKeep the people who matter.\nOnly the apps you choose.",
                color = FocusMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    Icons.Outlined.NotificationsOff to "Blocks\nnotifications",
                    Icons.Outlined.People to "Lets important\npeople through",
                    Icons.Outlined.Apps to "Selected apps\nin Lumen"
                ).forEach { (icon, label) ->
                    Column {
                        Icon(icon, null, tint = FocusIconTint, modifier = Modifier.size(20.dp))
                        Text(label, color = FocusMuted, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                }
            }
        }
    }
}

package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FocusTimerHero(minutes: Int, group: String, taskLabel: String?, onPickTask: () -> Unit, onStart: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(188.dp).offset(y = (-3).dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val outer = 5.dp.toPx()
                val inner = 14.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color(0xDA09121E), Color(0x8909121E))), radius = size.minDimension / 2 - outer)
                drawArc(Color(0xFFE5BC72), 0f, 360f, false, Offset(outer, outer), Size(size.width - outer * 2, size.height - outer * 2), style = Stroke(1.2.dp.toPx()))
                drawArc(Color(0xFF252B3C), 0f, 360f, false, Offset(inner, inner), Size(size.width - inner * 2, size.height - inner * 2), style = Stroke(4.dp.toPx()))
                drawArc(Color(0xFFB394F1), -90f, 295f, false, Offset(inner, inner), Size(size.width - inner * 2, size.height - inner * 2), style = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Column(Modifier.padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.Eco, null, tint = Color(0xFFB394F1), modifier = Modifier.size(25.dp))
                Text(if (taskLabel.isNullOrBlank()) "Focus on Yourself" else "Focus on $taskLabel", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 130.dp).clickable(onClick = onPickTask))
                Text("$minutes min", color = Color.White, fontSize = 28.sp)
            }
            Box(Modifier.align(Alignment.BottomCenter).offset(y = (-23).dp).size(34.dp)
                .shadow(7.dp, CircleShape, ambientColor = Color(0xFF7962FF), spotColor = Color(0xFF7962FF))
                .clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFB394F1), Color(0xFF8A68D0))))
                .border(0.5.dp, Color(0xFFC9BDFF), CircleShape).clickable(onClick = onStart), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Start Focus", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        OutlinedButton(onClick = onPickTask, modifier = Modifier.align(Alignment.TopEnd).widthIn(max = 116.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xEE101D30), contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA995E6)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Pick task", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.People
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
                val inset = 5.dp.toPx()
                val diameter = size.minDimension - inset * 2
                drawCircle(Brush.radialGradient(listOf(Color(0xA0061020), Color.Transparent)), radius = size.minDimension / 2)
                listOf(16f to .04f, 10f to .07f, 5f to .2f, 2.5f to 1f).forEach { (width, alpha) ->
                    drawArc(Color(0xFFFFC36C).copy(alpha = alpha), 0f, 360f, false, Offset(inset, inset), Size(diameter, diameter), style = Stroke(width.dp.toPx()))
                }
                drawArc(Color(0xFFFFF4D8), 0f, 360f, false, Offset(inset, inset), Size(diameter, diameter), style = Stroke(.4.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                Icon(Icons.Outlined.People, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Focus on", color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                Text(group, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 108.dp))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${minutes / 60}", color = Color.White, fontSize = 25.sp)
                        Text("hours", color = FocusMuted, fontSize = 9.sp)
                    }
                    Text(":", color = Color.White, fontSize = 24.sp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d".format(java.util.Locale.ROOT, minutes % 60), color = Color.White, fontSize = 25.sp)
                        Text("minutes", color = FocusMuted, fontSize = 9.sp)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).offset(y = (-4).dp).size(34.dp)
                .shadow(7.dp, CircleShape, ambientColor = Color(0xFF7962FF), spotColor = Color(0xFF7962FF))
                .clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFA387FF), Color(0xFF5533FF), Color(0xFF146EFF))))
                .border(0.5.dp, Color(0xFFC9BDFF), CircleShape).clickable(onClick = onStart), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Start Focus", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        TextButton(onClick = onPickTask, modifier = Modifier.align(Alignment.TopEnd).widthIn(max = 100.dp)) {
            Text(taskLabel ?: "Pick task", color = FocusMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

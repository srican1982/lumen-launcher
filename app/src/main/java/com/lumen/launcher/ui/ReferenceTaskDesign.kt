package com.lumen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.data.TodoTime
import com.lumen.launcher.ui.theme.Outfit
import java.util.Calendar

@Composable
internal fun ReferenceTaskWeek(anchor: Long, selected: Long, tasks: List<TodoItem>, onNavigate: (Int, Boolean) -> Unit, onSelect: (Long) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.width(108.dp).fillMaxHeight().clip(shape).border(1.dp, Color.White.copy(alpha = .4f), shape)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF202C32), Color(0xFF716344), Color(0xFFC5A66A))))
                drawCircle(Color(0xFFFFDC7C), 7.dp.toPx(), Offset(size.width * .35f, size.height * .6f))
                listOf(Color(0xFFB29B6D), Color(0xFF655B46), Color(0xFF293333)).forEachIndexed { index, color ->
                    val base = size.height * (.65f + index * .1f)
                    val path = Path().apply {
                        moveTo(0f, base); lineTo(size.width * .18f, base - 8.dp.toPx()); lineTo(size.width * .36f, base + 5.dp.toPx())
                        lineTo(size.width * .67f, base - 13.dp.toPx()); lineTo(size.width, base - 4.dp.toPx()); lineTo(size.width, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(path, color)
                }
            }
            Text(android.text.format.DateFormat.format("MMMM \u2019yy", anchor).toString(), color = Color.White, fontFamily = Outfit,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp))
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CalendarArrow(false, "Previous month") { onNavigate(-1, true) }
                CalendarArrow(true, "Next month") { onNavigate(1, true) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().glass(shape, LocalGlass.current).padding(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CalendarArrow(false, "Previous week") { onNavigate(-1, false) }
                Text("This week", color = Color(0xFFF5CB7E), fontSize = 10.sp, fontFamily = Outfit,
                    modifier = Modifier.weight(1f).clickable(onClickLabel = "Return to this week") { onNavigate(0, false) }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                CalendarArrow(true, "Next week") { onNavigate(1, false) }
            }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val start = Calendar.getInstance().apply { timeInMillis = TodoTime.startOfDay(anchor); add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7)) }
            repeat(7) { index ->
                val day = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, index) }.timeInMillis
                val active = TodoTime.startOfDay(selected) == day
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(if (active) Brush.verticalGradient(listOf(Color(0xFFF5CB7E).copy(alpha = .45f), Color(0xFFB49356).copy(alpha = .28f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(day) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text(android.text.format.DateFormat.format("EEE", day).toString(), color = Color.White.copy(alpha = .85f), fontSize = 8.sp, fontFamily = Outfit)
                    Text(android.text.format.DateFormat.format("d", day).toString(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = Outfit)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        tasks.filter { it.dueAt?.let(TodoTime::startOfDay) == day }.take(3).forEach { item ->
                            Box(Modifier.size(3.dp).background(if (item.done) Color(0xFFD9E6D0) else Color(0xFFF5CB7E), RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CalendarArrow(next: Boolean, label: String, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (next) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
            contentDescription = label, tint = Color(0xFFF5CB7E), modifier = Modifier.size(20.dp))
    }
}

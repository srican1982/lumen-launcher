package com.lumen.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.TaskAgenda
import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.data.TodoTime
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun TaskStatus(open: List<TodoItem>, completed: Int, now: Long) {
    val overdue = open.count { TaskAgenda.overdue(it, now) }
    val total = open.size + completed
    val progress by animateFloatAsState(if (total == 0) 0f else completed.toFloat() / total, label = "Task progress")
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().glass(shape, LocalGlass.current).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${open.size} ${if (open.size == 1) "task" else "tasks"} \u00B7 " + if (overdue > 0) "$overdue overdue" else "$completed done", color = Lumen.Text, fontFamily = Outfit,
            fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
        LinearProgressIndicator(progress = { progress }, color = Lumen.Accent, trackColor = Color.White.copy(alpha = .15f),
            modifier = Modifier.width(64.dp).height(3.dp), drawStopIndicator = {})
    }
}

@Composable
internal fun TaskCalmHero(completed: Int, onPlan: () -> Unit) {
    DashboardGlass {
        Text("L U M E N", color = Lumen.Muted, fontFamily = Outfit, fontSize = 10.sp)
        Text("All clear for today \u2713", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 27.sp, modifier = Modifier.padding(vertical = 8.dp))
        Text(if (completed > 0) "You've finished $completed ${if (completed == 1) "task" else "tasks"}. Enjoy the calm." else "A little room to breathe. Plan what's next.", color = Lumen.Text, fontFamily = Outfit, fontSize = 14.sp)
        if (completed > 0) LinearProgressIndicator(progress = { 1f }, color = Lumen.Accent, modifier = Modifier.fillMaxWidth().height(3.dp))
        Button(onClick = onPlan, colors = ButtonDefaults.buttonColors(containerColor = Lumen.BrandGold, contentColor = Lumen.OnBrandGold), shape = RoundedCornerShape(22.dp)) { Text("Plan tomorrow \u2192", fontFamily = Outfit) }
    }
}

@Composable
internal fun TaskTomorrowPreview(tasks: List<TodoItem>, compact: Boolean, onOpen: () -> Unit) {
    if (tasks.isEmpty()) return
    if (compact) {
        val shape = RoundedCornerShape(18.dp)
        Row(Modifier.fillMaxWidth().glass(shape, LocalGlass.current).clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Tomorrow", color = Lumen.Text, fontFamily = Outfit, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${tasks.size} ${if (tasks.size == 1) "task" else "tasks"} \u203A", color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
        }
        return
    }
    DashboardGlass {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            Text(if (compact) "Tomorrow" else "Tomorrow preview", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${tasks.size} ${if (tasks.size == 1) "task" else "tasks"} \u203A", color = Lumen.Text, fontFamily = Outfit)
        }
        if (!compact) tasks.take(3).forEach { item ->
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp)) {
                Text(item.text, color = Lumen.Text, fontFamily = Outfit, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text(item.dueAt?.let { if (TodoTime.hasClock(it)) TodoTime.timeLabel(it) else "Anytime" }.orEmpty(), color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun TaskQuickCapture(onReminder: () -> Unit, onNote: () -> Unit, onCall: () -> Unit) {
    DashboardGlass {
        Text("Quick capture", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 17.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Reminder" to onReminder, "Note" to onNote, "Call reminder" to onCall).forEach { (label, action) ->
                OutlinedButton(onClick = action, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(label, color = Lumen.Text, fontFamily = Outfit, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DashboardGlass(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().glass(shape, LocalGlass.current).padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

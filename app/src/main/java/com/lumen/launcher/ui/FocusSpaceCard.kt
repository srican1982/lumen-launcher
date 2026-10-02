package com.lumen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.FocusAppsResolver
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.data.NeedNowHint
import com.lumen.launcher.data.NeedNowResolver
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay

private val FocusGreen = Color(0xFF83F5AC)
private val FocusGreenDeep = Color(0xFF059669)
private val FocusGlass = Color(0xCC142018)

/**
 * Minimal Focus Space canvas — no TouchPad.
 * Idle: pick task + duration. Active: one task, timer, 4 apps, Need Now.
 */
@Composable
fun FocusSpaceCard(state: LauncherUiState, vm: LauncherViewModel) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var chooseTask by remember { mutableStateOf(false) }
    var editApps by remember { mutableStateOf(false) }
    LaunchedEffect(state.focusing, state.focusRunning) {
        while (state.focusRunning) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remainingMs = when {
        state.focusPaused -> state.focusPausedRemainingMs
        state.focusRunning -> (state.focusUntil - now).coerceAtLeast(0L)
        else -> 0L
    }
    val totalMs = state.focusTotalMs.takeIf { it > 0 } ?: remainingMs.coerceAtLeast(1L)
    val progress = if (!state.focusing) 0f else {
        val done = (totalMs - remainingMs).toFloat() / totalMs.toFloat()
        done.coerceIn(0f, 1f)
    }

    val task = state.focusTask
    LaunchedEffect(task?.text, state.visibleApps, state.geminiApiKey) {
        task?.takeUnless { it.done }?.let { vm.suggestFocusApps(it.text, state.visibleApps) }
    }
    val appScope = task?.let { "FocusTask:${it.id}" } ?: SpaceKind.Focus.name
    val taskPins = state.focusPins[appScope].orEmpty()
    val needNowApps = com.lumen.launcher.search.FocusContext.apps(state)
    val nextActions = com.lumen.launcher.search.FocusContext.actions(state)

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.focusing) {
            ActiveFocusCard(
                taskTitle = task?.text ?: "Focus session",
                remainingMs = remainingMs,
                progress = progress,
                paused = state.focusPaused,
                onPause = vm::pauseFocus,
                onResume = vm::resumeFocus,
                onEnd = vm::endFocus,
                onExtend = { vm.extendFocus(15) }
            )
        } else {
            IdleFocusCard(
                taskTitle = task?.text,
                onChooseTask = { chooseTask = true },
                onStart = { minutes -> vm.startFocus(minutes, task?.id) }
            )
        }
        FocusAppsRow(apps = needNowApps, icons = vm.icons,
            onLaunch = { vm.launch(it) }, onEdit = { editApps = true })
        if (nextActions.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(FocusGlass).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Next for this task", color = FocusGreen, fontFamily = Outfit, fontSize = 15.sp)
            nextActions.forEach { action ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f))
                    .clickable { vm.runHit(action) }.padding(12.dp)) {
                    Text(action.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(action.subtitle, color = Color.White.copy(.6f), fontSize = 12.sp)
                }
            }
        }
        if (!state.focusing) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(FocusGlass)
            .border(1.dp, Color.White.copy(.15f), RoundedCornerShape(22.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's Focus Tasks", color = Color.White, fontFamily = Outfit, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                Text("See all ›", color = FocusGreen, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.clickable { vm.openTodoList() })
            }
            val tasks = state.todos.filter { !it.done && it.space != SpaceKind.Private }.sortedByDescending { it.id == task?.id }.take(if (state.focusing) 2 else 3)
            if (tasks.isEmpty()) Text("Choose one thing to work on.", color = Color.White.copy(.65f), fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
            tasks.forEach { todo ->
                Text((if (todo.id == task?.id) "◉  " else "○  ") + todo.text,
                    color = if (todo.id == task?.id) FocusGreen else Color.White,
                    fontFamily = Outfit, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !state.focusing) { vm.selectFocusTask(todo.id) }.padding(vertical = 10.dp))
            }
        }
        if (!state.focusing) FocusQuote()
    }

    if (editApps) FocusAppPicker(
        apps = state.visibleApps, selected = taskPins,
        icons = vm.icons, excluded = state.focusPins["Exclude:$appScope"].orEmpty(),
        onExclude = { vm.excludeFocusApp(it, appScope) },
        onToggle = { vm.toggleFocusPin(it, appScope) }, onDismiss = { editApps = false }
    )

    if (chooseTask) FocusTaskPicker(
        tasks = state.todos.filter { !it.done && it.space != SpaceKind.Private }, selectedId = task?.id,
        onDismiss = { chooseTask = false },
        onSelect = { vm.selectFocusTask(it); chooseTask = false },
        onAdd = { chooseTask = false; vm.openTodoList() }
    )

}

@Composable
private fun ActiveFocusCard(
    taskTitle: String,
    remainingMs: Long,
    progress: Float,
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onExtend: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val mins = (remainingMs / 60_000L).coerceAtLeast(0)
    val secs = ((remainingMs / 1000L) % 60).toInt()
    val clock = "%d:%02d".format(remainingMs / 60_000L, secs)
    val pct = (progress * 100).toInt().coerceIn(0, 100)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xD9182A20), Color(0xD9121C18), FocusGlass))
            )
            .border(1.dp, FocusGreen.copy(0.35f), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FOCUS",
                color = FocusGreen,
                fontFamily = Outfit,
                fontSize = 11.sp, lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (paused) "Paused" else "Deep Focus",
                color = FocusGreen.copy(0.85f),
                fontFamily = Outfit,
                fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    taskTitle,
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp, lineHeight = 26.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text("One thing at a time.", color = Color.White.copy(.65f), fontFamily = Outfit, fontSize = 12.sp, lineHeight = 16.sp)

            }
            Spacer(Modifier.width(12.dp))
            FocusRing(progress = progress, label = clock, subtitle = if (paused) "Paused" else "Focus Mode")
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FocusActionChip(
                label = if (paused) "Resume" else "Pause",
                icon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                filled = true,
                onClick = if (paused) onResume else onPause,
                modifier = Modifier.weight(1f)
            )
            FocusActionChip(
                label = "End",
                icon = Icons.Outlined.Stop,
                filled = false,
                onClick = onEnd,
                modifier = Modifier.weight(1f)
            )
            FocusActionChip(
                label = "15 min",
                icon = Icons.Outlined.Add,
                filled = false,
                onClick = onExtend,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun IdleFocusCard(
    taskTitle: String?,
    onChooseTask: () -> Unit,
    onStart: (Int) -> Unit
) {
    var duration by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(30) }
    var customTime by remember { mutableStateOf(false) }
    if (customTime) FocusDurationDialog(
        onDismiss = { customTime = false },
        onStart = { duration = it; customTime = false },
        initialMinutes = duration, confirmLabel = "Set time"
    )
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xD9182A20), Color(0xD9121C18), FocusGlass))
            )
            .border(1.dp, FocusGreen.copy(0.28f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "FOCUS",
            color = FocusGreen,
            fontFamily = Outfit,
            fontSize = 11.sp, lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
        Text(
            "One task. Only what you need.",
            color = Color.White.copy(0.7f),
            fontFamily = Outfit,
            fontSize = 13.sp, lineHeight = 17.sp
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(0.06f))
                .clickable(onClick = onChooseTask)
                .padding(14.dp)
        ) {
            Text("Current task", color = Color.White.copy(0.55f), fontFamily = Outfit, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                taskTitle ?: "Choose a task or start a session",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp, lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("Tap to choose ›", color = FocusGreen.copy(0.85f), fontFamily = Outfit, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text("Session length", color = Color.White.copy(0.55f), fontFamily = Outfit, fontSize = 11.sp, lineHeight = 15.sp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(15, 30, 60, 0).forEach { minutes ->
                val selected = if (minutes == 0) duration !in listOf(15, 30, 60) else minutes == duration
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) FocusGreen.copy(0.28f) else Color.White.copy(0.08f))
                        .border(1.dp, if (selected) FocusGreen.copy(0.55f) else Color.White.copy(0.12f), RoundedCornerShape(14.dp))
                        .clickable { if (minutes == 0) customTime = true else duration = minutes }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (minutes == 0) { if (selected) "${duration}m" else "Custom" } else "${minutes}m",
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, lineHeight = 18.sp
                    )
                }
            }
        }
        FocusActionChip("Start focus", Icons.Outlined.PlayArrow, true, { onStart(duration) }, Modifier.fillMaxWidth())
        Text(
            "Focus quiets interruptions. Your settings return when the session ends.",
            color = Color.White.copy(0.45f),
            fontFamily = Outfit,
            fontSize = 11.sp, lineHeight = 15.sp
        )
    }
}

@Composable
private fun FocusRing(progress: Float, label: String, subtitle: String) {
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arc.width) / 2f, (size.height - arc.height) / 2f)
            drawArc(
                color = Color.White.copy(0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(FocusGreenDeep, FocusGreen, FocusGreenDeep)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp)
            Text(subtitle, color = FocusGreen.copy(0.9f), fontFamily = Outfit, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun FocusActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) FocusGreen.copy(0.22f) else Color.White.copy(0.07f))
            .border(1.dp, if (filled) FocusGreen.copy(0.55f) else Color.White.copy(0.14f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (filled) FocusGreen else Color.White.copy(0.9f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.White, fontFamily = Outfit, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FocusAppsRow(
    apps: List<AppInfo>,
    icons: IconCache,
    onLaunch: (AppInfo) -> Unit,
    onEdit: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(FocusGlass)
            .border(0.8.dp, Color.White.copy(0.10f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Need Now",
            modifier = Modifier.weight(1f),
            color = Color.White.copy(0.75f),
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 17.sp
        )
        Text("Edit ›", color = FocusGreen, modifier = Modifier.clickable(onClick = onEdit), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            apps.take(4).forEach { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onLaunch(app) }
                ) {
                    AppIcon(app.packageName, app.activityName, 46.dp, icons, showNotificationBadge = false)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.label,
                        color = Color.White,
                        fontFamily = Outfit,
                        fontSize = 11.sp, lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusQuote() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.FormatQuote, null, tint = FocusGreen.copy(0.8f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Small steps make big progress. Stay focused.",
            color = Color.White.copy(0.65f),
            fontFamily = Outfit,
            fontSize = 12.sp, lineHeight = 16.sp
        )
    }
}

package com.lumen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.data.TodoRepeat
import com.lumen.launcher.data.TodoTime
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

private enum class TaskFilter { Today, Upcoming, Done }
private val Sheet = Color(0xE616121C)

@Composable
fun TodoPage(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onComposerFocus: (Boolean) -> Unit = {}
) {
    val space = state.activeSpace
    val items = state.spaceTodos
    val today = items.filter { TodoTime.isToday(it) }
    val upcoming = items.filter { TodoTime.isUpcoming(it) }
    val done = items.filter { it.done }
    var filter by remember { mutableStateOf(TaskFilter.Today) }
    var draft by remember(space) { mutableStateOf("") }
    var dueAt by remember(space) { mutableLongStateOf(TodoTime.startOfDay()) }
    var priority by remember(space) { mutableStateOf(false) }
    var repeat by remember(space) { mutableStateOf("") }
    var dueEditId by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingRepeat by remember { mutableStateOf(false) }
    var spaceMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val pickerDue = items.find { it.id == dueEditId }?.dueAt ?: dueAt

    LaunchedEffect(state.duePickerTodoId) {
        val id = state.duePickerTodoId
        if (id.isNotBlank()) {
            dueEditId = id
            keyboard?.hide()
            pickingDate = true
            viewModel.clearDuePicker()
        }
    }

    fun applyDue(next: Long) {
        val id = dueEditId
        if (id == null) dueAt = next
        else viewModel.setTodoDue(id, next)
    }

    fun applyRepeat(next: String) {
        val id = dueEditId
        if (id == null) repeat = next
        else viewModel.setTodoRepeat(id, next)
    }

    fun applyPriority(next: Boolean) {
        val id = dueEditId
        if (id == null) priority = next
        else viewModel.setTodoPriority(id, next)
    }

    fun openWhen(id: String? = null) {
        dueEditId = id
        keyboard?.hide()
        pickingDate = true
    }

    fun submit() {
        val text = draft.trim()
        if (text.isBlank()) return
        viewModel.addTodo(text, dueAt, repeat, priority)
        draft = ""
        keyboard?.hide()
        onComposerFocus(false)
        filter = if (dueAt > TodoTime.endOfDay()) TaskFilter.Upcoming else TaskFilter.Today
        dueAt = TodoTime.startOfDay()
        priority = false
        repeat = ""
    }

    fun openComposer() {
        focusRequester.requestFocus()
        keyboard?.show()
        onComposerFocus(true)
    }

    val shown = when (filter) {
        TaskFilter.Today -> today
        TaskFilter.Upcoming -> upcoming
        TaskFilter.Done -> done
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tasks", color = Lumen.Accent, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 32.sp)
                Spacer(Modifier.weight(1f))
                Box {
                    Text(
                        space.title,
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clickable { spaceMenu = true }
                    )
                    DropdownMenu(expanded = spaceMenu, onDismissRequest = { spaceMenu = false }, containerColor = Sheet) {
                        DropdownMenuItem(
                            text = { Text("Auto · follow Home", color = Lumen.Text, fontFamily = Outfit) },
                            onClick = { spaceMenu = false; viewModel.selectSpace(null) }
                        )
                        SpaceKind.entries.filter { it != SpaceKind.Private }.forEach { kind ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        kind.title,
                                        color = if (kind == space && !state.spaceAutomatic) Lumen.Accent else Lumen.Text,
                                        fontFamily = Outfit
                                    )
                                },
                                onClick = { spaceMenu = false; viewModel.selectSpace(kind) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Outlined.PersonOutline) { viewModel.openSettings() }
                    RoundIcon(Icons.Outlined.Settings) { viewModel.openSettings() }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("Today", filter == TaskFilter.Today) { filter = TaskFilter.Today }
                    FilterChip("Upcoming", filter == TaskFilter.Upcoming) { filter = TaskFilter.Upcoming }
                    FilterChip("Done", filter == TaskFilter.Done) { filter = TaskFilter.Done }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.focusing) "End" else "Focus 30",
                    color = if (state.focusing) Lumen.OnAccent else Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (state.focusing) Lumen.Accent else LocalGlass.current.pill)
                        .clickable { if (state.focusing) viewModel.endFocus() else viewModel.startFocus(30) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(LocalGlass.current.pill)
                    .clickable(onClick = { openComposer() })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Lumen.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Lumen.OnAccent, modifier = Modifier.size(18.dp))
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    cursorBrush = SolidColor(Lumen.Accent),
                    textStyle = TextStyle(color = Lumen.Text, fontFamily = Outfit, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            onComposerFocus(it.isFocused)
                            if (it.isFocused) keyboard?.show()
                        },
                    decorationBox = { inner ->
                        Box {
                            if (draft.isBlank()) Text("Add a task…", color = Lumen.Faint, fontFamily = Outfit, fontSize = 16.sp)
                            inner()
                        }
                    }
                )
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { openWhen() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = "Pick date and time",
                        tint = if (TodoTime.hasClock(dueAt) || TodoTime.dayLabel(dueAt) != "Today") Lumen.Text else Lumen.Faint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                )
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { viewModel.openVoice() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.MicNone, contentDescription = "Speak a task", tint = Lumen.Faint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(filter.name, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                Text(if (shown.size == 1) "1 task" else "${shown.size} tasks", color = Lumen.Faint, fontFamily = Outfit, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 76.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (shown.isEmpty()) {
                    Text(
                        when (filter) {
                            TaskFilter.Today -> "Nothing in ${space.title} today."
                            TaskFilter.Upcoming -> "No upcoming ${space.title} tasks."
                            TaskFilter.Done -> "Nothing checked off in ${space.title}."
                        },
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                } else {
                    shown.forEach { item ->
                        TaskRow(
                            item = item,
                            onToggle = { viewModel.toggleTodo(item.id) },
                            onDelete = { viewModel.deleteTodo(item.id) },
                            onDue = {
                                dueEditId = item.id
                                pickingDate = true
                            },
                            onTime = { openWhen(item.id) },
                            onRepeat = {
                                dueEditId = item.id
                                pickingRepeat = true
                            },
                            onPriority = { viewModel.setTodoPriority(item.id, !item.priority) }
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 28.dp)
                .size(62.dp)
                .clip(CircleShape)
                .background(Lumen.Accent)
                .clickable { if (draft.isNotBlank()) submit() else openComposer() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (draft.isNotBlank()) Icons.Outlined.Check else Icons.Outlined.Add,
                contentDescription = "Add task",
                tint = Lumen.OnAccent,
                modifier = Modifier.size(28.dp)
            )
        }
        if (pickingDate) {
            DateSheet(
                initial = pickerDue,
                onDismiss = { pickingDate = false; dueEditId = null },
                onSet = { next ->
                    applyDue(next)
                    pickingDate = false
                    pickingTime = true
                }
            )
        }
        if (pickingTime) {
            TimeSheet(
                initial = pickerDue,
                initialRepeat = items.find { it.id == dueEditId }?.repeat ?: repeat,
                initialPriority = items.find { it.id == dueEditId }?.priority ?: priority,
                onDismiss = { pickingTime = false; dueEditId = null },
                onSet = { hour, minute, nextRepeat, nextPriority ->
                    applyDue(TodoTime.withClock(pickerDue, hour, minute))
                    applyRepeat(nextRepeat)
                    applyPriority(nextPriority)
                    pickingTime = false
                    dueEditId = null
                },
                onAnytime = { nextRepeat, nextPriority ->
                    applyDue(TodoTime.startOfDay(pickerDue))
                    applyRepeat(nextRepeat)
                    applyPriority(nextPriority)
                    pickingTime = false
                    dueEditId = null
                }
            )
        }
        if (pickingRepeat) {
            RepeatSheet(
                initial = items.find { it.id == dueEditId }?.repeat ?: repeat,
                dueAt = pickerDue,
                onDismiss = { pickingRepeat = false; dueEditId = null },
                onSet = { next ->
                    applyRepeat(next)
                    pickingRepeat = false
                    dueEditId = null
                }
            )
        }
    }
}

@Composable
private fun DateSheet(initial: Long, onDismiss: () -> Unit, onSet: (Long) -> Unit) {
    var cursor by remember {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        mutableStateOf(cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH))
    }
    var selected by remember { mutableLongStateOf(TodoTime.startOfDay(initial)) }
    val today = TodoTime.startOfDay()
    val monthCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, cursor.first)
        set(Calendar.MONTH, cursor.second)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val monthTitle = android.text.format.DateFormat.format("MMMM yyyy", monthCal.timeInMillis).toString()
    val firstWeekday = monthCal.get(Calendar.DAY_OF_WEEK) // 1 Sunday
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = firstWeekday - 1
    val cells = leading + daysInMonth

    PickerScaffold(
        icon = Icons.Outlined.CalendarToday,
        title = "Pick date",
        subtitle = "Set a date, then pick a time",
        onClose = onDismiss
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickChip("Today", TodoTime.startOfDay(selected) == today) { selected = today }
            QuickChip("Tomorrow", TodoTime.startOfDay(selected) == TodoTime.tomorrowStart()) { selected = TodoTime.tomorrowStart() }
            QuickChip("This week", TodoTime.startOfDay(selected) == thisWeek()) { selected = thisWeek() }
            QuickChip("Next week", TodoTime.startOfDay(selected) == nextWeek()) { selected = nextWeek() }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(monthTitle, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Outlined.ChevronLeft) {
                cursor = if (cursor.second == 0) (cursor.first - 1) to 11 else cursor.first to (cursor.second - 1)
            }
            Spacer(Modifier.width(6.dp))
            RoundIcon(Icons.Outlined.ChevronRight) {
                cursor = if (cursor.second == 11) (cursor.first + 1) to 0 else cursor.first to (cursor.second + 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(day, color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        val rows = (cells + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val day = index - leading + 1
                    if (day in 1..daysInMonth) {
                        val cell = Calendar.getInstance().apply {
                            timeInMillis = monthCal.timeInMillis
                            set(Calendar.DAY_OF_MONTH, day)
                        }.timeInMillis
                        val isSelected = TodoTime.startOfDay(cell) == TodoTime.startOfDay(selected)
                        val isToday = TodoTime.startOfDay(cell) == today
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { selected = cell },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Lumen.Accent else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    color = if (isSelected) Lumen.OnAccent else Lumen.Text,
                                    fontFamily = Outfit,
                                    fontSize = 15.sp
                                )
                            }
                            if (isToday && !isSelected) {
                                Box(Modifier.size(4.dp).clip(CircleShape).background(Lumen.Accent))
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(LocalGlass.current.pill)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarToday, null, tint = Lumen.Faint, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Selected date", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
                Text(
                    android.text.format.DateFormat.format("EEEE, MMMM d, yyyy", selected).toString(),
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 15.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(TodoTime.dayLabel(selected), color = Lumen.Text, fontFamily = Outfit, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionFrost("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
            ActionFrost("Set date", filled = true, modifier = Modifier.weight(1f)) {
                onSet(TodoTime.keepClock(initial, selected))
            }
        }
    }
}

@Composable
private fun TimeSheet(
    initial: Long,
    initialRepeat: String,
    initialPriority: Boolean,
    onDismiss: () -> Unit,
    onSet: (Int, Int, String, Boolean) -> Unit,
    onAnytime: (String, Boolean) -> Unit
) {
    val startHour = if (TodoTime.hasClock(initial)) TodoTime.hourOf(initial) else 9
    val startMinute = if (TodoTime.hasClock(initial)) TodoTime.minuteOf(initial) else 0
    var hour24 by remember { mutableIntStateOf(startHour) }
    var minute by remember { mutableIntStateOf(startMinute) }
    var anytime by remember { mutableStateOf(!TodoTime.hasClock(initial)) }
    var pickedRepeat by remember { mutableStateOf(initialRepeat) }
    var pickedPriority by remember { mutableStateOf(initialPriority) }
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    val isAm = hour24 < 12
    fun setClock(h12: Int, am: Boolean, min: Int = minute) {
        anytime = false
        hour24 = when {
            h12 == 12 && am -> 0
            h12 == 12 && !am -> 12
            am -> h12
            else -> h12 + 12
        }
        minute = min
    }

    PickerScaffold(
        icon = Icons.Outlined.Schedule,
        title = "Pick time",
        subtitle = "Select a time, or leave it anytime",
        onClose = onDismiss
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(9 to "9:00 AM", 12 to "12:00 PM", 15 to "3:00 PM", 18 to "6:00 PM").forEach { (h, label) ->
                val selected = !anytime && hour24 == h && minute == 0
                QuickChip(label, selected, if (selected) Icons.Outlined.Schedule else null) {
                    anytime = false
                    hour24 = h
                    minute = 0
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%d".format(hour12),
                color = Lumen.Text,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                fontSize = 56.sp
            )
            Text(" : ", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 56.sp)
            Text(
                "%02d".format(minute),
                color = Lumen.Text,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                fontSize = 56.sp
            )
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Lumen.Accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(if (isAm) "AM" else "PM", color = Lumen.OnAccent, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hour", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                WheelColumn(
                    values = (1..12).map { it.toString() },
                    selectedIndex = hour12 - 1,
                    onSelected = { setClock(it + 1, isAm) }
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Minute", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                WheelColumn(
                    values = (0..59).map { "%02d".format(it) },
                    selectedIndex = minute,
                    onSelected = { anytime = false; minute = it }
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                ActionFrost("AM", filled = isAm, modifier = Modifier.width(72.dp)) { setClock(hour12, true) }
                ActionFrost("PM", filled = !isAm, modifier = Modifier.width(72.dp)) { setClock(hour12, false) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(LocalGlass.current.pill)
                .clickable { anytime = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AllInclusive, null, tint = Lumen.Faint, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Anytime", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text("No specific time", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.6.dp, if (anytime) Lumen.Accent else Color.White.copy(alpha = 0.35f), CircleShape)
                    .background(if (anytime) Lumen.Accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (anytime) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Lumen.OnAccent))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Repeat", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "" to "Off",
                TodoRepeat.Daily to "Daily",
                TodoRepeat.Weekly to "Weekly",
                TodoRepeat.Monthly to "Monthly"
            ).forEach { (value, label) ->
                QuickChip(label, pickedRepeat == value, modifier = Modifier.weight(1f)) { pickedRepeat = value }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(LocalGlass.current.pill)
                .clickable { pickedPriority = !pickedPriority }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Flag, null, tint = if (pickedPriority) Lumen.Accent else Lumen.Faint, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Priority", color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(if (pickedPriority) "High priority" else "Normal", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.6.dp, if (pickedPriority) Lumen.Accent else Color.White.copy(alpha = 0.35f), CircleShape)
                    .background(if (pickedPriority) Lumen.Accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (pickedPriority) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Lumen.OnAccent))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionFrost("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
            ActionFrost("Set time", filled = true, modifier = Modifier.weight(1f)) {
                if (anytime) onAnytime(pickedRepeat, pickedPriority) else onSet(hour24, minute, pickedRepeat, pickedPriority)
            }
        }
    }
}

@Composable
private fun RepeatSheet(
    initial: String,
    dueAt: Long,
    onDismiss: () -> Unit,
    onSet: (String) -> Unit
) {
    var selected by remember { mutableStateOf(initial) }
    val options = listOf(
        "" to ("Off" to "Does not repeat"),
        TodoRepeat.Daily to ("Daily" to "Comes back every day"),
        TodoRepeat.Weekly to ("Weekly" to "Every ${TodoRepeat.weekday(dueAt)}"),
        TodoRepeat.Monthly to ("Monthly" to TodoRepeat.detail(TodoRepeat.Monthly, dueAt))
    )
    PickerScaffold(
        icon = Icons.Outlined.Repeat,
        title = "Repeat",
        subtitle = "The task comes back on this schedule",
        onClose = onDismiss
    ) {
        options.forEach { (value, copy) ->
            val on = selected == value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (on) Lumen.Accent.copy(alpha = 0.18f) else LocalGlass.current.pill)
                    .clickable { selected = value }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(copy.first, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Text(copy.second, color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.6.dp, if (on) Lumen.Accent else Color.White.copy(alpha = 0.35f), CircleShape)
                        .background(if (on) Lumen.Accent else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Lumen.OnAccent))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionFrost("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
            ActionFrost("Set repeat", filled = true, modifier = Modifier.weight(1f)) { onSet(selected) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    val itemH = 48.dp
    val loop = values.size
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val start = loop * 12 + selectedIndex - 1
    LaunchedEffect(Unit) { state.scrollToItem(start.coerceAtLeast(0)) }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val center = ((state.firstVisibleItemIndex + 1) % loop + loop) % loop
            if (center != selectedIndex) onSelected(center)
        }
    }
    Box(Modifier.width(88.dp).height(itemH * 3)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemH)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(loop * 24) { i ->
                val idx = i % loop
                val active = idx == selectedIndex
                Box(Modifier.height(itemH), contentAlignment = Alignment.Center) {
                    Text(
                        values[idx],
                        color = if (active) Lumen.Text else Lumen.Faint,
                        fontFamily = Outfit,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Light,
                        fontSize = if (active) 32.sp else 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onClose)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0xF218141E))
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(LocalGlass.current.pill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Lumen.Accent, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(title, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 22.sp)
                    Text(subtitle, color = Lumen.Faint, fontFamily = Outfit, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Lumen.Faint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            content()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Lumen.Accent else LocalGlass.current.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Lumen.OnAccent else Lumen.Text, modifier = Modifier.padding(end = 4.dp).size(13.dp))
        }
        Text(label, color = if (selected) Lumen.OnAccent else Lumen.Text, fontFamily = Outfit, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionFrost(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (filled) Lumen.Accent else LocalGlass.current.pill)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (filled) Lumen.OnAccent else Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Lumen.Accent else LocalGlass.current.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (selected) Lumen.OnAccent else Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(LocalGlass.current.pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onDue: () -> Unit,
    onTime: () -> Unit,
    onRepeat: () -> Unit,
    onPriority: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LocalGlass.current.pill)
            .combinedClickable(onClick = onToggle, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (item.done) Lumen.Accent else Color.Transparent)
                .border(1.4.dp, if (item.done) Lumen.Accent else Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.done) Icon(Icons.Outlined.Check, null, tint = Lumen.OnAccent, modifier = Modifier.size(14.dp))
        }
        if (item.priority) {
            Icon(
                Icons.Outlined.Flag,
                contentDescription = "High priority",
                tint = Lumen.Accent,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(14.dp)
                    .clickable(onClick = onPriority)
            )
        }
        Text(
            item.text,
            color = if (item.done) Lumen.Faint else Lumen.Text,
            fontFamily = Outfit,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)
        )
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onDue)
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Lumen.Faint, modifier = Modifier.size(13.dp))
                Text(
                    item.dueAt?.let { TodoTime.dayLabel(it) } ?: "Today",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 3.dp).clickable(onClick = onTime)
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = Lumen.Faint, modifier = Modifier.size(13.dp))
                Text(
                    item.dueAt?.let { if (TodoTime.hasClock(it)) TodoTime.timeLabel(it) else "Anytime" } ?: "Anytime",
                    color = Lumen.Faint,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (item.repeat.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp).clickable(onClick = onRepeat)
                ) {
                    Icon(Icons.Outlined.Repeat, null, tint = Lumen.Faint, modifier = Modifier.size(13.dp))
                    Text(
                        TodoRepeat.chip(item.repeat),
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
        Box {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "More",
                tint = Lumen.Faint,
                modifier = Modifier.size(18.dp).clickable { menu = true }
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Sheet) {
                DropdownMenuItem(
                    text = { Text("Change date", color = Lumen.Text, fontFamily = Outfit) },
                    onClick = { menu = false; onDue() }
                )
                DropdownMenuItem(
                    text = { Text("Change time", color = Lumen.Text, fontFamily = Outfit) },
                    onClick = { menu = false; onTime() }
                )
                DropdownMenuItem(
                    text = { Text("Repeat", color = Lumen.Text, fontFamily = Outfit) },
                    onClick = { menu = false; onRepeat() }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (item.priority) "Clear priority" else "High priority",
                            color = Lumen.Text,
                            fontFamily = Outfit
                        )
                    },
                    onClick = { menu = false; onPriority() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Lumen.Text, fontFamily = Outfit) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

private fun thisWeek(): Long {
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_WEEK)
    val add = if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) 0 else Calendar.SATURDAY - day
    cal.add(Calendar.DAY_OF_YEAR, add)
    return TodoTime.startOfDay(cal.timeInMillis)
}

private fun nextWeek(): Long = TodoTime.startOfDay() + 7 * 24 * 60 * 60 * 1000L

package com.lumen.launcher.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.launch
import com.lumen.launcher.data.TaskIcon
import com.lumen.launcher.data.TaskAgenda
import com.lumen.launcher.data.CaptureKind
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
private val Frost = Color(0x3D000000)
private val Sheet = Color(0xE616121C)

@Composable
fun TasksRoute(viewModel: LauncherViewModel, onPickerVisible: (Boolean) -> Unit, onComposerFocus: (Boolean) -> Unit) {
    val state by viewModel.tasksState.collectAsStateWithLifecycle()
    TodoPage(state, viewModel, onPickerVisible, onComposerFocus)
}

@Composable
fun TodoPage(
    state: com.lumen.launcher.vm.TasksUiState,
    viewModel: LauncherViewModel,
    onPickerVisible: (Boolean) -> Unit = {},
    onComposerFocus: (Boolean) -> Unit = {}
) {
    val space = state.activeSpace
    val items = state.todos.filter { it.space != SpaceKind.Private || space == SpaceKind.Private }
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while(true) { value=System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }
    val today = TaskAgenda.today(items, now)
    val completedToday = TaskAgenda.completedToday(items, now)
    val tomorrow = TaskAgenda.tomorrow(items, now)
    val upcoming = items.filter { TodoTime.isUpcoming(it, now) }
    val done = items.filter { it.done }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val pendingCompletion = remember { androidx.compose.runtime.mutableStateMapOf<String, TodoItem>() }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var editText by remember { mutableStateOf("") }
    fun changeTask(item: TodoItem, deleting: Boolean = false) {
        if (deleting) viewModel.deleteTodo(item.id)
        else {
            if (!item.done) {
                pendingCompletion[item.id] = item.copy(done = true)
                scope.launch { kotlinx.coroutines.delay(1200); pendingCompletion.remove(item.id) }
            }
            viewModel.toggleTodo(item.id)
        }
        scope.launch {
            val result = snackbar.showSnackbar(if (deleting) "Task deleted" else if (item.done) "Task reopened" else "Task completed", "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                pendingCompletion.remove(item.id)
                viewModel.restoreTodo(item)
            }
        }
    }
    var filter by remember { mutableStateOf(TaskFilter.Today) }
    var selectedDay by remember { mutableLongStateOf(TodoTime.startOfDay()) }
    var calendarAnchor by remember { mutableLongStateOf(TodoTime.startOfDay()) }
    var viewMenu by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }
    var addedMessage by remember { mutableStateOf<String?>(null) }
    var addedView by remember { mutableStateOf(TaskFilter.Today) }
    LaunchedEffect(addedMessage) { if(addedMessage != null) { kotlinx.coroutines.delay(2000); addedMessage=null } }
    var draft by remember(space) { mutableStateOf("") }
    var dateChosen by remember(space) { mutableStateOf(false) }
    var dueAt by remember(space) { mutableLongStateOf(TodoTime.startOfDay()) }
    var priority by remember(space) { mutableStateOf(false) }
    var repeat by remember(space) { mutableStateOf("") }
    var dueEditId by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingRepeat by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(pickingDate, pickingTime, pickingRepeat, editing) {
        onPickerVisible(pickingDate || pickingTime || pickingRepeat || editing != null)
        onDispose { onPickerVisible(false) }
    }
    var spaceMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val imeBottom = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    val pickerRequested = pickingDate || pickingTime || pickingRepeat
    LaunchedEffect(pickerRequested) {
        if (pickerRequested) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            onComposerFocus(false)
        }
    }
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
        if (id == null) { dueAt = next; dateChosen = true }
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
        addedView = if (dueAt > TodoTime.endOfDay()) TaskFilter.Upcoming else TaskFilter.Today
        addedMessage = "Task added to ${TodoTime.dayLabel(dueAt)}" + if(TodoTime.hasClock(dueAt)) " at ${TodoTime.timeLabel(dueAt)}" else " \u00b7 Anytime"
        dueAt = TodoTime.startOfDay()
        dateChosen = false
        priority = false
        repeat = ""
    }

    fun openComposer() {
        focusRequester.requestFocus()
        keyboard?.show()
        onComposerFocus(true)
    }

    val shown = when (filter) {
        TaskFilter.Today -> if (TodoTime.startOfDay(selectedDay) != TodoTime.startOfDay(now)) items.filter { !it.done && it.dueAt?.let(TodoTime::startOfDay) == TodoTime.startOfDay(selectedDay) } else TaskAgenda.today((today + pendingCompletion.values.map { it.copy(done = false) }).distinctBy { it.id }, now)
            .map { pendingCompletion[it.id] ?: it }
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
                .padding(horizontal = 10.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            ReferenceTaskWeek(calendarAnchor, selectedDay, items,
                onNavigate = { step, month ->
                    calendarAnchor = if (step == 0) TodoTime.startOfDay(now) else Calendar.getInstance().apply {
                        timeInMillis = calendarAnchor
                        add(if (month) Calendar.MONTH else Calendar.WEEK_OF_YEAR, step)
                    }.timeInMillis
                    if (step == 0) { selectedDay = TodoTime.startOfDay(now); filter = TaskFilter.Today }
                }, onSelect = { selectedDay = it; filter = TaskFilter.Today })
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tasks", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                    Text(if (filter == TaskFilter.Today) android.text.format.DateFormat.format("EEEE, MMM d", selectedDay).toString() else filter.name,
                        color = Color.White.copy(alpha = .85f), fontFamily = Outfit, fontSize = 16.sp)
                }
                Text("${shown.size} ${if (shown.size == 1) "task" else "tasks"}", color = Color.White, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.clickable(onClickLabel = "Choose task view") { viewMenu = true })
                Box {
                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }, containerColor = Sheet, shape = RoundedCornerShape(18.dp)) {
                        TaskFilter.entries.forEach { choice -> DropdownMenuItem(text = { Text(choice.name, color = Lumen.Text) }, onClick = {
                            filter = choice; selectedDay = TodoTime.startOfDay(now); viewMenu = false
                        }) }
                        DropdownMenuItem(text = { Text("Settings", color = Lumen.Text) }, onClick = { viewMenu = false; viewModel.openSettings() })
                    }
                }
                Spacer(Modifier.width(6.dp))
                RoundIcon(Icons.Outlined.Settings) { viewModel.openSettings() }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .glass(RoundedCornerShape(14.dp), LocalGlass.current)
                    .clickable(onClick = { openComposer() })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            if (draft.isBlank()) Text("Add a task\u2026", color = Lumen.Faint, fontFamily = Outfit, fontSize = 16.sp)
                            inner()
                        }
                    }
                )
                Box(Modifier.size(36.dp).clickable(onClickLabel = if (draft.isNotBlank()) "Add task" else "Speak a task") {
                    if (draft.isNotBlank()) submit() else viewModel.voice.openVoice()
                }, contentAlignment = Alignment.Center) {
                    Icon(if (draft.isNotBlank()) Icons.Outlined.Check else Icons.Outlined.MicNone,
                        if (draft.isNotBlank()) "Add task" else "Speak a task", tint = Color(0xFFF5CB7E), modifier = Modifier.size(24.dp))
                }
            }
            }
            Spacer(Modifier.height(8.dp))
            if(draft.isNotBlank()) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                QuickChip(if(TodoTime.hasClock(dueAt)) TodoTime.timeLabel(dueAt) else "Time", TodoTime.hasClock(dueAt), Icons.Outlined.Schedule) { dueEditId=null; pickingTime=true }
                QuickChip(if(dateChosen) TodoTime.dayLabel(dueAt) else "Date", false, Icons.Outlined.CalendarMonth) { openWhen() }
                QuickChip("Priority", priority, Icons.Outlined.Flag) { priority=!priority }
            }
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 76.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filter == TaskFilter.Today && TodoTime.startOfDay(selectedDay) == TodoTime.startOfDay(now)) {
                    AnimatedContent(targetState = today.isEmpty() && pendingCompletion.isEmpty(), label = "Today state") { clear ->
                        if (clear) TaskCalmHero(completedToday.size) {
                            dueAt = TodoTime.tomorrowStart(); dateChosen = true; openComposer()
                        }
                    }
                } else if (shown.isEmpty()) {
                    Text(if (filter == TaskFilter.Upcoming) "No upcoming tasks yet" else if (filter == TaskFilter.Today) "No tasks for this date" else "Completed tasks will appear here", color = Lumen.Text, fontFamily = Outfit, fontSize = 14.sp)
                }
                Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shown.forEach { item ->
                        androidx.compose.runtime.key(item.id) {
                            TaskRow(item = item, todayView = filter == TaskFilter.Today,
                                overdue = TaskAgenda.overdue(item, now),
                                onToggle = { changeTask(item) }, onDelete = { changeTask(item, deleting = true) },
                                onEdit = { editing = item; editText = item.text },
                                onDue = { dueEditId = item.id; pickingDate = true },
                                onTime = { dueEditId = item.id; pickingTime = true },
                                onRepeat = { dueEditId = item.id; pickingRepeat = true },
                                onPriority = { viewModel.setTodoPriority(item.id, !item.priority) })
                        }
                    }
                }
                if (filter == TaskFilter.Today && TodoTime.startOfDay(selectedDay) == TodoTime.startOfDay(now)) {
                    if (completedToday.any { it.id !in pendingCompletion }) {
                        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), LocalGlass.current)
                            .clickable { completedExpanded = !completedExpanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Check, null, tint = Lumen.Accent, modifier = Modifier.size(24.dp))
                            Text("Completed today  (${completedToday.size})", color = Lumen.Muted, fontFamily = Outfit, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Text(if (completedExpanded) "\u2303" else "\u2304", color = Lumen.Muted, fontSize = 20.sp)
                        }
                        if (completedExpanded) completedToday.filter { it.id !in pendingCompletion }.forEach { item ->
                            androidx.compose.runtime.key("done-${item.id}") {
                                TaskRow(item, true, false, onToggle = { changeTask(item) }, onDelete = { changeTask(item, true) },
                                    onEdit = { editing = item; editText = item.text }, onDue = { openWhen(item.id) },
                                    onTime = { dueEditId = item.id; pickingTime = true }, onRepeat = { dueEditId = item.id; pickingRepeat = true },
                                    onPriority = { viewModel.setTodoPriority(item.id, !item.priority) })
                            }
                        }
                    }

                }

            }
        }
        androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 44.dp))
        editing?.let { item ->
            androidx.compose.material3.AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit task") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.OutlinedTextField(editText, { editText = it }, label = { Text("Task") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickChip("Date", false, Icons.Outlined.CalendarMonth) { viewModel.renameTodo(item.id, editText); editing = null; openWhen(item.id) }
                        QuickChip("Time", false, Icons.Outlined.Schedule) { viewModel.renameTodo(item.id, editText); editing = null; dueEditId = item.id; pickingTime = true }
                    }
                }
            }, confirmButton = { androidx.compose.material3.TextButton(onClick = { viewModel.renameTodo(item.id, editText); editing = null }) { Text("Save") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("Cancel") } })
        }
        addedMessage?.let { message ->
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                .padding(start=24.dp,end=24.dp,bottom=48.dp)
                .background(Color(0xF212182A),RoundedCornerShape(20.dp))
                .border(1.dp,Color.White.copy(alpha=.35f),RoundedCornerShape(20.dp))
                .padding(horizontal=18.dp,vertical=12.dp)) {
                Text(message,color=Lumen.Text,fontFamily=Outfit,fontSize=14.sp,textAlign=TextAlign.Center)
            }
        }
        if (pickingDate && imeBottom == 0) {
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
        if (pickingTime && imeBottom == 0) {
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
        if (pickingRepeat && imeBottom == 0) {
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
    var selected by remember { mutableLongStateOf(maxOf(TodoTime.startOfDay(initial),TodoTime.startOfDay())) }
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
            QuickChip("Weekend", selected == nextWeekday(Calendar.SATURDAY)) { selected = nextWeekday(Calendar.SATURDAY) }
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
                                .clickable(enabled = cell >= today) { selected = cell },
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
                                    color = if(cell < today) Lumen.Faint.copy(alpha=.3f) else if (isSelected) Lumen.OnAccent else Lumen.Text,
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
                .background(Frost)
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
    val nextRound = remember { Calendar.getInstance().apply { set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0); add(Calendar.MINUTE,30-get(Calendar.MINUTE)%30) }.timeInMillis }
    val defaultTime = if(TodoTime.hasClock(initial) && initial > System.currentTimeMillis()) initial else nextRound
    val startHour = TodoTime.hourOf(defaultTime)
    val startMinute = TodoTime.minuteOf(defaultTime)
    var hour24 by remember { mutableIntStateOf(startHour) }
    var minute by remember { mutableIntStateOf(startMinute) }
    var anytime by remember { mutableStateOf(false) }
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
            listOf(9 to "9:00 AM", 12 to "12:00 PM", 15 to "3:00 PM", 18 to "6:00 PM").filter { (h, _) -> TodoTime.withClock(initial,h,0) > System.currentTimeMillis() }.forEach { (h, label) ->
                val selected = !anytime && hour24 == h && minute == 0
                QuickChip(label, selected, if (selected) Icons.Outlined.Schedule else null) {
                    anytime = false
                    hour24 = h
                    minute = 0
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha=if(anytime) .35f else 1f },
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
                .background(Frost)
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
                    .background(if (on) Lumen.Accent.copy(alpha = 0.18f) else Frost)
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
                .background(Brush.linearGradient(listOf(Color(0xBDA5B0D8),Color(0xAD4A5680),Color(0xAD6E7CFF))),RoundedCornerShape(topStart=30.dp,topEnd=30.dp))
                .border(1.dp,Color.White.copy(alpha=.35f),RoundedCornerShape(topStart=30.dp,topEnd=30.dp))
                .padding(14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF212182A))
                .border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(24.dp))
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
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Frost),
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
            .background(if (selected) Lumen.Accent else Frost)
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
            .background(if (filled) Lumen.Accent else Frost)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (filled) Lumen.OnAccent else Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(24.dp))
        .background(if (selected) Brush.horizontalGradient(listOf(Lumen.Accent.copy(alpha = .78f), Color(0xFF7365AC))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
        .border(1.dp, if (selected) Lumen.Accent.copy(alpha = .5f) else Color.Transparent, RoundedCornerShape(24.dp))
        .clickable(onClick = onClick).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else Lumen.Muted, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
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
    todayView: Boolean,
    overdue: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDue: () -> Unit,
    onTime: () -> Unit,
    onRepeat: () -> Unit,
    onPriority: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val tint = when (item.space) {
        SpaceKind.Home -> Color(0xFFF5CB7E)
        SpaceKind.Work -> Color(0xFF9CBFEF)
        SpaceKind.Personal -> Color(0xFFD3ACF2)
        SpaceKind.Travel -> Color(0xFFAC1749)
        SpaceKind.Focus -> Color(0xFFA7CDB7)
        SpaceKind.Private -> Color(0xFFBAC4DB)
    }
    val tagText = if (item.space == SpaceKind.Travel) Color(0xFFFFDCE6) else tint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .glass(RoundedCornerShape(13.dp), LocalGlass.current)
            .graphicsLayer { alpha = if (item.done) .65f else 1f }
            .combinedClickable(onClick = onEdit, onLongClick = { menu = true })
            .padding(horizontal = 12.dp, vertical = if (item.done) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).border(1.4.dp, if (item.done) tint else Color.White.copy(alpha = .6f), CircleShape)
            .toggleable(value = item.done, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onToggle() }), contentAlignment = Alignment.Center) {
            if (item.done) Icon(Icons.Outlined.Check, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        if (!item.done) Icon(taskLineIcon(item.text), null, tint = Color(0xFFF5CB7E).copy(alpha = .85f), modifier = Modifier.padding(start = 10.dp).size(24.dp))
        Column(Modifier.weight(1f).padding(start=14.dp,end=8.dp)) {
        Text(
            item.text,
            color = if (item.done) Lumen.Faint else Lumen.Text,
            fontFamily = Outfit,
            fontSize = if (item.done) 13.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier
        )
        val due = item.dueAt
        val metadata = buildList {
            if (due != null && (!todayView || TodoTime.startOfDay(due) != TodoTime.startOfDay())) add(TodoTime.dayLabel(due))
            if (due != null && TodoTime.hasClock(due)) add(TodoTime.timeLabel(due))
            if (due == null || !TodoTime.hasClock(due)) add("Anytime")
            if (overdue) add("Overdue")
            if (item.repeat.isNotBlank()) add(TodoRepeat.chip(item.repeat))
        }.joinToString(" \u00B7 ")
        Row(Modifier.padding(top = 4.dp).clickable(onClick = onDue), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, tint = if (overdue) Color(0xFFFF8A80) else Lumen.Muted, modifier = Modifier.size(12.dp))
            Text(metadata, color = if (overdue) Color(0xFFFF8A80) else Lumen.Muted,
                fontFamily = Outfit, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 5.dp))
        }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Icon(
                Icons.Outlined.MoreHoriz,
                contentDescription = "More",
                tint = Lumen.Faint,
                modifier = Modifier.size(18.dp).clickable { menu = true }
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Sheet, shape = RoundedCornerShape(22.dp)) {
                DropdownMenuItem(text = { Text(if (item.done) "Mark as not done" else "Mark as done", color = Lumen.Text, fontFamily = Outfit) },
                    onClick = { menu = false; onToggle() })
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
            Text(item.space.title, color = tagText, fontFamily = Outfit, fontSize = 10.sp, maxLines = 1,
                modifier = Modifier.padding(top = 6.dp).background(tint.copy(alpha = if (item.space == SpaceKind.Travel) .65f else .12f), RoundedCornerShape(9.dp))
                    .border(.7.dp, tint.copy(alpha = .25f), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

private fun taskLineIcon(text: String): ImageVector = when (TaskIcon.symbol(text)) {
    "\uD83D\uDCDE" -> Icons.Outlined.Call
    "\uD83D\uDC8A" -> Icons.Outlined.Medication
    "\uD83D\uDCB3" -> Icons.Outlined.CreditCard
    "\uD83D\uDED2" -> Icons.Outlined.ShoppingCart
    "\uD83D\uDC65" -> Icons.Outlined.Groups
    "\uD83D\uDCDD" -> Icons.Outlined.Description
    else -> Icons.Outlined.Assignment
}

private fun thisWeek(): Long {
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_WEEK)
    val add = if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) 0 else Calendar.SATURDAY - day
    cal.add(Calendar.DAY_OF_YEAR, add)
    return TodoTime.startOfDay(cal.timeInMillis)
}

private fun nextWeek(): Long = TodoTime.startOfDay() + 7 * 24 * 60 * 60 * 1000L

private fun taskOverdue(item: TodoItem, now: Long): Boolean {
    val due = item.dueAt ?: return false
    return !item.done && if(TodoTime.hasClock(due)) due < now else TodoTime.startOfDay(due) < TodoTime.startOfDay(now)
}

private fun nextWeekday(day: Int): Long = Calendar.getInstance().apply {
    timeInMillis=TodoTime.startOfDay()
    do { add(Calendar.DAY_OF_YEAR,1) } while(get(Calendar.DAY_OF_WEEK) != day)
}.timeInMillis

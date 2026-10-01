package com.lumen.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.*
import com.lumen.launcher.inbox.InboxSource
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.travel.TripAlbumScreen
import com.lumen.launcher.travel.TripModeCard
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** One useful workspace per mode, with explicit setup instead of fabricated content. */
@Composable
fun ModeWorkspace(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    var chooseApps by remember(state.activeSpace) { mutableStateOf(false) }
    var editTrip by remember { mutableStateOf(false) }
    var chooseTask by remember { mutableStateOf(false) }
    var customFocus by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // Only tick while Focus is active ? avoids recomposing Travel / Trip Mode every second (flicker).
    LaunchedEffect(state.activeSpace, state.focusUntil) {
        if (state.activeSpace != SpaceKind.Focus && state.focusUntil <= System.currentTimeMillis()) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (state.focusUntil <= now && state.activeSpace != SpaceKind.Focus) break
            delay(1000)
        }
    }
    val activeFocus = state.focusUntil > now
    val task = state.spaceTodos.firstOrNull { !it.done }
    val event = state.upcomingEvents.firstOrNull { it.end > now }
    val message = state.inbox.firstOrNull {
        !it.isDigest && it.source in setOf(InboxSource.Messages, InboxSource.WhatsApp, InboxSource.Telegram, InboxSource.Messenger)
    }
    var travelCollection by remember { mutableStateOf<Boolean?>(null) }
    var albumTrip by remember { mutableStateOf<Trip?>(null) }
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), LocalGlass.current).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when (state.activeSpace) {
                SpaceKind.Home -> "What's next"
                SpaceKind.Work -> "Task + next meeting"
                SpaceKind.Personal -> "Connect + create"
                SpaceKind.Focus -> "One thing at a time"
                SpaceKind.Travel -> "Your journey"
                SpaceKind.Private -> "Private"
            }, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 17.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { chooseApps = true }) { Text("Apps") }
        }
        when (state.activeSpace) {
            SpaceKind.Home -> {
                val next = state.upNext
                if (next != null) WorkspaceLine(next.title, next.detail) { vm.openUpNext(next) }
                else if (event != null) WorkspaceLine(event.title, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.begin))) { vm.openCalendarEvent(event) }
                else Text("A little space for your day.", color = Lumen.Muted)
                task?.let { WorkspaceLine(it.text, "Next task ù tap to view") { vm.openTodoList() } }
                WorkspaceActions {
                    OutlinedButton(onClick = { vm.openCapture(CaptureKind.Reminder) }) { Text("Reminder") }
                    OutlinedButton(onClick = { if (state.calendarAccess) vm.openCalendarApp() else vm.requestCalendarAccess() }) { Text(if (state.calendarAccess) "Calendar" else "Connect calendar") }
                }
            }
            SpaceKind.Work -> {
                WorkspaceLine(task?.text ?: "Choose your next task", "Tap to manage work tasks") { vm.openTodoList() }
                if (event != null) WorkspaceLine(event.title, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.begin)) + " ù Open meeting") { vm.openCalendarEvent(event) }
                else Text(if (state.calendarAccess) "No upcoming meeting." else "Connect your calendar to see your next meeting.", color = Lumen.Muted, fontSize = 12.sp)
                WorkspaceActions {
                    OutlinedButton(onClick = { vm.openCapture(CaptureKind.Task) }) { Text("Add task") }
                    OutlinedButton(onClick = { vm.openCapture(CaptureKind.Note) }) { Text("Meeting note") }
                    OutlinedButton(onClick = { vm.startFocus(25, task?.id) }) { Text("Focus 25m") }
                    if (event != null && event.begin <= now + 15 * 60_000L && event.location.trim().startsWith("https://")) {
                        OutlinedButton(onClick = { vm.joinWorkspaceMeeting(event) }) { Text("Join meeting") }
                    }
                    if (!state.calendarAccess) OutlinedButton(onClick = vm::requestCalendarAccess) { Text("Calendar access") }
                }
            }
            SpaceKind.Personal -> {
                if (!state.inboxAccess) WorkspaceLine("Connect your messages", "Allow notification access for recent message previews") { vm.requestInboxAccess() }
                else if (message != null) WorkspaceLine(message.title, message.preview) { vm.openInboxItem(message) }
                else Text("You're caught up. Make something worth sharing.", color = Lumen.Muted)
                WorkspaceActions {
                    OutlinedButton(onClick = { vm.openSocialTool(SocialCreateTool.Quote) }) { Text("Quote") }
                    OutlinedButton(onClick = { vm.openSocialTool(SocialCreateTool.Scribble) }) { Text("Scribble") }
                    OutlinedButton(onClick = { vm.openSocialTool(SocialCreateTool.Photo) }) { Text("Photo") }
                    OutlinedButton(onClick = { vm.setRecentsOpen(true) }) { Text("Create studio") }
                }
            }
            SpaceKind.Focus -> {
                WorkspaceLine(state.focusTask?.text ?: "What are you working on?", "Choose one task") { chooseTask = true }
                if (activeFocus) {
                    val seconds = ((state.focusUntil - now) / 1000).coerceAtLeast(0)
                    Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = Lumen.Text, fontFamily = Outfit, fontSize = 30.sp)
                }
                Text("Focus silences calls and notification interruptions. Your previous settings return when the timer ends, with a completion alert. Alarms and media stay available.", color = Lumen.Muted, fontSize = 12.sp)
                WorkspaceActions {
                    if (activeFocus) OutlinedButton(onClick = vm::endFocus) { Text("End session") }
                    else {
                        OutlinedButton(onClick = { vm.startFocus(25, state.focusTask?.id) }) { Text("Start 25m") }
                        OutlinedButton(onClick = { vm.startFocus(50, state.focusTask?.id) }) { Text("Start 50m") }
                        OutlinedButton(onClick = { customFocus = true }) { Text("Custom") }
                    }
                    OutlinedButton(onClick = { vm.selectSpace(SpaceKind.Home) }) { Text("Back to Home") }
                }
            }
            SpaceKind.Travel -> {
                TripModeCard(onViewTrip = { albumTrip = it })
                TravelNavigationBlock(
                    stops = state.travelStops,
                    lastSelectedId = state.lastTravelStopId,
                    vm = vm
                )
                state.upNext?.takeIf { it.kind == UpNext.Kind.Flight }?.let { flight ->
                    WorkspaceLine(flight.title, flight.detail) { vm.openUpNext(flight) }
                }
                WorkspaceActions {
                    OutlinedButton(onClick = { travelCollection = false }) { Text("Documents (" + state.travelAttachments.size + ")") }
                    OutlinedButton(onClick = { vm.openCapture(CaptureKind.Reminder) }) { Text("Departure reminder") }
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com"))) }
                            .onFailure { error = "No browser is available to open translation." }
                    }) { Text("Translate") }
                }
                state.spaceTodos.firstOrNull { !it.done && it.dueAt != null }?.let {
                    WorkspaceLine(it.text, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.dueAt!!))) { vm.openTodoList() }
                }
            }
            else -> Unit
        }
        if (state.doubleAction.isBlank()) Text(when (state.activeSpace) {
            SpaceKind.Home -> "Double-tap TouchPad for what's next"
            SpaceKind.Work -> "Double-tap TouchPad to capture a task"
            SpaceKind.Personal -> "Double-tap TouchPad to create"
            SpaceKind.Focus -> "Double-tap TouchPad to start or end a session"
            SpaceKind.Travel -> "Double-tap TouchPad to navigate"
            else -> ""
        }, color = Lumen.Faint, fontSize = 11.sp)
        if (state.homeApps.isEmpty()) TextButton(onClick = { chooseApps = true }) { Text("Choose apps for ${state.activeSpace.title}") }
    }
    travelCollection?.let { itinerary ->
        TravelCollection(state, vm, onDismiss = { travelCollection = null })
    }
    albumTrip?.let { trip ->
        TripAlbumScreen(trip = trip, onClose = { albumTrip = null })
    }
    if (customFocus) FocusDurationDialog(
        onDismiss = { customFocus = false },
        onStart = { minutes -> customFocus = false; vm.startFocus(minutes, state.focusTask?.id) }
    )
    if (chooseApps) ModeAppPicker(state, vm.icons, onDismiss = { chooseApps = false }, onSave = { vm.saveModeApps(state.activeSpace, it); chooseApps = false })
    if (editTrip) {
        var destination by remember { mutableStateOf(state.travelDestination) }
        AlertDialog(onDismissRequest = { editTrip = false }, title = { Text("Travel destination") }, text = {
            OutlinedTextField(destination, { destination = it }, label = { Text("Address, city or place") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { vm.saveTravel(destination, state.travelTicket); editTrip = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editTrip = false }) { Text("Cancel") } })
    }
    if (chooseTask) AlertDialog(onDismissRequest = { chooseTask = false }, title = { Text("Choose a focus task") }, text = {
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(state.todos.filterNot { it.done }, key = { it.id }) { todo ->
                Text(todo.text, Modifier.fillMaxWidth().clickable { vm.selectFocusTask(todo.id); chooseTask = false }.padding(12.dp))
            }
        }
    }, confirmButton = { TextButton(onClick = { chooseTask = false; vm.openCapture(CaptureKind.Task) }) { Text("New task") } }, dismissButton = { TextButton(onClick = { chooseTask = false }) { Text("Cancel") } })
    error?.let { messageText -> AlertDialog(onDismissRequest = { error = null }, text = { Text(messageText) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable
private fun WorkspaceLine(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(title, color = Lumen.Text, fontFamily = Outfit, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun WorkspaceActions(content: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    val containScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.fillMaxWidth()
        .nestedScroll(containScroll)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadeWidth = minOf(24.dp.toPx(), size.width / 2)
            // Mask only the viewport edges that have more actions beyond them.
            if (if (rtl) scroll.canScrollForward else scroll.canScrollBackward) {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fadeWidth),
                    size = Size(fadeWidth, size.height), blendMode = BlendMode.DstIn
                )
            }
            if (if (rtl) scroll.canScrollBackward else scroll.canScrollForward) {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fadeWidth, size.width),
                    topLeft = Offset(size.width - fadeWidth, 0f),
                    size = Size(fadeWidth, size.height), blendMode = BlendMode.DstIn
                )
            }
        }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}


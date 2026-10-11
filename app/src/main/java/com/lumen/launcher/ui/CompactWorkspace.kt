package com.lumen.launcher.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.*
import com.lumen.launcher.media.NowPlayingRepository
import com.lumen.launcher.ui.focus.FocusTouchpadButton
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactWorkspace(state: LauncherUiState, vm: LauncherViewModel, touchpad: @Composable () -> Unit) {
    val context = LocalContext.current
    val media by NowPlayingRepository.state.collectAsStateWithLifecycle()
    val expanded = state.workspaceOpen
    val travelPrefs = remember { context.getSharedPreferences("travel_discovery", android.content.Context.MODE_PRIVATE) }
    var travelDiscovered by remember { mutableStateOf(travelPrefs.getBoolean("opened", false)) }
    val travelPulse = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(state.activeSpace, travelDiscovered) {
        if (state.activeSpace == SpaceKind.Travel && !travelDiscovered) {
            repeat(2) {
                travelPulse.animateTo(1f, androidx.compose.animation.core.tween(850))
                travelPulse.animateTo(0f, androidx.compose.animation.core.tween(850))
            }
        }
    }
    LaunchedEffect(expanded, state.activeSpace) {
        if (expanded && state.activeSpace == SpaceKind.Travel) {
            travelPrefs.edit().putBoolean("opened", true).apply()
            travelDiscovered = true
        }
    }

    var boardingPasses by remember(state.activeSpace, state.homePulse) { mutableStateOf(false) }
    var travelCategory by remember(state.activeSpace, state.homePulse) { mutableStateOf<TravelCategory?>(null) }
    val savedCategories = TravelCategory.entries.filter { category -> state.travelAttachments.any { it.travelCategory == category } }
    val showPasses = state.activeSpace == SpaceKind.Travel && savedCategories.isNotEmpty()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { NowPlayingRepository.start(context) }
    LaunchedEffect(state.focusUntil) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    LaunchedEffect(state.activeSpace, state.homePulse, state.sheet, state.pagerPage, state.socialCreateTool, state.recentsOpen) { vm.setWorkspaceOpen(false) }
    val task = state.spaceTodos.firstOrNull { !it.done && (state.activeSpace != SpaceKind.Home || it.dueAt == null || TodoTime.startOfDay(it.dueAt) <= TodoTime.startOfDay(now)) }
    val event = EventPresentation.next(state.upcomingEvents, now, state.activeSpace, state.workCalendars, state.personalCalendars)
    val upcomingTasks = state.todos.filter { !it.done && it.space != SpaceKind.Private && it.dueAt != null && TodoTime.startOfDay(it.dueAt!!) > TodoTime.startOfDay(now) }
    val message = state.inbox.firstOrNull { !it.isDigest && it.source in setOf(
        com.lumen.launcher.inbox.InboxSource.Messages, com.lumen.launcher.inbox.InboxSource.WhatsApp,
        com.lumen.launcher.inbox.InboxSource.Telegram, com.lumen.launcher.inbox.InboxSource.Messenger
    ) }
    val focusing = state.focusUntil > now
    val seconds = ((state.focusUntil - now) / 1000).coerceAtLeast(0)
    val workEvening = EventPresentation.evening(now)
    val night = TodoTime.hourOf(now) >= 21 || TodoTime.hourOf(now) < 5
    fun taskDetail(item: TodoItem): String {
        val day = if (TaskAgenda.overdue(item, now)) "Overdue" else item.dueAt?.let { TodoTime.dayLabel(it, now) } ?: "Today"
        val time = item.dueAt?.let { if (TodoTime.hasClock(it)) TodoTime.timeLabel(it) else "Anytime" } ?: "Anytime"
        return "$day \u00B7 $time"
    }
    val title = when (state.activeSpace) {
        SpaceKind.Home -> event?.title ?: task?.text ?: if (upcomingTasks.isNotEmpty()) "${upcomingTasks.size} ${if (upcomingTasks.size == 1) "task" else "tasks"} in Upcoming" else if (night) "Plan tomorrow" else "Your day is clear"
        SpaceKind.Work -> task?.text ?: event?.title ?: if (workEvening) "Work's done" else "Your work day"
        SpaceKind.Personal -> message?.title ?: "Make something worth sharing"
        SpaceKind.Focus -> if (focusing) "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "One thing at a time"
        SpaceKind.Travel -> "Where to next?"
        SpaceKind.Private -> "Your private space"
    }
    val detail = when (state.activeSpace) {
        SpaceKind.Home -> event?.let { EventPresentation.whenText(it, now) } ?: if (task != null) taskDetail(task) else upcomingTasks.minByOrNull { it.dueAt!! }?.text ?: if (night) "A calmer morning" else "Capture what\u2019s next."
        SpaceKind.Work -> if (task != null) taskDetail(task) else event?.let { EventPresentation.whenText(it, now) } ?: if (workEvening) "Plan tomorrow" else "Add a task or note"
        SpaceKind.Personal -> message?.preview ?: "Messages and your creation studio."
        SpaceKind.Focus -> state.focusTask?.text ?: "Choose a task. Take your time."
        SpaceKind.Travel -> if (state.travelAttachments.isNotEmpty()) "${state.travelAttachments.size} saved travel items" else "Destination, tickets and itinerary."
        SpaceKind.Private -> "Only for you."
    }
    val shape = RoundedCornerShape(26.dp)
    val showMedia = media?.playing == true && state.activeSpace != SpaceKind.Focus && state.activeSpace != SpaceKind.Travel
    val showCapture = state.activeSpace !in setOf(SpaceKind.Focus, SpaceKind.Travel)
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Row(
        Modifier.fillMaxWidth().height(152.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.16f), Color.White.copy(0.07f))))
            .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(0.35f), Color.White.copy(0.10f))), shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Equal, independent touch regions. No buttons or media extend under the TouchPad.
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { touchpad() }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(Color.White.copy(0.20f)))
        if (state.focusing) {
            // Active Focus session � replace the right panel with a return-to-Focus control.
            Box(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                FocusTouchpadButton(
                    state = state,
                    onOpenFocus = { vm.selectSpace(SpaceKind.Focus) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (state.activeSpace == SpaceKind.Travel) {
            TravelPadPanel(state, vm, Modifier.weight(1f).fillMaxHeight())
        } else {
            Column(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .then(if (state.activeSpace == SpaceKind.Travel && !travelDiscovered) Modifier.background(Color(0xFFAA1748).copy(alpha = travelPulse.value * .23f)).border(1.dp, Color(0xFFEB91B1).copy(alpha = travelPulse.value * .8f), RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(onClickLabel = "Open ${state.activeSpace.title} controls") { vm.setWorkspaceOpen(true) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    if (state.activeSpace == SpaceKind.Travel && !showPasses) {
                        TravelMapEmblem(Modifier.align(Alignment.CenterHorizontally).size(36.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.activeSpace.title.uppercase(), color = Lumen.Text.copy(alpha = 0.72f), fontFamily = Outfit,
                            fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Lumen.Text.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                    }
                    Text(title, color = Lumen.Text, fontFamily = Outfit, fontWeight = FontWeight.Medium,
                        fontSize = if (state.activeSpace == SpaceKind.Focus && focusing) 28.sp else 16.sp,
                        lineHeight = if (state.activeSpace == SpaceKind.Focus && focusing) 32.sp else 19.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.activeSpace == SpaceKind.Travel && !travelDiscovered) Text("Try me >", color = Color(0xFFFFBED2), fontFamily = Outfit, fontSize = 11.sp)
                    if (state.activeSpace != SpaceKind.Travel) Text(detail, color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp,
                        lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                }
                if (showPasses) {
                    WorkspaceActions {
                        savedCategories.forEach { group ->
                            Column(Modifier.width(52.dp).clip(RoundedCornerShape(12.dp)).clickable {
                                val items = state.travelAttachments.filter { it.travelCategory == group }
                                if (items.size == 1 && openTravelItem(context, state, vm, items.single())) {
                                    // A single saved app, link or file opens immediately.
                                } else { travelCategory = group; boardingPasses = true }
                            }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                TravelBadge(group)
                                Text(if (group == TravelCategory.Flights) "Flights" else if (group == TravelCategory.Other) "Other" else group.title,
                                    color = Lumen.Text, fontSize = 9.sp, maxLines = 2,
                                    modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                }
                if (showCapture && !showMedia) {
                    HorizontalDivider(color = Color.White.copy(.16f))
                    Row(Modifier.fillMaxWidth().height(40.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("+ Task" to CaptureKind.Task, "+ Note" to CaptureKind.Note).forEach { (label, kind) ->
                            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = .09f))
                                .border(.8.dp, Color.White.copy(alpha = .19f), RoundedCornerShape(12.dp))
                                .clickable(onClickLabel = "Add ${kind.name.lowercase()}") { vm.openCapture(kind) }, contentAlignment = Alignment.Center) {
                                Text(label, color = Lumen.Text, fontFamily = Outfit, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }

                if (showMedia) {
                    HorizontalDivider(color = Color.White.copy(0.16f))
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (media?.art != null || !media?.artUri.isNullOrBlank()) {
                            AsyncImage(
                                model = media?.art ?: media?.artUri,
                                contentDescription = "Album artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                                    .clickable(onClickLabel = "Open media app") { NowPlayingRepository.openPlayer(context) }
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(media?.title?.ifBlank { "Media controls" } ?: "Media controls",
                            color = Lumen.Text, fontFamily = Outfit, fontSize = 11.sp, lineHeight = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable(onClickLabel = "Open media app") { NowPlayingRepository.openPlayer(context) })
                        IconButton(onClick = { NowPlayingRepository.playPause(context) }, modifier = Modifier.size(40.dp)) {
                            Icon(if (media?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (media?.playing == true) "Pause" else "Play", tint = Lumen.Text, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
    if (state.activeSpace == SpaceKind.Travel) TravelShortcutRow(state,vm)
    }
    if (boardingPasses) TravelCollection(state, vm, travelCategory) { boardingPasses = false }
}

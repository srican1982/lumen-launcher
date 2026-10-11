package com.lumen.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumen.launcher.data.*
import com.lumen.launcher.inbox.InboxHub
import com.lumen.launcher.travel.TripModeCard
import com.lumen.launcher.travel.TripViewModel
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import com.lumen.launcher.widgets.*
import kotlinx.coroutines.delay
import java.io.File

/** A widget opens its own useful tool without changing the current page or Space. */
@Composable
fun WidgetToolSheet(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val tool = state.widgetTool
    val creation = state.socialCreations.firstOrNull { it.filePath == state.widgetItem }
    val title = when (tool) { "TripToggle" -> "Trip Mode"; "Photo" -> creation?.kind?.name ?: "Photo"; else -> WidgetKind.parse(tool)?.title ?: "Lumen" }
    var snapshot by remember { mutableStateOf<WidgetSnapshot?>(null) }
    LaunchedEffect(tool) {
        while (true) {
            snapshot = runCatching { WidgetRepository.load(context) }.getOrNull()
            delay(2_000)
        }
    }
    BottomMenu(title, onDismiss = vm::closeSheet) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tool) {
                "Tasks" -> {
                    val tasks = TaskAgenda.today(state.todos.filter { it.space != SpaceKind.Private }, System.currentTimeMillis())
                    if (tasks.isEmpty()) WidgetToolText("All clear for today", "A little room to breathe.")
                    tasks.forEach { task ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = false, onCheckedChange = { vm.toggleTodo(task.id, true) },
                                colors = CheckboxDefaults.colors(uncheckedColor = Lumen.BrandGold))
                            Column(Modifier.weight(1f)) {
                                Text(task.text, color = Lumen.Text, fontFamily = Outfit, fontSize = 16.sp)
                                Text(listOfNotNull(task.dueAt?.let { TodoTime.dayLabel(it) + if (TodoTime.hasClock(it)) " \u00b7 ${TodoTime.timeLabel(it)}" else "" },
                                    task.space.title, if (TaskAgenda.overdue(task, System.currentTimeMillis())) "Overdue" else null).joinToString(" \u00b7 "),
                                    color = if (TaskAgenda.overdue(task, System.currentTimeMillis())) Lumen.BrandGold else Lumen.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    WidgetToolButton("Add a task") { vm.openCapture(CaptureKind.Task) }
                }
                "Focus" -> {
                    val session by com.lumen.launcher.focus.FocusSessionManager.get(context).snapshot.collectAsStateWithLifecycle()
                    val start = com.lumen.launcher.ui.focus.FocusStartButtonAction { vm.startFocus(30, openSpace = false) }
                    LaunchedEffect(state.widgetItem) {
                        if (state.widgetItem == "start" && !session.focusing) start()
                    }
                    val remaining = (session.remainingMs() + 59_999) / 60_000
                    WidgetToolText(if (session.focusing) "$remaining min remaining" else "Make room for what matters",
                        session.error.takeIf { it.isNotBlank() } ?: if (session.paused) "Paused" else if (session.running) "Quiet mode is on" else "Start a quiet 30-minute session.")
                    if (session.focusing) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WidgetToolButton(if (session.paused) "Resume" else "Pause", Modifier.weight(1f)) { if (session.paused) vm.resumeFocus() else vm.pauseFocus() }
                            WidgetToolButton("End", Modifier.weight(1f)) { vm.endFocus() }
                        }
                    } else WidgetToolButton("Start 30 min", action = start)
                }
                "Trip", "TripToggle" -> {
                    val tripVm: TripViewModel = viewModel()
                    val tripState by tripVm.uiState.collectAsStateWithLifecycle()
                    var toggled by remember { mutableStateOf(false) }
                    var confirmEnd by remember { mutableStateOf(false) }
                    LaunchedEffect(tripState.isLoading) {
                        if (tool == "TripToggle" && !toggled && !tripState.isLoading) {
                            toggled = true
                            if (tripState.tripModeEnabled) confirmEnd = true else tripVm.onTripModeToggled(true)
                        }
                    }
                    TripModeCard(tripVm = tripVm)
                    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false },
                        title = { Text("End trip?") }, text = { Text("Your album stays saved. New photos will no longer be added.") },
                        confirmButton = { TextButton(onClick = { confirmEnd = false; tripVm.endTripConfirmed() }) { Text("End trip") } },
                        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } })
                }
                "Social" -> {
                    val data = snapshot
                    when {
                        data == null -> WidgetToolText("Loading your day", "")
                        data.usage == null -> {
                            WidgetToolText("Your time, at a glance", "Usage Access lets Lumen show real time spent in your social apps.")
                            WidgetToolButton("Allow Usage Access") { vm.openWidgetDestination("UsageAccess", "") }
                        }
                        else -> {
                            val ranked = data.usage.filterValues { it > 0 }.entries.sortedByDescending { it.value }
                            if (ranked.isEmpty()) WidgetToolText("A quiet day", "No social app usage recorded today.")
                            ranked.forEach { (pkg, duration) ->
                                val app = state.apps.firstOrNull { it.packageName == pkg }
                                val name = app?.label ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                                val mins = duration / 60_000
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(enabled = app != null) { app?.let(vm::launch) },
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, color = Lumen.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m", color = Lumen.BrandGold)
                                }
                                LinearProgressIndicator(progress = { duration.toFloat() / (ranked.first().value.coerceAtLeast(1)) },
                                    modifier = Modifier.fillMaxWidth().height(3.dp), color = Lumen.BrandGold, trackColor = Lumen.Faint.copy(alpha = .15f))
                            }
                        }
                    }
                }
                "Hush" -> {
                    val notifications by InboxHub.items.collectAsStateWithLifecycle()
                    val items = notifications.filter { item -> !item.secret && !item.isDigest && item.packageName !in state.hidden &&
                        state.privateApps.none { it.substringBefore('/') == item.packageName } && (state.widgetItem.isBlank() || item.packageName == state.widgetItem) }
                    if (!InboxHub.hasAccess(context)) {
                        WidgetToolText("Keep your notifications close", "Allow notification access to review them here.")
                        WidgetToolButton("Allow notification access") { vm.requestNotificationAccess() }
                    } else if (items.isEmpty()) WidgetToolText("All quiet", "No notifications waiting.")
                    items.forEach { notification ->
                        Column(Modifier.fillMaxWidth().clickable { vm.openInboxItem(notification) }.padding(vertical = 6.dp)) {
                            Text(notification.source.title, color = Lumen.BrandGold, fontSize = 11.sp)
                            Text(notification.title, color = Lumen.Text, fontFamily = Outfit, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(notification.preview, color = Lumen.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                "Photo" -> {
                    // Resolve only a real creation; an arbitrary incoming path is never shared.
                    val item = state.socialCreations.firstOrNull { it.filePath == state.widgetItem }
                    if (item == null) WidgetToolText("Photo unavailable", "It may have been removed.")
                    else {
                        AsyncImage(model = File(item.filePath), contentDescription = "Your Lumen ${item.kind.name.lowercase()}", modifier = Modifier.fillMaxWidth().height(270.dp))
                        WidgetToolButton("Share ${item.kind.name.lowercase()}") { vm.socialCreate.share.shareImage(File(item.filePath)) }
                    }
                    WidgetToolButton(if (item?.kind == com.lumen.launcher.social.CreationKind.Scribble) "New scribble"
                        else if (item?.kind == com.lumen.launcher.social.CreationKind.Quote) "New quote" else "Choose another photo") {
                        vm.openWidgetDestination(when (item?.kind) {
                            com.lumen.launcher.social.CreationKind.Scribble -> "Scribble"
                            com.lumen.launcher.social.CreationKind.Quote -> "Quote"
                            else -> "Photos"
                        }, "")
                    }
                }
            }
            TextButton(onClick = vm::closeSheet, modifier = Modifier.align(Alignment.End)) { Text("Done", color = Lumen.BrandGold) }
        }
    }
}

@Composable
private fun WidgetToolText(title: String, detail: String) {
    Text(title, color = Lumen.Text, fontFamily = Outfit, fontSize = 21.sp, fontWeight = FontWeight.Medium)
    if (detail.isNotEmpty()) Text(detail, color = Lumen.Muted, fontFamily = Outfit, fontSize = 14.sp)
}

@Composable
private fun WidgetToolButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), action: () -> Unit) {
    Button(onClick = action, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = Lumen.BrandGold, contentColor = Lumen.OnBrandGold)) {
        Text(label, fontFamily = Outfit)
    }
}

package com.lumen.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.search.SearchHit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel

internal fun modeSearchHint(space: SpaceKind): String = when(space) {
    SpaceKind.Travel -> "Where do you want to go?"
    SpaceKind.Work -> "Find a task, meeting, or app"
    SpaceKind.Personal -> "Who do you want to connect with?"
    SpaceKind.Focus -> "What are you working on?"
    else -> "What do you want to do?"
}

@Composable
internal fun ModeSearchActions(state: LauncherUiState, vm: LauncherViewModel) {
    val query = state.query.trim()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        when(state.activeSpace) {
            SpaceKind.Travel -> {
                Text("Places & directions", style = MaterialTheme.typography.titleMedium)
                Text("Search a destination in Maps. App matches are below.", style = MaterialTheme.typography.bodySmall)
                if (query.isNotEmpty()) Button(onClick = { vm.searchPlace(query) }) { Text("Find $query in Maps") }
                else if (state.travelDestination.isNotBlank()) TextButton(onClick = { vm.searchPlace(state.travelDestination) }) { Text(state.travelDestination) }
            }
            SpaceKind.Work -> {
                Text("Tasks & meetings", style = MaterialTheme.typography.titleMedium)
                state.todos.filter { !it.done && (query.isBlank() || it.text.contains(query,true)) }.take(3).forEach { todo ->
                    TextButton(onClick = vm::openTodoList) { Text(todo.text) }
                }
                state.upcomingEvents.filter { it.end > System.currentTimeMillis() && (query.isBlank() || it.title.contains(query,true)) }.take(3).forEach { event ->
                    TextButton(onClick = { vm.openCalendarEvent(event) }) { Text(event.title) }
                }
                if (query.isNotEmpty()) TextButton(onClick = { vm.addTodo(query); vm.openTodoList() }) { Text("Create task: $query") }
                TextButton(onClick = { if (state.calendarAccess) vm.openCalendarApp() else vm.requestCalendarAccess() }) { Text(if(state.calendarAccess) "Open calendar" else "Connect calendar") }
            }
            SpaceKind.Personal -> {
                Text("Connect with someone", style = MaterialTheme.typography.titleMedium)
                Text("Enter a contact name. Choose how to connect.", style = MaterialTheme.typography.bodySmall)
                if (query.isNotEmpty()) {
                    Row {
                        TextButton(onClick = { vm.runHit(SearchHit.Action("call", "Call $query", "Choose contact", query)) }) { Text("Call") }
                        TextButton(onClick = { vm.runHit(SearchHit.Action("whatsapp", "WhatsApp $query", "Choose contact", query)) }) { Text("WhatsApp") }
                    }
                }
            }
            SpaceKind.Focus -> {
                Text("Make time for one thing", style = MaterialTheme.typography.titleMedium)
                Text(if(query.isEmpty()) "Name your task, then choose a focus session." else query)
                Row {
                    listOf(25,50).forEach { minutes ->
                        TextButton(onClick = {
                            val taskId = query.takeIf { it.isNotEmpty() }?.let { vm.addTodo(it) }
                            vm.startFocus(minutes, taskId)
                            vm.closeSheet()
                        }) { Text("Focus ${minutes}m") }
                    }
                }
            }
            else -> {
                Text("Ask Lumen", style = MaterialTheme.typography.titleMedium)
                if(query.isNotEmpty()) TextButton(onClick = { vm.onWakeWord(query) }) { Text("Ask: $query") }
                else Text("Type a request or find an app below.")
            }
        }
    }
}

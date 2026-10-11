package com.lumen.launcher.ui.focus

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumen.launcher.focus.*

/** One contact sheet with group navigation; all drafts commit together on Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusGroupContactPicker(initialId: String, onDismiss: () -> Unit, dismissSignal: Int = 0) {
    val repo = FocusAllowedPeopleRepository.get(LocalContext.current)
    val groups by repo.groups.collectAsStateWithLifecycle()
    var currentId by remember { mutableStateOf(initialId) }
    val drafts = remember { mutableStateMapOf<String, List<FocusPerson>>() }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val group = groups.firstOrNull { it.id == currentId } ?: return
    FocusPeoplePicker(
            selected = remember(currentId) { drafts[currentId] ?: repo.peopleForGroup(currentId) },
            onDismiss = onDismiss,
            dismissSignal = dismissSignal,
            selectionKey = currentId,
            onSave = { members ->
                drafts[currentId] = members
                drafts.forEach { (id, people) ->
                    groups.firstOrNull { it.id == id }?.let { repo.saveList(it.title, people, id) }
                }
                onDismiss()
            },
            groupContent = { members ->
                Column {
                    FlowRow(Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groups.forEach { tab ->
                            FilterChip(selected = tab.id == currentId, onClick = {
                                drafts[currentId] = members
                                currentId = tab.id
                            }, label = { Text(tab.title, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = FocusAccent))
                        }
                        AssistChip(onClick = { creating = !creating }, label = { Text("+ New group", color = FocusMuted) })
                    }
                    if (creating) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = name, onValueChange = { name = it.take(40) },
                                label = { Text("Group name") }, singleLine = true, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = FocusAccent,
                                    focusedBorderColor = FocusAccent,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.09f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                    focusedLabelColor = FocusAccent,
                                    unfocusedLabelColor = FocusMuted
                                ))
                            TextButton(enabled = name.isNotBlank(), onClick = {
                                drafts[currentId] = members
                                val id = java.util.UUID.randomUUID().toString()
                                repo.saveList(name.trim(), emptyList(), id)
                                name = ""
                                creating = false
                                currentId = id
                            }) { Text("Create") }
                        }
                    }
                    Text("Contacts for ${group.title}", color = FocusMuted)
                }
            }
        )
}

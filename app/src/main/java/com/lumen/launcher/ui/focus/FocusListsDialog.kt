package com.lumen.launcher.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumen.launcher.focus.*

@Composable
internal fun FocusListsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val groups by repo.groups.collectAsState()
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<FocusPeopleGroup?>(null) }
    var picking by remember { mutableStateOf(false) }
    var members by remember { mutableStateOf(emptyList<FocusPerson>()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF101923), title = { Text("Saved people lists") },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("New list name") }, singleLine = true)
                TextButton(enabled = name.isNotBlank(), onClick = { editing = null; members = repo.peopleNow(); picking = true }) { Text("Choose people & save") }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(group.title + " · " + group.personIds.size + " people", color = Color.White)
                            Row {
                                TextButton(onClick = { repo.setPeople(repo.peopleForGroup(group.id)); onDismiss() }) { Text("Use") }
                                TextButton(onClick = { editing = group; members = repo.peopleForGroup(group.id); picking = true }) { Text("Edit") }
                                TextButton(onClick = { repo.deleteList(group.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
    if (picking) FocusPeoplePicker(members, onDismiss = { picking = false }, onSave = { chosen ->
        val group = editing
        if (group == null) repo.saveList(name, chosen) else repo.saveList(group.title, chosen, group.id)
        repo.setPeople(chosen)
        name = ""
        picking = false
    })
}

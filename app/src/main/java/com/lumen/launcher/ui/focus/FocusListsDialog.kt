package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.focus.*


@Composable
internal fun FocusGroupCards(dismissSignal: Int = 0) {
    val repo = FocusAllowedPeopleRepository.get(LocalContext.current)
    val groups by repo.groups.collectAsState()
    val selected by repo.selectedGroups.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(dismissSignal) { open = null; create = false }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups, key = { it.id }) { group ->
            Column(Modifier.width(132.dp).height(84.dp).background(FocusSurface, RoundedCornerShape(16.dp))
                .border(1.dp, if(group.id in selected) FocusAccent else FocusBorder, RoundedCornerShape(16.dp))
                .clickable { open = group.id }.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, Modifier.weight(1f), color = Color.White, fontSize = 13.sp, maxLines = 1)
                    Checkbox(group.id in selected, { repo.selectGroup(group.id, it) }, modifier = Modifier.size(32.dp))
                }
                Text("${group.personIds.size} people   >", color = FocusMuted, fontSize = 11.sp)
            }
        }
        item { TextButton(onClick = { create = true }) { Text("+ New group") } }
    }
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("New group") }, text = {
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Group name") })
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
        val id = java.util.UUID.randomUUID().toString()
        repo.saveList(name, emptyList(), id); repo.selectGroup(id, true); open = id; create = false; name = ""
    }) { Text("Create") } })
    open?.let { id -> FocusGroupMembers(id, repo, { open = null }) }
}

@Composable
private fun FocusGroupMembers(initialId: String, repo: FocusAllowedPeopleRepository, onDismiss: () -> Unit) {
    var id by remember { mutableStateOf(initialId) }
    var picking by remember { mutableStateOf(false) }
    val groups by repo.groups.collectAsState()
    val revision by repo.listsRevision.collectAsState()
    val group = groups.find { it.id == id } ?: return
    val members = remember(id, revision) { repo.peopleForGroup(id) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(FocusInk).systemBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDismiss) { Text("< Back", color = FocusMuted) }
            FocusPageBanner(group.title, "${members.size} people can still call you during Focus.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it.id }) { tab -> FilterChip(tab.id == id, { id = tab.id }, label = { Text(tab.title) }) }
            }
            Text("${group.title} members", color = Color.White, fontSize = 18.sp)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members, key = { it.id }) { person ->
                    Row(Modifier.fillMaxWidth().background(FocusSurface, RoundedCornerShape(18.dp)).border(1.dp, FocusBorder, RoundedCornerShape(18.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FocusContactAvatar(person, 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(person.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if(person.reach.allowsCalls) "Calls allowed" else "Calls silenced", color = if(person.reach.allowsCalls) Color(0xFF50CBB0) else FocusMuted, fontSize = 11.sp)
                        }
                        FocusContactIndicators(person) { reach -> repo.saveList(group.title, members.map {
                            if(it.id == person.id) it.copy(reach = if(reach.allowsCalls) FocusReach.CallsOnly else FocusReach.Neither) else it
                        }, id) }
                        TextButton(onClick = { repo.saveList(group.title, members.filterNot { it.id == person.id }, id) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("-", color = Color(0xFFFF809D), fontSize = 24.sp); Text("Remove", color = FocusMuted, fontSize = 10.sp) }
                        }
                    }
                }
                item { FocusGlass(Modifier.clickable { picking = true }) { Text("+   Add contact", color = Color.White, fontSize = 17.sp) } }
            }
            FocusPrimaryAction("Done", onDismiss)
            TextButton(onClick = { repo.deleteList(id); onDismiss() }) { Text("Delete group", color = Color(0xFFFF9EAB)) }
        }
        if(picking) FocusPeoplePicker(members, { picking = false }, { repo.saveList(group.title, it, id); picking = false })
    }
}

@Composable
internal fun FocusListsDialog(onDismiss: () -> Unit, dismissSignal: Int = 0) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(FocusInk).systemBarsPadding().padding(20.dp)) {
            TextButton(onClick = onDismiss) { Text("< Back") }
            FocusGroupCards(dismissSignal)
        }
    }
}

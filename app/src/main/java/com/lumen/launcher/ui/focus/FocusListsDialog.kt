package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.*
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun FocusGroupCards(dismissSignal: Int = 0) {
    val repo = FocusAllowedPeopleRepository.get(LocalContext.current)
    val groups by repo.groups.collectAsState()
    val selected by repo.selectedGroups.collectAsState()
    val revision by repo.listsRevision.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(dismissSignal) { open = null; create = false }
    val groupMembers = remember(groups, revision) { groups.associate { it.id to repo.peopleForGroup(it.id) } }
    val sortedGroups = remember(groups, groupMembers) { groups.sortedByDescending { groupMembers[it.id].orEmpty().size } }

    LazyRow(
        Modifier
            .fillMaxWidth()
            .focusContainHorizontalScroll(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sortedGroups, key = { it.id }) { group ->
            val isSelected = group.id in selected
            val members = groupMembers[group.id].orEmpty()
            Row(Modifier.width(176.dp).height(56.dp).clip(RoundedCornerShape(14.dp))
                .background(if(isSelected) FocusCardSelected else FocusCard)
                .border(1.dp, if(isSelected) FocusAccent else FocusBorder, RoundedCornerShape(14.dp))
                .clickable { open = group.id }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (members.isEmpty()) FocusGroupAvatar(group, members, 30.dp)
                else Box(Modifier.width((30 + (minOf(members.size, 3) - 1) * 18).dp).height(30.dp)) {
                    members.take(3).forEachIndexed { index, person ->
                        Box(Modifier.offset(x = (index * 18).dp).size(30.dp).clip(CircleShape)
                            .background(FocusCard).border(1.dp, FocusBorder, CircleShape).padding(1.dp)) {
                            FocusContactAvatar(person, 28.dp)
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 7.dp)) {
                    Text(group.title, color = Color.White, fontSize = 11.sp, maxLines = 1)
                    Text(if (members.isEmpty()) "Tap to add" else "${members.size} ${if (members.size == 1) "contact" else "contacts"}", color = FocusMuted, fontSize = 9.sp)
                }
                Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, if(isSelected) FocusAccent else FocusMuted, CircleShape)
                    .background(if(isSelected) FocusAccent else Color.Transparent)
                    .clickable { repo.selectGroup(group.id, !isSelected) }, contentAlignment = Alignment.Center) {
                    if(isSelected) Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        item(key = "new_group") {
            Row(Modifier.width(176.dp).height(56.dp).clip(RoundedCornerShape(14.dp)).background(FocusCard)
                .border(1.dp, FocusBorder, RoundedCornerShape(14.dp)).clickable { create = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AddCircleOutline, null, tint = FocusMuted, modifier = Modifier.size(30.dp))
                Column(Modifier.padding(start = 7.dp)) {
                    Text("New group", color = Color.White, fontSize = 11.sp)
                    Text("Add people", color = FocusMuted, fontSize = 9.sp)
                }
            }
        }
    }
    if (create) {
        FocusPopupSheet(onDismiss = { create = false; name = "" }, heightFraction = 0.42f) {
            Text(
                "New group",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
            Text(
                "Name a list of people who can still call during Focus.",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 13.sp
            )
            val fieldShape = RoundedCornerShape(22.dp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = fieldShape,
                placeholder = { Text("Group name", color = FocusMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = FocusAccent,
                    focusedBorderColor = FocusAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
                )
            )
            FocusPrimaryAction(
                label = "Create group",
                action = {
                    if (name.isBlank()) return@FocusPrimaryAction
                    val id = java.util.UUID.randomUUID().toString()
                    repo.saveList(name.trim(), emptyList(), id)
                    repo.selectGroup(id, true)
                    open = id
                    create = false
                    name = ""
                }
            )
        }
    }
    open?.let { id -> FocusGroupMembers(id, repo, { open = null }) }
}

@Composable
private fun FocusGroupMembers(initialId: String, repo: FocusAllowedPeopleRepository, onDismiss: () -> Unit) {
    var id by remember { mutableStateOf(initialId) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val groups by repo.groups.collectAsState()
    val revision by repo.listsRevision.collectAsState()
    val group = groups.find { it.id == id }
    LaunchedEffect(group) { if (group == null) onDismiss() }
    if (group == null) return
    val members = remember(id, revision) { repo.peopleForGroup(id) }
    val canDelete = !repo.isPresetGroup(id)
    FocusPopupSheet(onDismiss = onDismiss, heightFraction = 0.88f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                group.title,
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            if (canDelete) {
                Text(
                    "Delete",
                    color = FocusDanger,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Outlined.People, null, tint = FocusMuted, modifier = Modifier.size(14.dp))
                Text("${members.size} people", color = FocusMuted, fontSize = 12.sp, fontFamily = Outfit)
            }
        }
        Text("People who can still call you during Focus.", color = FocusMuted, fontSize = 13.sp, fontFamily = Outfit)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups, key = { it.id }) { tab ->
                val active = tab.id == id
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .then(
                            if (active) Modifier.shadow(10.dp, shape, ambientColor = FocusAccent.copy(0.4f), spotColor = FocusAccent.copy(0.5f))
                            else Modifier
                        )
                        .clip(shape)
                        .background(if (active) FocusGradient else Brush.linearGradient(listOf(FocusCard, FocusCard)))
                        .border(1.dp, if (active) Color.Transparent else FocusBorder, shape)
                        .clickable { id = tab.id }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(focusGroupIcon(tab), null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(tab.title, color = Color.White, fontSize = 13.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
                }
            }
        }

        Text("${group.title} members", color = Color.White, fontSize = 15.sp, fontFamily = Outfit, fontWeight = FontWeight.SemiBold)

        val listShape = RoundedCornerShape(20.dp)
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 340.dp)
                .clip(listShape)
                .background(FocusCard.copy(alpha = 0.85f))
                .border(1.dp, FocusBorder, listShape)
        ) {
            items(members, key = { it.id }) { person ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusContactAvatar(person, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            person.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (person.reach.allowsCalls) FocusOk else FocusMuted))
                            Text(
                                if (person.reach.allowsCalls) "Calls allowed" else "Calls silenced",
                                color = FocusMuted,
                                fontSize = 12.sp,
                                fontFamily = Outfit
                            )
                        }
                    }
                    FocusContactIndicators(person) { reach ->
                        repo.saveList(group.title, members.map {
                            if (it.id == person.id) it.copy(reach = if (reach.allowsCalls) FocusReach.CallsOnly else FocusReach.Neither) else it
                        }, id)
                    }
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color.White.copy(alpha = 0.12f))
                    )
                    Column(
                        Modifier
                            .clickable {
                                repo.saveList(group.title, members.filterNot { it.id == person.id }, id)
                            }
                            .padding(start = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.RemoveCircleOutline, null, tint = FocusDanger, modifier = Modifier.size(20.dp))
                        Text("Remove", color = FocusDanger, fontSize = 10.sp, fontFamily = Outfit)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { picking = true }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, null, tint = FocusAccent, modifier = Modifier.size(24.dp))
                    Text("Add contact", Modifier.weight(1f).padding(start = 12.dp), color = Color.White, fontSize = 16.sp, fontFamily = Outfit)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = FocusMuted)
                }
            }
        }

        FocusPrimaryAction(label = "Done", action = onDismiss)
    }
    if (picking) FocusPeoplePicker(members, { picking = false }, { repo.saveList(group.title, it, id); picking = false })
    if (confirmDelete) {
        FocusPopupSheet(onDismiss = { confirmDelete = false }, heightFraction = 0.36f) {
            Text(
                "Delete “${group.title}”?",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
            Text(
                "This removes the group. People in other groups stay.",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 13.sp
            )
            FocusPrimaryAction(
                label = "Delete group",
                action = {
                    repo.deleteList(id)
                    confirmDelete = false
                    onDismiss()
                },
                showCheck = false
            )
            Text(
                "Cancel",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { confirmDelete = false }
                    .padding(8.dp)
            )
        }
    }
}


@Composable
internal fun FocusListsDialog(onDismiss: () -> Unit, dismissSignal: Int = 0) {
    FocusPopupSheet(onDismiss = onDismiss, heightFraction = 0.55f) {
        Text(
            "Who can reach you",
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
        Text(
            "Choose groups that can still call during Focus.",
            color = FocusMuted,
            fontSize = 13.sp,
            fontFamily = Outfit
        )
        FocusGroupCards(dismissSignal)
        FocusPrimaryAction(label = "Done", action = onDismiss)
    }
}

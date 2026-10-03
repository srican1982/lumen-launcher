package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lumen.launcher.focus.FocusAllowedPeopleRepository
import com.lumen.launcher.focus.FocusPeopleGroup
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.ui.theme.Outfit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusListsDialog(onDismiss: () -> Unit, dismissSignal: Int = 0) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val groups by repo.groups.collectAsState()
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<FocusPeopleGroup?>(null) }
    var picking by remember { mutableStateOf(false) }
    var members by remember { mutableStateOf(emptyList<FocusPerson>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismissBaseline = remember { dismissSignal }
    LaunchedEffect(dismissSignal) {
        if (dismissSignal != dismissBaseline) {
            picking = false
            onDismiss()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                picking = false
                onDismiss()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FocusInk,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(0.22f))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Saved people lists",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            )
            Text(
                "Reuse who can reach you. Tap Use to load a list into Focus.",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Create new list
            FocusGlass {
                Text(
                    "Create a list",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.06f))
                        .border(1.dp, Color(0xFF344454), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = Outfit,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(FocusAccent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (name.isBlank()) {
                                Text(
                                    "List name — Family, Clients…",
                                    color = FocusMuted.copy(0.7f),
                                    fontFamily = Outfit,
                                    fontSize = 14.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (name.isNotBlank()) FocusGradient
                            else Brush.linearGradient(listOf(FocusCard, FocusCard))
                        )
                        .clickable(enabled = name.isNotBlank()) {
                            editing = null
                            members = repo.peopleNow()
                            picking = true
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        null,
                        tint = if (name.isNotBlank()) Color(0xFF080E24) else FocusMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Choose people & save",
                        color = if (name.isNotBlank()) Color(0xFF080E24) else FocusMuted,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Text(
                "Your lists",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    ListCard(
                        group = group,
                        count = group.personIds.size,
                        onUse = {
                            repo.setPeople(repo.peopleForGroup(group.id))
                            onDismiss()
                        },
                        onEdit = {
                            editing = group
                            members = repo.peopleForGroup(group.id)
                            name = group.title
                            picking = true
                        },
                        onDelete = { repo.deleteList(group.id) }
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Done",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }

    if (picking) {
        FocusPeoplePicker(
            selected = members,
            dismissSignal = dismissSignal,
            onDismiss = { picking = false },
            onSave = { chosen ->
                val group = editing
                if (group == null) repo.saveList(name, chosen)
                else repo.saveList(group.title.ifBlank { name }, chosen, group.id)
                repo.setPeople(chosen)
                name = ""
                editing = null
                picking = false
            }
        )
    }
}

@Composable
private fun ListCard(
    group: FocusPeopleGroup,
    count: Int,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, accent) = listStyle(group)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(FocusSurface)
            .border(1.dp, Color(0xFF344454), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    if (count == 0) "Empty — add people" else "$count ${if (count == 1) "person" else "people"}",
                    color = FocusMuted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconAction(Icons.Outlined.Edit, FocusMuted, onEdit)
                IconAction(Icons.Outlined.DeleteOutline, Color(0xFFFB7185).copy(0.85f), onDelete)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (count > 0) accent.copy(0.22f) else Color.White.copy(0.06f)
                )
                .border(
                    1.dp,
                    if (count > 0) accent.copy(0.45f) else Color.White.copy(0.10f),
                    RoundedCornerShape(14.dp)
                )
                .clickable(enabled = count > 0, onClick = onUse)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (count > 0) "Use this list" else "Add people first",
                color = if (count > 0) Color.White else FocusMuted,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun IconAction(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

private fun listStyle(group: FocusPeopleGroup): Pair<ImageVector, Color> = when (group.id) {
    FocusPeopleGroup.Family.id, "family" -> Icons.Outlined.FavoriteBorder to Color(0xFFFBBF24)
    FocusPeopleGroup.WorkVips.id -> Icons.Outlined.WorkOutline to Color(0xFF60A5FA)
    FocusPeopleGroup.Emergency.id -> Icons.Outlined.LocalHospital to Color(0xFFFB7185)
    else -> Icons.Outlined.Groups to FocusAccent
}

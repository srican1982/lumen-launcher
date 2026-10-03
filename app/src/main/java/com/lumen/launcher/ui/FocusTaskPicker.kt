package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.ui.focus.FocusAccent
import com.lumen.launcher.ui.focus.FocusBorder
import com.lumen.launcher.ui.focus.FocusCard
import com.lumen.launcher.ui.focus.FocusMuted
import com.lumen.launcher.ui.focus.FocusPopupSheet
import com.lumen.launcher.ui.focus.FocusPrimaryAction
import com.lumen.launcher.ui.focus.FocusSheetSearchHeader
import com.lumen.launcher.ui.theme.Outfit

@Composable
fun FocusTaskPicker(
    tasks: List<TodoItem>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val filtered = remember(tasks, query) {
        val q = query.trim()
        if (q.isEmpty()) tasks
        else tasks.filter { it.text.contains(q, ignoreCase = true) }
    }

    FocusPopupSheet(onDismiss = onDismiss, heightFraction = 0.72f) {
        FocusSheetSearchHeader(
            title = "Focus on a task",
            query = query,
            onQueryChange = { query = it },
            searching = searching,
            onSearchingChange = { searching = it }
        )
        if (!searching) {
            Text(
                "Pick one open task. It’ll be crossed off when Focus ends.",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 13.sp
            )
        }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (tasks.isEmpty()) "No open tasks yet. Add one from your list."
                        else "No matching tasks.",
                        color = FocusMuted,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(filtered, key = { it.id }) { task ->
                val selected = task.id == selectedId
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (selected) FocusAccent.copy(alpha = 0.18f) else FocusCard.copy(alpha = 0.9f))
                        .border(1.dp, if (selected) FocusAccent.copy(alpha = 0.7f) else FocusBorder, shape)
                        .clickable { onSelect(task.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (selected) Icons.Filled.Check else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) FocusAccent else FocusMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            task.text,
                            color = Color.White,
                            fontFamily = Outfit,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            task.space.title,
                            color = FocusMuted,
                            fontFamily = Outfit,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        FocusPrimaryAction(label = "Add or manage tasks", action = onAdd, showCheck = false)
    }
}

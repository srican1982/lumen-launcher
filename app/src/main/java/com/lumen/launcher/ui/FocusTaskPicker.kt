package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.TodoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTaskPicker(tasks: List<TodoItem>, selectedId: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val mint = Color(0xFF83F5AC)
    val filtered = tasks.filter { it.text.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF182720), contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = .6f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Text("ONE THING AT A TIME", color = mint, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Choose your focus", fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("Pick the task that matters right now.", color = Color.White.copy(.65f), fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Find a task…") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = mint, unfocusedBorderColor = Color.White.copy(.2f), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = mint))
            Spacer(Modifier.height(14.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (filtered.isEmpty()) item { Text(if (tasks.isEmpty()) "No open tasks yet. Add one to get started." else "No matching tasks.", modifier = Modifier.padding(vertical = 20.dp), color = Color.White.copy(.7f)) }
                items(filtered, key = { it.id }) { task ->
                    val selected = task.id == selectedId
                    val shape = RoundedCornerShape(18.dp)
                    Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(if (selected) mint.copy(.18f) else Color.White.copy(.07f), Color.White.copy(.03f))), shape)
                        .border(1.dp, if (selected) mint.copy(.7f) else Color.White.copy(.14f), shape)
                        .clickable { onSelect(task.id) }.padding(16.dp)) {
                        Text(if (selected) "✓" else "○", color = mint, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(task.text, color = Color.White, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                            Text(task.space.title, color = Color.White.copy(.55f), fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = mint, contentColor = Color(0xFF102018))) { Text("Add or manage tasks") }
        }
    }
}

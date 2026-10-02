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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.IconCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusAppPicker(apps: List<AppInfo>, selected: List<String>, icons: IconCache,
                   onToggle: (AppInfo) -> Unit, onDismiss: () -> Unit,
                   excluded: List<String> = emptyList(), onExclude: (AppInfo) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    val accent = Color(0xFF83F5AC)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF17251E),
        contentColor = Color.White, scrimColor = Color.Black.copy(alpha = .6f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your Focus apps", style = MaterialTheme.typography.headlineSmall)
            Text("${selected.size}/4 saved · Used alongside task suggestions", color = Color.White.copy(alpha = .65f))
            OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search apps") },
                singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
            val filtered = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }.sortedBy { it.label.lowercase() }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filtered.isEmpty()) item { Text("No apps found", Modifier.padding(16.dp)) }
                items(filtered, key = { it.key }) { app ->
                    val checked = app.key in selected
                    val shape = RoundedCornerShape(18.dp)
                    Row(Modifier.fillMaxWidth().clip(shape).background(if (checked) accent.copy(alpha = .12f) else Color.White.copy(alpha = .04f))
                        .border(1.dp, if (checked) accent.copy(alpha = .5f) else Color.White.copy(alpha = .1f), shape)
                        .clickable(enabled = app.key !in excluded) { onToggle(app) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.packageName, app.activityName, 42.dp, icons, showNotificationBadge = false)
                        Text(app.label, Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White)
                        TextButton(onClick = { onExclude(app) }) { Text(if (app.key in excluded) "Allow" else "Hide", color = if (app.key in excluded) accent else Color.White.copy(.6f)) }
                        Checkbox(checked = checked, enabled = app.key !in excluded, onCheckedChange = { onToggle(app) },
                            colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = Color(0xFF142018)))
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF142018))) { Text("Done") }
        }
    }
}

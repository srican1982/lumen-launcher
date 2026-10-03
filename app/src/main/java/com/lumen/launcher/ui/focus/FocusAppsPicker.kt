package com.lumen.launcher.ui.focus

import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.data.*
import com.lumen.launcher.focus.FocusAppAccess
import com.lumen.launcher.ui.AppIcon

@Composable
internal fun FocusAppsSection() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(FocusAppAccess.selected(context)) }
    var show by remember { mutableStateOf(false) }
    val apps by produceState<List<AppInfo>>(emptyList()) { value = AppRepository(context).loadLaunchableApps().distinctBy { it.packageName } }
    val icons = remember { IconCache(context) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("3. Allowed apps during Focus", Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
        TextButton(onClick = { show = true }) { Text("${selected.size} selected", color = FocusAccent, fontSize = 11.sp) }
    }
    FocusGlass {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            items(apps.filter { it.packageName in selected }, key = { it.packageName }) { app ->
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app.packageName, app.activityName, 36.dp, icons, showNotificationBadge = false)
                    Text(app.label, color = FocusMuted, fontSize = 10.sp, maxLines = 1)
                }
            }
            item { TextButton(onClick = { show = true }) { Text("+\nManage apps", color = FocusMuted) } }
        }
    }
    if (show) {
        var draft by remember { mutableStateOf(selected) }
        var query by remember { mutableStateOf("") }
        val apps by produceState<List<AppInfo>>(emptyList()) { value = AppRepository(context).loadLaunchableApps().distinctBy { it.packageName } }
        val icons = remember { IconCache(context) }
        var category by remember { mutableStateOf("All") }
        fun matches(app: AppInfo, group: String): Boolean = when(group) {
            "Work", "Work essentials" -> app.category == AppCategory.Work
            "Communication", "Family only" -> app.category == AppCategory.Social || app.label.contains("phone", true) || app.label.contains("messages", true)
            "Essential", "Minimal" -> app.category == AppCategory.Utilities
            else -> true
        }
        val filtered = apps.filter { it.label.contains(query.trim(), true) && matches(it, category) }
        Dialog(onDismissRequest = { show = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(FocusInk).systemBarsPadding().imePadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { show = false }) { Text("‹ Back") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { FocusAppAccess.save(context, draft); selected = draft; show = false }) { Text("Done") }
                }
                FocusPageBanner("Allowed apps", "${draft.size} selected for use during Focus")
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search apps") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Essential", "Work", "Communication", "All")) { tab ->
                        FilterChip(category == tab, { category = tab }, label = { Text(tab) })
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in draft
                        Row(Modifier.fillMaxWidth().background(FocusSurface, RoundedCornerShape(16.dp)).border(1.dp, FocusBorder, RoundedCornerShape(16.dp)).clickable { draft = if (checked) draft - app.packageName else draft + app.packageName }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(12.dp))
                            AppIcon(app.packageName, app.activityName, 40.dp, icons, showNotificationBadge = false)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) { Text(app.label, color = Color.White); Text(app.category.label, color = FocusMuted, fontSize = 11.sp) }
                            Switch(checked, { draft = if(it) draft + app.packageName else draft - app.packageName }, colors = SwitchDefaults.colors(checkedTrackColor = FocusAccent, checkedThumbColor = Color.White))
                        }
                    }
                }
                Text("Quick sets", color = Color.White, modifier = Modifier.padding(top = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Work essentials", "Family only", "Minimal")) { preset ->
                        OutlinedButton(onClick = { draft = apps.filter { matches(it, preset) }.map { it.packageName }.toSet() }) { Text(preset, fontSize = 11.sp) }
                    }
                }
                FocusPrimaryAction("Save allowed apps") { FocusAppAccess.save(context, draft); selected = draft; show = false }
                Text("Applies to Lumen launch controls. Android Recents and notifications can still open other apps.", color = FocusMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState

@Composable
fun ModeAppPicker(state: LauncherUiState, icons: IconCache, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var selected by remember(state.activeSpace) { mutableStateOf(state.homeApps.map { it.key }.toSet()) }
    var query by remember { mutableStateOf("") }
    val apps = remember(state.visibleApps, query) {
        state.visibleApps.filter { it.label.contains(query.trim(), true) }.sortedBy { it.label.lowercase() }
    }
    val accent = Color(0xFFE1C4FF)
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding()
                .heightIn(max = 720.dp).fillMaxHeight(0.86f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF302B3D), Color(0xFF1D1C26))))
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MAKE IT YOURS", color = accent, fontFamily = Outfit, fontSize = 10.sp, letterSpacing = 1.6.sp)
                    Text("Apps for ${state.activeSpace.title}", color = Color.White, fontFamily = Outfit, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cancel", tint = Color.White.copy(alpha = 0.7f)) }
            }
            Text("Choose the apps you want in this space.", color = Color.White.copy(alpha = 0.6f), fontFamily = Outfit, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Find an app", fontFamily = Outfit) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f), unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = accent,
                    focusedLeadingIconColor = accent, unfocusedLeadingIconColor = Color.White.copy(alpha = 0.55f)
                )
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} selected", color = accent, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("Clear", color = if (selected.isEmpty()) Color.Gray else accent) }
            }
            if (apps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No apps match your search", color = Color.White.copy(alpha = 0.6f), fontFamily = Outfit)
                }
            } else LazyVerticalGrid(
                columns = GridCells.Adaptive(86.dp), modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(apps, key = { it.key }) { app ->
                    val chosen = app.key in selected
                    val tileShape = RoundedCornerShape(18.dp)
                    Box(Modifier.fillMaxWidth().height(108.dp).clip(tileShape)
                        .background(if (chosen) Color(0xFF493951) else Color.White.copy(alpha = 0.035f))
                        .border(1.dp, if (chosen) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f), tileShape)
                        .toggleable(value = chosen, role = Role.Checkbox) { selected = if (it) selected + app.key else selected - app.key }
                    ) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app.packageName, app.activityName, 44.dp, icons, showNotificationBadge = false)
                            Spacer(Modifier.height(8.dp))
                            Text(app.label, color = Color.White, fontFamily = Outfit, fontSize = 11.sp, lineHeight = 14.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                        if (chosen) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Check, null, tint = Color(0xFF31213D), modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) }
                Button(onClick = { onSave(selected.toList()) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF30213A))) {
                    Text("Save ${selected.size} apps", fontFamily = Outfit, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

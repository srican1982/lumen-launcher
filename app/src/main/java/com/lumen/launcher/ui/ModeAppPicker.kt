package com.lumen.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import com.lumen.launcher.ui.theme.Lumen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
fun ModeAppPicker(state: LauncherUiState, icons: IconCache, onDismiss: () -> Unit, onSave: (List<String>) -> Unit, welcome: Boolean = false, initialKeys: List<String>? = null, onUsage: (() -> Unit)? = null, editing: Boolean = false) {
    val maxApps = com.lumen.launcher.data.ModeAppsResolver.MAX_APPS
    var selected by remember(state.activeSpace, initialKeys) { mutableStateOf((initialKeys ?: state.homeApps.map { it.key }).take(maxApps).toSet()) }
    val gridState = rememberLazyGridState()
    var query by remember { mutableStateOf("") }
    val apps = remember(state.visibleApps, query, selected) {
        state.visibleApps.filter { it.label.contains(query.trim(), true) }.sortedWith(compareByDescending<com.lumen.launcher.data.AppInfo> { it.key in selected }.thenBy { it.label.lowercase() })
    }
    val accent = Lumen.Accent
    val motion = rememberInfiniteTransition(label = "mode edit")
    val wiggle by motion.animateFloat(-1.4f, 1.4f, infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "wiggle")
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding()
                .heightIn(max = 720.dp).fillMaxHeight(0.86f)
                .denseGlass(shape)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MAKE IT YOURS", color = accent, fontFamily = Outfit, fontSize = 10.sp, letterSpacing = 1.6.sp)
                    Text(if (welcome) "Pick your ${state.activeSpace.title} apps" else "Apps for ${state.activeSpace.title}", color = Color.White, fontFamily = Outfit, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cancel", tint = Color.White.copy(alpha = 0.7f)) }
            }
            Text(if (welcome) "We picked ${(initialKeys ?: state.homeApps.map { it.key }).take(maxApps).size} to start \u00b7 Up to $maxApps apps" else if (editing) "Remove apps from this mode or search to add more." else "Choose up to $maxApps apps for this space.", color = Color.White.copy(alpha = 0.6f), fontFamily = Outfit, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
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
                Text("${selected.size} of $maxApps selected", color = accent, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("Clear", color = if (selected.isEmpty()) Color.Gray else accent) }
            }
            if (apps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No apps match your search", color = Color.White.copy(alpha = 0.6f), fontFamily = Outfit)
                }
            } else LazyVerticalGrid(
                columns = GridCells.Fixed(4), state = gridState,
                modifier = Modifier.weight(1f).fillMaxWidth().softGridEdges({ false }, { gridState.canScrollForward }),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(apps, key = { it.key }) { app ->
                    val chosen = app.key in selected
                    val tileShape = RoundedCornerShape(18.dp)
                    Box(Modifier.fillMaxWidth().height(80.dp).graphicsLayer { rotationZ = if (editing) wiggle * (if (app.key.hashCode() % 2 == 0) 1 else -1) else 0f }.clip(tileShape)
                        .background(if (chosen) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.035f))
                        .border(if (chosen) 1.5.dp else 1.dp, if (chosen) accent else Color.White.copy(alpha = 0.06f), tileShape)
                        .toggleable(value = chosen, enabled = chosen || selected.size < maxApps, role = Role.Checkbox) { selected = if (it) selected + app.key else selected - app.key }
                    ) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app.packageName, app.activityName, 38.dp, icons, showNotificationBadge = false)
                            Spacer(Modifier.height(5.dp))
                            Text(app.label, color = Color.White, fontFamily = Outfit, fontSize = 11.sp, lineHeight = 14.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                        if (chosen) Box(Modifier.align(Alignment.TopStart).padding(3.dp).size(24.dp).background(accent, CircleShape).clickable { selected = selected - app.key }, contentAlignment = Alignment.Center) {
                            if (editing) Text("−", color = Color(0xFF31213D), fontSize = 18.sp) else Icon(Icons.Outlined.Check, "Remove from mode", tint = Color(0xFF31213D), modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            if (welcome || onUsage != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (welcome) TextButton(onClick = {
                    selected = com.lumen.launcher.data.SpaceStarterApps.picks(state.activeSpace, state.visibleApps, state.dock.map { it.packageName }.toSet()).map { it.key }.toSet()
                    query = ""
                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text("Reset to suggestions", color = accent, fontSize = 11.sp, maxLines = 1)
                }
                if (onUsage != null) TextButton(onClick = onUsage, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text("Use your recent apps", color = accent, fontSize = 11.sp, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!welcome) TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) }
                Button(onClick = { onSave(selected.toList()) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF30213A))) {
                    Text(if (welcome) "Continue (${selected.size})" else if (editing) "Done (${selected.size})" else "Save (${selected.size})", fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

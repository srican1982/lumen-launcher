package com.lumen.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel

/** Home membership and uninstall actions are distinct; uninstall uses Android confirmation. */
@Composable
fun AppEditPage(state: LauncherUiState, vm: LauncherViewModel, onDismiss: () -> Unit) {
    val openedAtHomePulse = remember { state.homePulse }
    val closeEditor by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.homePulse) {
        // Android delivers Home to the launcher; close this locally owned dialog as well.
        if (state.homePulse != openedAtHomePulse) closeEditor()
    }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val self = LocalContext.current.packageName
    val homeKeys = state.copy(spaceOverride = com.lumen.launcher.data.SpaceKind.Home).homeApps.map { it.key }.toSet()
    val maxHomeApps = com.lumen.launcher.data.ModeAppsResolver.MAX_APPS
    val apps = remember(state.visibleApps, query) {
        state.visibleApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(query) { grid.scrollToItem(0) }
    val animation = rememberInfiniteTransition(label = "Edit apps")
    val angle by animation.animateFloat(-1.6f, 1.6f,
        infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "Icon wiggle")
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            SpaceBackdrop(state.spaceWallpaper, extraBlur = 16.dp)
            AmbientBackdrop()
            Box(Modifier.fillMaxSize().background(Lumen.Scrim))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Edit apps", color = Lumen.Text, fontFamily = Outfit, fontSize = 26.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { focus.requestFocus() }) {
                        Icon(Icons.Outlined.Search, "Search apps", tint = Lumen.Text)
                    }
                    TextButton(onClick = onDismiss) { Text("Done", color = Lumen.Accent, fontFamily = Outfit) }
                }
                Text("Home apps: ${homeKeys.size} / $maxHomeApps", color = Lumen.BrandGold, fontFamily = Outfit,
                    modifier = Modifier.padding(bottom = 4.dp))
                Text(if (homeKeys.size >= maxHomeApps) "Home is full. Remove a Home app to add another. − on the icon uninstalls."
                    else "Add apps to Home below. − Home removes the shortcut; − on the icon uninstalls.",
                    color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp))
                TextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Search apps", fontFamily = Outfit) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, "Clear search")
                    } },
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = .12f),
                        unfocusedContainerColor = Color.White.copy(alpha = .08f),
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Lumen.Text, unfocusedTextColor = Lumen.Text,
                        cursorColor = Lumen.Accent, focusedLeadingIconColor = Lumen.Accent,
                        unfocusedLeadingIconColor = Lumen.Muted,
                        focusedPlaceholderColor = Lumen.Muted, unfocusedPlaceholderColor = Lumen.Muted))
                Spacer(Modifier.height(20.dp))
                if (apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No apps match your search", color = Lumen.Muted, fontFamily = Outfit)
                } else LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), state = grid,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 24.dp)) {
                    items(apps, key = { it.key }) { app ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(76.dp)) {
                                Box(Modifier.align(Alignment.Center).size(58.dp).graphicsLayer {
                                    rotationZ = angle * if (app.key.hashCode() % 2 == 0) 1f else -1f
                                }) {
                                    AppIcon(app.packageName, app.activityName, 58.dp, vm.icons, showNotificationBadge = false)
                                    // Center the badge on the actual icon corner, including while it wiggles.
                                    IconButton(onClick = { vm.uninstallApp(app) }, enabled = app.packageName != self,
                                        modifier = Modifier.align(Alignment.TopStart).offset(x = (-22).dp, y = (-22).dp).size(44.dp)) {
                                        Box(Modifier.size(26.dp).background(Lumen.Text.copy(alpha = if (app.packageName == self) .35f else .9f), CircleShape),
                                            contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Remove,
                                                if (app.packageName == self) "Lumen cannot delete itself" else "Delete ${app.label}",
                                                tint = Color(0xFF30213A), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            FittingAppLabel(app.label, color = Lumen.Text, fontFamily = Outfit, fontSize = 12.sp,
                                maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                            val onHome = app.key in homeKeys
                            val inDock = state.dock.any { it.packageName == app.packageName }
                            TextButton(onClick = { vm.toggleHomeApp(app) },
                                enabled = !inDock && (onHome || homeKeys.size < maxHomeApps),
                                contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth().height(32.dp)) {
                                Text(when { inDock -> "In dock"; onHome -> "− Home"; homeKeys.size >= maxHomeApps -> "Home full"; else -> "Add to Home" },
                                    color = if (onHome || (!inDock && homeKeys.size < maxHomeApps)) Lumen.BrandGold else Lumen.Faint,
                                    fontFamily = Outfit, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

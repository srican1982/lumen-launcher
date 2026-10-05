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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Spa
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
import com.lumen.launcher.data.*
import com.lumen.launcher.focus.FocusAppAccess
import com.lumen.launcher.ui.AppIcon
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun FocusAppsSection(dismissSignal: Int = 0, showHeading: Boolean = true) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(FocusAppAccess.selected(context)) }
    var show by remember { mutableStateOf(false) }
    val apps by produceState<List<AppInfo>>(emptyList()) { value = AppRepository(context).loadLaunchableApps().distinctBy { it.packageName } }
    val icons = remember { IconCache(context) }
    val preview = apps.filter { it.packageName in selected }.take(5)
    LaunchedEffect(dismissSignal) { show = false }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeading) Text("3. Which apps can you use?", color = Color.White, fontSize = 15.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
        LazyRow(
            Modifier
                .fillMaxWidth()
                .focusContainHorizontalScroll(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(preview, key = { it.packageName }) { app ->
                FocusGlassTile(label = app.label, width = 64.dp, height = 64.dp, onClick = { show = true }) {
                    AppIcon(app.packageName, app.activityName, 30.dp, icons, showNotificationBadge = false)
                }
            }
            item(key = "manage_apps") {
                FocusGlassTile(label = "Add apps", width = 64.dp, height = 64.dp, onClick = { show = true }) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = FocusMuted, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }

    if (show) {
        AllowedAppsDialog(
            apps = apps,
            icons = icons,
            initial = selected,
            onDismiss = { show = false },
            onSave = { next ->
                FocusAppAccess.save(context, next)
                selected = next
                show = false
            }
        )
    }
}

@Composable
private fun AllowedAppsDialog(
    apps: List<AppInfo>,
    icons: IconCache,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    var category by remember { mutableStateOf("Essential") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    fun matches(app: AppInfo, group: String): Boolean = when (group) {
        "Work", "Work essentials" -> app.category == AppCategory.Work
        "Communication", "Family only" ->
            app.category == AppCategory.Social ||
                app.label.contains("phone", true) ||
                app.label.contains("messages", true) ||
                app.label.contains("sms", true)
        "Essential", "Minimal" ->
            app.category == AppCategory.Utilities ||
                app.label.contains("phone", true) ||
                app.label.contains("messages", true) ||
                app.label.contains("calendar", true) ||
                app.label.contains("maps", true)
        else -> true
    }
    val filtered = apps
        .filter { matches(it, category) }
        .filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        .sortedBy { it.label.lowercase() }
    val tabs = listOf(
        Triple("Essential", Icons.Filled.Star, true),
        Triple("Work", Icons.Outlined.BusinessCenter, false),
        Triple("Communication", Icons.Outlined.ChatBubbleOutline, false),
        Triple("All", Icons.Outlined.Apps, false)
    )
    val presets = listOf(
        Triple("Work essentials", Icons.Outlined.BusinessCenter, "Work essentials"),
        Triple("Family only", Icons.Outlined.FavoriteBorder, "Family only"),
        Triple("Minimal", Icons.Outlined.Spa, "Minimal")
    )

    FocusPopupSheet(onDismiss = onDismiss, heightFraction = 0.88f) {
        FocusSheetSearchHeader(
            title = "Allowed apps",
            query = query,
            onQueryChange = { query = it },
            searching = searching,
            onSearchingChange = { searching = it }
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Outlined.GridView, null, tint = FocusMuted, modifier = Modifier.size(14.dp))
                Text("${draft.size} selected", color = FocusMuted, fontSize = 12.sp, fontFamily = Outfit)
            }
        }
        if (!searching) {
            Text("Choose the only apps that stay available during Focus.", color = FocusMuted, fontSize = 13.sp, fontFamily = Outfit)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs) { (label, icon, _) ->
                val active = category == label
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .then(
                            if (active) Modifier.shadow(10.dp, shape, ambientColor = FocusAccent.copy(0.45f), spotColor = FocusAccent.copy(0.55f))
                            else Modifier
                        )
                        .clip(shape)
                        .background(
                            if (active) Brush.horizontalGradient(listOf(Color(0xFF3A3F8A), Color(0xFF2A3A7A)))
                            else Brush.linearGradient(listOf(FocusCard, FocusCard))
                        )
                        .border(1.dp, if (active) FocusAccent.copy(alpha = 0.7f) else FocusBorder, shape)
                        .clickable { category = label }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, null, tint = if (active) Color.White else FocusMuted, modifier = Modifier.size(15.dp))
                    Text(label, color = if (active) Color.White else FocusMuted, fontSize = 13.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
                }
            }
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in draft
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(FocusCard.copy(alpha = 0.9f))
                        .border(1.dp, FocusBorder, shape)
                        .clickable {
                            draft = if (checked) draft - app.packageName else draft + app.packageName
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.packageName, app.activityName, 30.dp, icons, showNotificationBadge = false)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(app.label, color = Color.White, fontSize = 16.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
                        Text(appSubtitle(app), color = FocusMuted, fontSize = 12.sp, fontFamily = Outfit)
                    }
                    Switch(
                        checked,
                        {
                            draft = if (it) draft + app.packageName else draft - app.packageName
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = FocusAccent,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF1A1F35),
                            uncheckedThumbColor = FocusMuted
                        )
                    )
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Quick sets", color = Color.White, fontSize = 15.sp, fontFamily = Outfit, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (title, icon, key) ->
                        val count = apps.count { matches(it, key) }
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            Modifier
                                .width(168.dp)
                                .clip(shape)
                                .background(FocusCard)
                                .border(1.dp, FocusBorder, shape)
                                .clickable {
                                    draft = apps.filter { matches(it, key) }.map { it.packageName }.toSet()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(FocusAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = FocusIconTint, modifier = Modifier.size(16.dp))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(title, color = Color.White, fontSize = 13.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("$count apps", color = FocusMuted, fontSize = 11.sp, fontFamily = Outfit)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = FocusMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Text(
            "Blocked apps stay hidden until Focus ends.",
            color = FocusMuted,
            fontSize = 11.sp,
            fontFamily = Outfit,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        FocusPrimaryAction(label = "Save allowed apps", action = { onSave(draft) })
    }
}

private fun appSubtitle(app: AppInfo): String = when {
    app.label.contains("phone", true) -> "Calls"
    app.label.contains("message", true) || app.label.contains("sms", true) -> "Texts"
    app.label.contains("calendar", true) -> "Events"
    app.label.contains("map", true) -> "Navigation"
    app.label.contains("team", true) || app.label.contains("slack", true) -> "Work chat"
    else -> app.category.label
}

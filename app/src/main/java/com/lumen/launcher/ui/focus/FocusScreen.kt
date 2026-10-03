package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.AppRepository
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.focus.FocusAppAccess
import com.lumen.launcher.focus.FocusDoneEntry
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.ui.AppIcon
import com.lumen.launcher.ui.theme.Outfit

@Composable
fun FocusActiveScreen(
    remainingMs: Long,
    totalMs: Long,
    paused: Boolean,
    people: List<FocusPerson>,
    capabilityNote: String,
    taskLabel: String? = null,
    doneEntries: List<FocusDoneEntry> = emptyList(),
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtend: () -> Unit,
    onEnd: () -> Unit,
    onAddSomeone: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val icons = remember { IconCache(context) }
    val selectedPkgs = remember { FocusAppAccess.selected(context) }
    val apps by produceState(emptyList()) {
        val all = AppRepository(context).loadLaunchableApps().distinctBy { it.packageName }
        value = all.filter { it.packageName in selectedPkgs }.sortedBy { it.label.lowercase() }
    }
    var details by remember { mutableStateOf(false) }
    val underOneMinute = remainingMs < 60_000L
    val countdownValue = if (underOneMinute) {
        ((remainingMs + 999L) / 1_000L).coerceAtLeast(0L)
    } else {
        (remainingMs + 59_999L) / 60_000L
    }
    val countdownUnit = when {
        paused -> "paused"
        underOneMinute -> if (countdownValue == 1L) "second left" else "seconds left"
        else -> "minutes left"
    }
    val fraction = (remainingMs.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(FocusSurface)
                .border(1.dp, FocusBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Quiet,\nwithout becoming\nunreachable.",
                        color = Color(0xFFA9CEFF),
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Silence distractions.\nKeep the people who matter.",
                        color = FocusMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 10.dp.toPx()
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        drawCircle(Brush.radialGradient(listOf(Color(0x303C9FEF), Color.Transparent)), radius = size.width / 2)
                        drawArc(Color(0xFF253B4B), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(
                            Brush.sweepGradient(listOf(Color(0xFF8EA9FF), Color(0xFFAA8DFF), Color(0xFF44BFEF), Color(0xFF8EA9FF))),
                            -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$countdownValue",
                            color = Color.White,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (underOneMinute) 38.sp else 40.sp,
                            lineHeight = 46.sp
                        )
                        Text(countdownUnit, color = Color(0xFFD8E4F5), fontFamily = Outfit, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
            // Active task at the bottom of the countdown hero.
            val taskShape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(taskShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), taskShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Checklist, null, tint = FocusAccent, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Focusing on",
                        color = FocusMuted,
                        fontFamily = Outfit,
                        fontSize = 11.sp
                    )
                    Text(
                        if (taskLabel.isNullOrBlank()) "No task selected" else taskLabel,
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        FocusGlass(Modifier.clickable { details = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Eco, null, tint = Color(0xFF9AF0B2), modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(if (paused) "Focus is paused" else "Focus is ON", color = Color.White, fontFamily = Outfit, fontSize = 15.sp, lineHeight = 20.sp)
                    Text(
                        if (paused) "Your previous sound settings are restored."
                        else "Selected callers can reach you.",
                        color = FocusMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("›", color = FocusMuted, fontSize = 24.sp)
            }
        }
        FocusRingCheck()
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Allowed apps", Modifier.weight(1f), color = Color.White, fontFamily = Outfit, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            Text("${apps.size}", Modifier.clip(CircleShape).background(Color(0xFF24313E)).padding(horizontal = 9.dp, vertical = 3.dp), color = FocusMuted, fontSize = 12.sp)
        }
        if (apps.isEmpty()) {
            Text("No apps selected. End Focus and choose apps in setup to use them here.", color = FocusMuted, fontSize = 12.sp, lineHeight = 17.sp)
        } else {
            LazyRow(
                Modifier.fillMaxWidth().focusContainHorizontalScroll(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    FocusGlassTile(label = app.label, onClick = { onLaunchApp(app) }) {
                        AppIcon(app.packageName, app.activityName, 40.dp, icons, showNotificationBadge = false)
                    }
                }
            }
            Text("Tap an app to open it. Media sound stays on during Focus.", color = FocusMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Selected people", Modifier.weight(1f), color = Color.White, fontFamily = Outfit, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            Text("${people.size}", Modifier.clip(CircleShape).background(Color(0xFF24313E)).padding(horizontal = 9.dp, vertical = 3.dp), color = FocusMuted, fontSize = 12.sp)
        }
        if (people.isEmpty()) {
            Text("Add people to allow their ordinary phone calls during Focus.", color = FocusMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        LazyRow(
            Modifier.fillMaxWidth().focusContainHorizontalScroll(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(people, key = { it.id }) { person ->
                FocusGlassTile(label = person.name, onClick = { }) {
                    FocusContactAvatar(person, 40.dp)
                }
            }
            item(key = "add_someone") {
                FocusGlassTile(label = "Add", onClick = onAddSomeone) {
                    Box(
                        Modifier
                            .size(40.dp)
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
        FocusDoneSection(doneEntries)
        FocusRingCheck()
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusControl(if (paused) "Resume" else "Pause", if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (paused) onResume else onPause, Modifier.weight(1f))
            FocusControl("+15 min", Icons.Outlined.Schedule, onExtend, Modifier.weight(1f))
            FocusControl("End", Icons.Outlined.Stop, onEnd, Modifier.weight(1f), danger = true)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsOff, null, tint = FocusMuted, modifier = Modifier.size(13.dp))
            Text("  Quiet mode · Android manages interruptions", color = FocusMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0xFF36475F), RoundedCornerShape(20.dp)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AllInclusive, null, tint = FocusAccent, modifier = Modifier.size(18.dp))
            Text("  Focus keeps running when you switch Spaces.", color = FocusAccent, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, containerColor = Color(0xFF15202C),
        title = { Text("Your Focus policy", color = Color.White) }, text = { Text(capabilityNote, color = FocusMuted) },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Got it", color = FocusAccent) } })
}

@Composable
private fun FocusControl(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, danger: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(if (danger) Brush.verticalGradient(listOf(Color(0xFF381D23), Color(0xFF25181D))) else FocusSurface)
        .border(1.dp, if (danger) Color(0xFF72343D) else Color(0xFF344454), RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (danger) Color(0xFFFF8795) else Color.White, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (danger) Color(0xFFFF8795) else Color.White, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = Outfit)
    }
}

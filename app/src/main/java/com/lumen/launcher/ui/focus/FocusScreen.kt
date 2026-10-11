package com.lumen.launcher.ui.focus

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.MusicNote
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val ringSize = minOf(configuration.screenWidthDp - 72, (configuration.screenHeightDp * 0.34f).toInt(), 310).coerceAtLeast(180).dp * 0.8f
    val repo = com.lumen.launcher.focus.FocusAllowedPeopleRepository.get(context)
    val groups by repo.groups.collectAsStateWithLifecycle()
    var contacts by remember { mutableStateOf(false) }
    var appsOpen by remember { mutableStateOf(false) }
    var soundsOpen by remember { mutableStateOf(false) }
    val apps by produceState<List<AppInfo>>(emptyList()) { value = AppRepository(context).loadLaunchableApps().distinctBy { it.packageName } }
    val icons = remember { IconCache(context) }
    val seconds = ((remainingMs + 999) / 1000).coerceAtLeast(0)
    val countdown = "%02d:%02d".format(seconds / 60, seconds % 60)
    val fraction = (remainingMs.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(androidx.compose.ui.res.painterResource(com.lumen.launcher.R.drawable.ic_lumen_mark), null,
            tint = Color(0xFFAB9AFF), modifier = Modifier.size(28.dp))
        Text(if (paused) "Focus is paused" else "Focus is on", color = Color.White, fontFamily = Outfit, fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 16.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawCircle(Brush.radialGradient(listOf(Color(0xDB07111F), Color(0xA9101A29))), radius = size.minDimension / 2 - inset)
                drawArc(Color(0x555F574B), -90f, 360f, false, Offset(inset,inset), arcSize, style = Stroke(13.dp.toPx()))
                drawArc(Color(0x887E7969), -90f, 360f, false, Offset(inset,inset), arcSize, style = Stroke(1.5.dp.toPx()))
                for (width in listOf(19f, 12f, 7f)) {
                    drawArc(Color(0xFFFFB83F).copy(alpha = 0.14f), -90f, 360f * fraction, false, Offset(inset,inset), arcSize, style = Stroke(width.dp.toPx(), cap = StrokeCap.Round))
                }
                drawArc(Color(0xFFFFD275), -90f, 360f * fraction, false, Offset(inset,inset), arcSize, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Color(0xFFFFF1B7), -90f, 360f * fraction, false, Offset(inset,inset), arcSize, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            }
            val textScale = ringSize.value / 248f / configuration.fontScale.coerceAtLeast(1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(.76f)) {
                Text(countdown, color = Color.White, fontSize = (48 * textScale).sp, fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Add 5 minutes", onClick = onExtend)
                        .padding(horizontal = 8.dp, vertical = 2.dp))
                Text(if (paused) "paused" else "left", color = Color.White, fontFamily = Outfit, fontSize = (17 * textScale).sp)
                Spacer(Modifier.height(ringSize * .025f))
                Text("Tap time to add 5 min", color = FocusMuted, fontFamily = Outfit, fontSize = (10 * textScale).sp, maxLines = 1)
                Spacer(Modifier.height(ringSize * .045f))
                Row(Modifier.width(ringSize * .45f).height(ringSize * .135f)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xCC3C3B39), Color(0xE6121927))))
                    .border(1.dp, Color(0x997F693E), RoundedCornerShape(50))
                    .clickable(onClick = if (paused) onResume else onPause),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null, tint = Color.White, modifier = Modifier.size(ringSize * .055f))
                    Spacer(Modifier.width(8.dp))
                    Text(if (paused) "Resume" else "Pause", color = Color.White, fontFamily = Outfit, fontSize = (12 * textScale).sp)
                }
            }
        }
        if (!taskLabel.isNullOrBlank()) Text(taskLabel, color = Color.White, fontFamily = Outfit, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActiveFocusEdit("Add contacts", Icons.Outlined.People, { contacts = true }, Modifier.weight(1f))
            ActiveFocusEdit("Add apps", Icons.Outlined.Apps, { appsOpen = true }, Modifier.weight(1f))
            ActiveFocusEdit("Change sounds", Icons.Outlined.MusicNote, { soundsOpen = true }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xBB111D30)).border(1.dp, Color(0xFFD3B27B), CircleShape)
            .combinedClickable(onClick = {}, onLongClick = onEnd, onLongClickLabel = "End Focus"), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Stop, "Hold to end Focus", tint = Color(0xFFFFE8C4), modifier = Modifier.size(26.dp))
        }
        Text("Hold to end", color = Color.White, fontFamily = Outfit, fontSize = 12.sp)
    }
    if (contacts) {
        val groupId = groups.firstOrNull()?.id
        if (groupId != null) FocusGroupContactPicker(groupId, onDismiss = { contacts = false })
        else { contacts = false; onAddSomeone() }
    }
    if (appsOpen) AllowedAppsDialog(apps, icons, FocusAppAccess.selected(context),
        onDismiss = { appsOpen = false }, onSave = { FocusAppAccess.save(context, it); appsOpen = false })
    if (soundsOpen) FocusPopupSheet(onDismiss = { soundsOpen = false }, heightFraction = 0.6f, maxWidth = 480.dp) {
        FocusSoundsSection(heading = "Change sounds")
        FocusPrimaryAction("Done", action = { soundsOpen = false })
    }
}

@Composable
private fun ActiveFocusEdit(label: String, icon: ImageVector, action: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(FocusSurface).border(1.dp, FocusBorder, RoundedCornerShape(18.dp))
        .clickable(onClick = action).padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontFamily = Outfit, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

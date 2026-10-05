package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.*
import com.lumen.launcher.data.*
import com.lumen.launcher.ui.AppIcon

@Composable
fun FocusSetupSheet(
    people: List<FocusPerson>, settings: FocusPolicySettings, groups: List<FocusPeopleGroup>, capabilityNote: String,
    dismissSignal: Int = 0, taskLabel: String? = null, doneEntries: List<FocusDoneEntry> = emptyList(),
    onPickTask: () -> Unit = {}, onDuration: (Int) -> Unit, onOpenPeople: () -> Unit,
    onSettingsChange: (FocusPolicySettings) -> Unit, onStart: (Int) -> Unit, onCustomDuration: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val selectedGroups by repo.selectedGroups.collectAsState()
    val group = groups.filter { it.id in selectedGroups }.joinToString(", ") { it.title }.ifBlank { "" }
    var popup by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(dismissSignal) { popup = ""; revision++ }
    val selected = remember(revision) { FocusAppAccess.selected(context) }
    val apps by produceState<List<AppInfo>>(emptyList(), revision) {
        value = AppRepository(context).loadLaunchableApps().distinctBy { it.packageName }.filter { it.packageName in selected }
    }
    val icons = remember { IconCache(context) }
    val sound = remember(revision) { FocusSoundPrefs.sound(context) }
    val start = FocusStartButtonAction { onStart(settings.lastDurationMinutes) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FocusTimerHero(settings.lastDurationMinutes, group, taskLabel, onPickTask, start)
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(15,30,60,90,0).forEach { minutes ->
                val active = if(minutes == 0) settings.lastDurationMinutes !in listOf(15,30,60,90) else settings.lastDurationMinutes == minutes
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.weight(1f).fillMaxHeight().clip(shape).background(if(active) FocusGradient else FocusSurface)
                    .border(1.dp, if(active) FocusAccent else Color(0xFF424B6E), shape)
                    .clickable { if(minutes == 0) onCustomDuration() else onDuration(minutes) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if(minutes == 0) Icon(Icons.Outlined.Tune, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    else Text("$minutes", color = Color.White, fontSize = 17.sp)
                    Text(if(minutes == 0) "Custom" else "min", color = if (active) Color.White else FocusMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        FocusSummaryRow(Icons.Outlined.People, "Who can reach you?", group, { popup = "people" }) {
            people.take(3).forEach { FocusContactAvatar(it, 27.dp) }
            Icon(Icons.Outlined.Add, "Manage people", tint = FocusAccent, modifier = Modifier.size(22.dp))
        }
        FocusSummaryRow(Icons.Outlined.Apps, "Allowed apps", apps.joinToString(", ") { it.label }.ifBlank { "" }, { popup = "apps" }) {
            apps.take(3).forEach { AppIcon(it.packageName, it.activityName, 26.dp, icons, showNotificationBadge = false) }
        }
        FocusSummaryRow(Icons.Outlined.MusicNote, "Focus sound", if (sound == FocusSound.Off) "" else sound.title, { popup = "sound" }) {
            if (sound != FocusSound.Off) Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))) { SoundArt(sound) }
        }
        FocusSummaryRow(Icons.Outlined.Settings, "Additional settings", "", { popup = "options" }) {}
    }
    if(popup.isNotBlank()) FocusPopupSheet(onDismiss = { popup = ""; revision++ }) {
        when(popup) {
            "people" -> FocusGroupCards(dismissSignal)
            "apps" -> FocusAppsSection(dismissSignal)
            "sound" -> FocusSoundsSection(dismissSignal)
            else -> {
                Text("Additional settings", color = Color.White, fontSize = 20.sp)
                FocusOption("Repeated callers", settings.allowRepeatedCallers) { onSettingsChange(settings.copy(allowRepeatedCallers = it)) }
                FocusOption("Allow alarms", settings.allowAlarms) { onSettingsChange(settings.copy(allowAlarms = it)) }
                FocusOption("Calendar reminders", settings.allowCalendarReminders) { onSettingsChange(settings.copy(allowCalendarReminders = it)) }
                FocusOption("Quiet visuals", settings.silenceEveryoneElse) { onSettingsChange(settings.copy(silenceEveryoneElse = it)) }
                FocusRingCheck()
            }
        }
        FocusPrimaryAction("Done", action = { popup = ""; revision++ })
    }
}

@Composable
private fun FocusSummaryRow(icon: ImageVector, title: String, subtitle: String, action: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().height(61.dp).clip(shape)
        .background(Brush.horizontalGradient(listOf(Color(0xEE102034), Color(0xE6091421))))
        .border(.7.dp, Color(0x303F607C), shape).clickable(onClick = action).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(25.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = FocusMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), content = trailing)
        Icon(Icons.Outlined.ChevronRight, null, tint = FocusMuted, modifier = Modifier.size(18.dp))
    }
}
@Composable
private fun FocusOption(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.White)
        Switch(checked, change)
    }
}

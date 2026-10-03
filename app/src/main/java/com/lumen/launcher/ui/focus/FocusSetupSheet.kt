package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.*

@Composable
fun FocusSetupSheet(people: List<FocusPerson>, settings: FocusPolicySettings, groups: List<FocusPeopleGroup>,
    capabilityNote: String, onDuration: (Int) -> Unit, onOpenPeople: () -> Unit,
    onSettingsChange: (FocusPolicySettings) -> Unit, onStart: (Int) -> Unit, onCustomDuration: () -> Unit) {
    var lists by remember { mutableStateOf(false) }
    val duration = settings.lastDurationMinutes
    val start = FocusStartButtonAction { onStart(duration) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusLandscapeHero()
        Text("1. How long do you want to focus?", color = Color.White, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(15, 30, 60, 120) + 0).forEach { mins ->
                val selected = duration == mins || (mins == 0 && duration !in listOf(15,30,60,120))
                Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF343F92) else Color(0xFF101B27))
                    .border(1.dp, if (selected) FocusAccent else Color(0xFF334251), RoundedCornerShape(12.dp))
                    .clickable { if (mins == 0) onCustomDuration() else onDuration(mins) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (mins == 0) { if (selected) "$duration" else "✎" } else "$mins", color = Color.White, fontSize = 16.sp, lineHeight = 19.sp)
                    Text(if (mins == 0) "Custom" else "min", color = FocusMuted, fontSize = 10.sp, lineHeight = 13.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("2. Who can call you?", Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
            TextButton(onClick = { lists = true }) { Text("Saved lists ›", color = FocusAccent, fontSize = 11.sp) }
        }
        FocusGlass {
        people.forEachIndexed { index, person ->
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenPeople).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FocusContactAvatar(person, 34.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(person.name, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
                    Text(person.reach.label(), color = FocusMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
                FocusContactIndicators(person)
                Text("⋮", Modifier.padding(start = 10.dp), color = FocusMuted, fontSize = 20.sp)
            }
        }
        Text("＋ Add someone", Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(FocusSurface).clickable(onClick = onOpenPeople).padding(12.dp), color = Color.White, fontSize = 14.sp)
        }
        Text("Calls enabled in this list can ring. Other ordinary phone calls are silenced. Message exceptions use Android favorites, not this list.", color = FocusMuted, fontSize = 10.sp, lineHeight = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("3. Additional settings", Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
            FocusSettingsButton()
        }
        FocusGlass {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    SetupToggle("Repeated callers", settings.allowRepeatedCallers) { onSettingsChange(settings.copy(allowRepeatedCallers = it)) }
                    SetupToggle("Calendar reminders", settings.allowCalendarReminders) { onSettingsChange(settings.copy(allowCalendarReminders = it)) }
                }
                Column(Modifier.weight(1f)) {
                    SetupToggle("Allow alarms", settings.allowAlarms) { onSettingsChange(settings.copy(allowAlarms = it)) }
                    SetupToggle("Hide alert visuals", settings.silenceEveryoneElse) { onSettingsChange(settings.copy(silenceEveryoneElse = it)) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF9A7FFF), Color(0xFF567DFF))))
            .clickable(onClick = start), contentAlignment = Alignment.Center) {
            Text("▶  Start Focus", color = Color(0xFF080E24), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("You can switch Spaces anytime. Focus will keep running.", color = FocusMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
    if (lists) FocusListsDialog(onDismiss = { lists = false })
}

@Composable
private fun SetupToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
        Switch(checked, change, colors = SwitchDefaults.colors(checkedTrackColor = FocusAccent, checkedThumbColor = Color.White))
    }
}

package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.*
import com.lumen.launcher.ui.theme.Outfit

@Composable
fun FocusSetupSheet(
    people: List<FocusPerson>,
    settings: FocusPolicySettings,
    groups: List<FocusPeopleGroup>,
    capabilityNote: String,
    dismissSignal: Int = 0,
    taskLabel: String? = null,
    doneEntries: List<com.lumen.launcher.focus.FocusDoneEntry> = emptyList(),
    onPickTask: () -> Unit = {},
    onDuration: (Int) -> Unit,
    onOpenPeople: () -> Unit,
    onSettingsChange: (FocusPolicySettings) -> Unit,
    onStart: (Int) -> Unit,
    onCustomDuration: () -> Unit
) {
    var lists by remember { mutableStateOf(false) }
    val dismissBaseline = remember { dismissSignal }
    LaunchedEffect(dismissSignal) {
        if (dismissSignal != dismissBaseline) lists = false
    }
    LaunchedEffect(Unit) {
        if (!settings.silenceEveryoneElse) {
            onSettingsChange(settings.copy(silenceEveryoneElse = true))
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val selectedGroups by remember { FocusAllowedPeopleRepository.get(context) }.selectedGroups.collectAsState()
    val groupLabel = groups.filter { it.id in selectedGroups }.joinToString(", ") { it.title }.ifBlank { "Yourself" }
    val duration = settings.lastDurationMinutes
    val start = FocusStartButtonAction {
        onSettingsChange(settings.copy(silenceEveryoneElse = true))
        onStart(duration)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FocusTimerHero(minutes = duration, group = groupLabel, taskLabel = taskLabel, onPickTask = onPickTask, onStart = start)
        FocusDoneSection(doneEntries)

        Text("1. How long do you want to focus?", color = Color.White, fontSize = 15.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(15, 30, 60, 120) + 0).forEach { mins ->
                val selected = duration == mins || (mins == 0 && duration !in listOf(15, 30, 60, 120))
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .then(
                            if (selected) Modifier.shadow(10.dp, shape, ambientColor = FocusAccent.copy(0.45f), spotColor = FocusAccent.copy(0.55f))
                            else Modifier
                        )
                        .clip(shape)
                        .background(if (selected) FocusSelectionGradient else Brush.linearGradient(listOf(FocusCard, FocusCard)))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            brush = if (selected) Brush.linearGradient(listOf(FocusBorderGlow, FocusAccentEnd))
                            else Brush.linearGradient(listOf(FocusBorder, FocusBorder)),
                            shape = shape
                        )
                        .clickable { if (mins == 0) onCustomDuration() else onDuration(mins) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (mins == 0) {
                            if (selected) "$duration" else "✎"
                        } else {
                            "$mins"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Outfit,
                        lineHeight = 20.sp
                    )
                    Text(
                        if (mins == 0) "Custom" else "min",
                        color = FocusMuted,
                        fontSize = 11.sp,
                        fontFamily = Outfit,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        Text("2. Who can reach you?", color = Color.White, fontSize = 15.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
        Text("Choose which people can still call during Focus.", color = FocusMuted, fontSize = 12.sp, fontFamily = Outfit)
        FocusGroupCards(dismissSignal)
        FocusAppsSection(dismissSignal = dismissSignal)
        FocusSoundsSection(dismissSignal = dismissSignal)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("5. Additional settings", Modifier.weight(1f), color = Color.White, fontSize = 15.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
            FocusSettingsButton(dismissSignal = dismissSignal)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupSettingCard(
                    Modifier.weight(1f),
                    Icons.Outlined.Phone,
                    "Repeated callers",
                    "Same number again",
                    settings.allowRepeatedCallers
                ) { onSettingsChange(settings.copy(allowRepeatedCallers = it, silenceEveryoneElse = true)) }
                SetupSettingCard(
                    Modifier.weight(1f),
                    Icons.Outlined.Alarm,
                    "Allow alarms",
                    "Timers & clocks",
                    settings.allowAlarms
                ) { onSettingsChange(settings.copy(allowAlarms = it, silenceEveryoneElse = true)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupSettingCard(
                    Modifier.weight(1f),
                    Icons.Outlined.CalendarMonth,
                    "Calendar reminders",
                    "Events & alerts",
                    settings.allowCalendarReminders
                ) { onSettingsChange(settings.copy(allowCalendarReminders = it, silenceEveryoneElse = true)) }
                SetupSettingCard(
                    Modifier.weight(1f),
                    Icons.Outlined.NightsStay,
                    "Quiet visuals",
                    "Hide banners",
                    settings.silenceEveryoneElse
                ) { onSettingsChange(settings.copy(silenceEveryoneElse = it)) }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = FocusAccent.copy(0.4f), spotColor = FocusAccent.copy(0.5f))
                .clip(RoundedCornerShape(28.dp))
                .background(FocusGradient)
                .clickable(onClick = start),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.PlayArrow, null, tint = FocusCtaText, modifier = Modifier.size(22.dp))
                Text(
                    "Start Focus ($duration min)",
                    color = FocusCtaText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Outfit
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Info, null, tint = FocusMuted, modifier = Modifier.size(14.dp))
            Text(
                "You can switch Spaces anytime. Focus will keep running.",
                color = FocusMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = Outfit
            )
        }
    }
    if (lists) FocusListsDialog(dismissSignal = dismissSignal, onDismiss = { lists = false })
}

@Composable
private fun SetupSettingCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(72.dp)
            .clip(shape)
            .background(FocusCard)
            .border(1.dp, FocusBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = FocusIconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 2, lineHeight = 14.sp)
            Text(subtitle, color = FocusMuted, fontSize = 10.sp, fontFamily = Outfit, maxLines = 1)
        }
        Switch(
            checked,
            onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = FocusAccent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF1A1F35),
                uncheckedThumbColor = FocusMuted
            )
        )
    }
}

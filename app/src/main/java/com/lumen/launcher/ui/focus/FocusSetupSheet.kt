package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lumen.launcher.focus.FocusAllowedPeopleRepository
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.*

@Composable
fun FocusSetupSheet(
    people: List<FocusPerson>,
    settings: FocusPolicySettings,
    groups: List<FocusPeopleGroup>,
    capabilityNote: String,
    dismissSignal: Int = 0,
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
    // Core promise — always on for setup; not an optional toggle here.
    LaunchedEffect(Unit) {
        if (!settings.silenceEveryoneElse) {
            onSettingsChange(settings.copy(silenceEveryoneElse = true))
        }
    }
    val duration = settings.lastDurationMinutes
    val start = FocusStartButtonAction {
        onSettingsChange(settings.copy(silenceEveryoneElse = true))
        onStart(duration)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusLandscapeHero()

        Text("1. How long do you want to focus?", color = Color.White, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(15, 30, 60, 120) + 0).forEach { mins ->
                val selected = duration == mins || (mins == 0 && duration !in listOf(15, 30, 60, 120))
                Column(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected && !com.lumen.launcher.ui.theme.LumenPalette.whiteGlass) FocusSelectionGradient else if (selected) Brush.linearGradient(listOf(FocusCardSelected, FocusCardSelected)) else Brush.linearGradient(listOf(FocusCard, FocusInk)))
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            brush = if (selected) Brush.linearGradient(listOf(FocusBorderGlow, FocusAccentEnd)) else Brush.linearGradient(listOf(FocusBorder, FocusBorder)),
                            shape = RoundedCornerShape(14.dp)
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
                        fontSize = 16.sp,
                        lineHeight = 19.sp
                    )
                    Text(
                        if (mins == 0) "Custom" else "min",
                        color = FocusMuted,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        Text("2. Who can reach you?", color = Color.White, fontSize = 15.sp)
        Text("Choose which people can still call during Focus.", color = FocusMuted, fontSize = 12.sp)
        FocusGroupCards(dismissSignal)
        FocusAppsSection()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("4. Additional settings", Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
            FocusSettingsButton()
        }
        FocusGlass {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    SetupToggle("Repeated callers", settings.allowRepeatedCallers) {
                        onSettingsChange(settings.copy(allowRepeatedCallers = it, silenceEveryoneElse = true))
                    }
                    SetupToggle("Calendar reminders", settings.allowCalendarReminders) {
                        onSettingsChange(settings.copy(allowCalendarReminders = it, silenceEveryoneElse = true))
                    }
                }
                Column(Modifier.weight(1f)) {
                    SetupToggle("Allow alarms", settings.allowAlarms) {
                        onSettingsChange(settings.copy(allowAlarms = it, silenceEveryoneElse = true))
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(FocusGradient)
                .clickable(onClick = start),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▶  Start Focus",
                color = FocusCtaText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "You can switch Spaces anytime. Focus will keep running.",
            color = FocusMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
    if (lists) FocusListsDialog(dismissSignal = dismissSignal, onDismiss = { lists = false })
}

@Composable
private fun SetupToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
        Switch(
            checked,
            change,
            colors = SwitchDefaults.colors(
                checkedTrackColor = FocusAccent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = FocusBorder,
                uncheckedThumbColor = FocusMuted
            )
        )
    }
}

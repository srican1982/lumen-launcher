package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.FocusAllowedPeopleRepository
import com.lumen.launcher.focus.FocusAppAccess
import com.lumen.launcher.focus.FocusDoneEntry
import com.lumen.launcher.focus.FocusPeopleGroup
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.focus.FocusPolicySettings
import com.lumen.launcher.focus.FocusSound
import com.lumen.launcher.focus.FocusSoundPrefs
import com.lumen.launcher.ui.theme.Outfit

/**
 * Daily Focus page after guided setup: one-tap start from last settings.
 * Tapping duration / people / apps / sound opens that single elegant step.
 */
@Composable
fun FocusSetupSheet(
    people: List<FocusPerson>,
    settings: FocusPolicySettings,
    groups: List<FocusPeopleGroup>,
    capabilityNote: String,
    dismissSignal: Int = 0,
    taskLabel: String? = null,
    doneEntries: List<FocusDoneEntry> = emptyList(),
    onPickTask: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onDuration: (Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenPeople: () -> Unit,
    onSettingsChange: (FocusPolicySettings) -> Unit,
    onStart: (Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onCustomDuration: () -> Unit,
    onEditStep: (Int) -> Unit = {},
    onReplayGuide: () -> Unit = {}
) {
    val duration = settings.lastDurationMinutes
    val start = FocusStartButtonAction { onStart(duration) }
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FocusDurationReferenceRing(duration, taskLabel=taskLabel, onPickTask=onPickTask, onStart=start)
        Text("1. How long do you want to focus?",color=Color.White,fontFamily=Outfit,fontSize=17.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(15,30,60,120).forEach { value ->
                DurationChip(top="$value",bottom="min",active=duration==value,modifier=Modifier.weight(1f),onClick={onDuration(value)})
            }
            DurationChip(top=null,bottom="Custom",active=duration !in listOf(15,30,60,120),modifier=Modifier.weight(1f),onClick=onCustomDuration,icon=false)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("2. Who can reach you?",color=Color.White,fontFamily=Outfit,fontSize=17.sp)
            TextButton(onClick=onOpenPeople) { Text("Manage",color=Color.LightGray) }
        }
        FocusGroupCards(dismissSignal)
        Text("3. Which apps can you use?",color=Color.White,fontFamily=Outfit,fontSize=17.sp)
        FocusAppsSection(dismissSignal,showHeading=false)
        FocusSoundsSection(dismissSignal,heading="4. Focus sound")
        Text("5. Additional settings",color=Color.White,fontFamily=Outfit,fontSize=17.sp)
        FocusToggle("Allow repeated calls",settings.allowRepeatedCallers) { onSettingsChange(settings.copy(allowRepeatedCallers=it)) }
        FocusToggle("Allow alarms",settings.allowAlarms) { onSettingsChange(settings.copy(allowAlarms=it)) }
        FocusToggle("Allow calendar reminders",settings.allowCalendarReminders) { onSettingsChange(settings.copy(allowCalendarReminders=it)) }
        FocusToggle("Hide notification visuals",settings.silenceEveryoneElse) { onSettingsChange(settings.copy(silenceEveryoneElse=it)) }
        FocusPrimaryAction("Start Focus · $duration min",action=start,showCheck=false)
        Text("You can switch Spaces anytime. Focus will keep running.",color=FocusMuted,fontFamily=Outfit,fontSize=12.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=onReplayGuide) { Text("How Focus works",color=Color.LightGray) }
            FocusSettingsButton(dismissSignal=dismissSignal)
        }
    }
}

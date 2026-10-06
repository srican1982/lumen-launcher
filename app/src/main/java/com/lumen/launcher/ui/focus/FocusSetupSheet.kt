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
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val selectedGroups by repo.selectedGroups.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(dismissSignal) { revision++ }

    val duration = settings.lastDurationMinutes
    val groupLabel = groups.filter { it.id in selectedGroups }
        .joinToString { it.title }
        .ifBlank { "Yourself" }
    val appCount = remember(revision) { FocusAppAccess.selected(context).size }
    val sound = remember(revision) { FocusSoundPrefs.sound(context) }
    val soundLabel = if (sound == FocusSound.Off) "Off" else sound.title
    val start = FocusStartButtonAction {
        onSettingsChange(settings.copy(silenceEveryoneElse = true))
        onStart(duration)
    }

    LaunchedEffect(Unit) {
        if (!settings.silenceEveryoneElse) {
            onSettingsChange(settings.copy(silenceEveryoneElse = true))
        }
    }

    val stage = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(stage)
                .background(Brush.verticalGradient(listOf(Color(0xCC121C30), Color(0xB80A121F))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), stage)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FocusGuideRing(
                primary = "%d:%02d".format(duration, 0),
                secondary = if (taskLabel.isNullOrBlank()) "Tap time to change · or ▶ to start"
                else "Focus on $taskLabel",
                showPlay = true,
                onPlay = start,
                onPrimaryClick = { onEditStep(0) }
            )

            // One summary line — each chip opens only that step.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FocusSummaryChip(groupLabel, Modifier.weight(1f)) { onEditStep(1) }
                FocusSummaryChip(
                    if (appCount == 1) "1 app" else "$appCount apps",
                    Modifier.weight(1f)
                ) { onEditStep(2) }
                FocusSummaryChip(soundLabel, Modifier.weight(1f)) { onEditStep(3) }
            }
        }

        FocusPrimaryAction(
            label = "Start Focus · $duration min",
            action = start,
            showCheck = false
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPickTask) {
                Text(
                    if (taskLabel.isNullOrBlank()) "Pick task" else "Task: $taskLabel",
                    color = FocusAccent,
                    fontFamily = Outfit,
                    maxLines = 1
                )
            }
            TextButton(onClick = onReplayGuide) {
                Text("How Focus works", color = FocusMuted, fontFamily = Outfit)
            }
            FocusSettingsButton(dismissSignal = dismissSignal)
        }

        if (doneEntries.isNotEmpty()) {
            FocusDoneSection(doneEntries)
        }

        Text(
            capabilityNote.takeIf { it.isNotBlank() } ?: "Quiet mode with your saved exceptions.",
            color = FocusMuted,
            fontFamily = Outfit,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FocusSummaryChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontFamily = Outfit,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

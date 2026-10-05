package com.lumen.launcher.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.*

@Composable
internal fun FocusOnboardingScreen(
    step: Int,
    minutes: Int,
    dismissSignal: Int,
    onStep: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onCustom: () -> Unit,
    onFinish: () -> Unit,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val selected by repo.selectedGroups.collectAsState()
    val groups by repo.groups.collectAsState()
    val people by repo.people.collectAsState()
    val titles = listOf("Duration", "People", "Apps", "Ready")
    val start = FocusStartButtonAction(onReady = onStart)
    BackHandler { if (step > 0) onStep(step - 1) else onFinish() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (step > 0) onStep(step - 1) else onFinish() }) {
                Text(if (step > 0) "Back" else "Not now", color = FocusMuted)
            }
            TextButton(onClick = onFinish) { Text("Skip guide", color = FocusMuted) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            titles.forEachIndexed { index, title ->
                Column(Modifier.weight(1f).clickable(enabled = index < step) { onStep(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(32.dp).background(if (index == step) FocusAccent else Color(0xCC101C30), CircleShape)
                        .border(1.dp, if (index <= step) FocusAccent else FocusMuted, CircleShape), contentAlignment = Alignment.Center) {
                        Text(if (index < step) "\u2713" else "${index + 1}", color = Color.White)
                    }
                    Text(title, color = if (index == step) Color.White else FocusMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Text("Step ${step + 1} of 4", color = FocusMuted, fontSize = 13.sp)
        Text(listOf("Choose your time", "Who can reach you?", "Choose your allowed apps", "Ready to focus")[step], color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        when (step) {
            0 -> {
                Box(Modifier.align(Alignment.CenterHorizontally).size(152.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0xAA071321), radius = size.minDimension / 2 - 8.dp.toPx())
                        drawCircle(Color(0xFFF1C779), radius = size.minDimension / 2 - 4.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawArc(FocusAccent, -90f, 285f, false, style = Stroke(5.dp.toPx()))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$minutes min", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        Text("A little time for you", color = FocusMuted, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { value ->
                        FilterChip(selected = minutes == value, onClick = { onDuration(value) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = FocusAccent, containerColor = Color(0xB3101C30)), label = { Text("$value", color = Color.White) }, modifier = Modifier.weight(1f))
                    }
                }
                TextButton(onClick = onCustom) { Text("Set a custom duration", color = FocusAccent) }
                Text("Next, choose people and apps. Sound is optional.", color = FocusMuted)
            }
            1 -> {
                FocusGlass { Text("Duration  ·  $minutes min", color = Color.White) }
                Text("Choose who can still call you. Tap a group to add contacts; use its circle to select it.", color = FocusMuted)
                FocusGroupCards(dismissSignal)
                Text(if (people.isEmpty()) "No selected callers. You can add people later." else "${people.size} selected contacts", color = FocusMuted)
            }
            2 -> {
                FocusGlass {
                    Text("Duration  ·  $minutes min", color = Color.White)
                    Text("People  ·  " + groups.filter { it.id in selected }.joinToString { it.title }.ifBlank { "No one" }, color = FocusMuted)
                }
                Text("Choose apps that stay available in Lumen during Focus. Phone and emergency access remain available.", color = FocusMuted)
                FocusAppsSection(dismissSignal, showHeading = false)
            }
            3 -> {
                FocusGlass {
                    Text("Duration  ·  $minutes min", color = Color.White)
                    Text("People  ·  " + groups.filter { it.id in selected }.joinToString { it.title }.ifBlank { "No one" }, color = FocusMuted)
                    Text("Apps  ·  ${FocusAppAccess.selected(context).size} selected", color = FocusMuted)
                }
                FocusSoundsSection(dismissSignal, heading = "Set the tone")
                Text("Your choices are saved. You can change them anytime on the Focus page.", color = FocusMuted)
            }
        }
        if (step < 3) FocusPrimaryAction("Continue", action = { onStep(step + 1) }, showCheck = false)
        else {
            FocusPrimaryAction("Start Focus · $minutes min", action = start, showCheck = false)
            TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Finish setup without starting", color = FocusMuted) }
        }
    }
}

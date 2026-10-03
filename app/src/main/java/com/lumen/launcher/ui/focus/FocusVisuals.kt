package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.*
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.launch

/** Exact Focus theme from the Deep Focus mock — deep navy + purple/blue glow. */
internal val FocusAccent = Color(0xFF8B80F8)
internal val FocusAccentEnd = Color(0xFF4C73F8)
internal val FocusMuted = Color(0xFFA1A5B1)
internal val FocusInk = Color(0xFF0B0E14)
internal val FocusCard = Color(0xFF1A1F2A)
internal val FocusCardSelected = Color(0xFF252B4A)
internal val FocusBorder = Color(0xFF2A3140)
internal val FocusBorderGlow = Color(0xFF6E78F0)
internal val FocusIconTint = Color(0xFFACB4E0)
internal val FocusCtaText = Color(0xFF0B0E18)
internal val FocusGradient = Brush.horizontalGradient(listOf(FocusAccent, FocusAccentEnd))
internal val FocusTitleGradient = Brush.horizontalGradient(listOf(Color(0xFFB4C8FF), Color(0xFFB49BFF)))
internal val FocusSurface = Brush.linearGradient(listOf(Color(0xF21A1F2A), Color(0xF0121620)))

@Composable
internal fun FocusGlass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FocusSurface)
            .border(1.dp, FocusBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun FocusHeader(greeting: String, weatherContent: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")), color = FocusMuted, fontSize = 11.sp)
            Text(greeting, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp)
            Text("Focus without going unreachable.", color = FocusMuted, fontFamily = Outfit, fontSize = 12.sp, lineHeight = 18.sp)
        }
        weatherContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSettingsButton(label: String? = null) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val settings by repo.settings.collectAsState()
    val people by repo.people.collectAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    if (label == null) IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Settings, "Focus settings", tint = FocusMuted, modifier = Modifier.size(22.dp))
    } else TextButton(onClick = { open = true }) { Text(label, color = FocusAccent, fontSize = 12.sp) }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = FocusInk, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Focus settings", fontFamily = Outfit, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            fun save(next: FocusPolicySettings) {
                repo.setSettings(next)
                scope.launch { FocusSessionManager.get(context).refreshPolicy() }
            }
            FocusToggle("Calls from selected people", settings.allowCallsFromSelected) { save(settings.copy(allowCallsFromSelected = it)) }
            FocusToggle("Messages from ALL Android favorites", settings.allowMessagesFromSelected) { save(settings.copy(allowMessagesFromSelected = it)) }
            FocusToggle("Repeated callers", settings.allowRepeatedCallers) { save(settings.copy(allowRepeatedCallers = it)) }
            FocusToggle("Alarms", settings.allowAlarms) { save(settings.copy(allowAlarms = it)) }
            FocusToggle("Calendar reminders", settings.allowCalendarReminders) { save(settings.copy(allowCalendarReminders = it)) }
            FocusToggle("Hide notification visuals", settings.silenceEveryoneElse) { save(settings.copy(silenceEveryoneElse = it)) }
            FocusRingCheck()
            Text(FocusPolicyController(context).capabilityNote(people, settings), color = FocusMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Button(
                onClick = { open = false },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FocusAccent, contentColor = FocusCtaText)
            ) { Text("Done") }
        }
    }
}

@Composable
private fun FocusToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FocusCard)
            .border(1.dp, FocusBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = Color.White, fontFamily = Outfit, fontSize = 14.sp, lineHeight = 19.sp)
        Switch(
            checked,
            onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = FocusAccent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = FocusBorder,
                uncheckedThumbColor = FocusMuted
            )
        )
    }
}

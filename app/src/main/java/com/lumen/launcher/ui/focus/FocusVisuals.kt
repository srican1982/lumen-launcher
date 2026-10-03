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
import com.lumen.launcher.ui.theme.LumenPalette
import kotlinx.coroutines.launch

/** Focus-local navy surfaces and violet/blue accents, for the normal theme; white glass retains its existing colors. */
internal val FocusAccent get() = if (LumenPalette.whiteGlass) Color(0xFF8B80F8) else Color(0xFF9180FF)
internal val FocusAccentEnd get() = if (LumenPalette.whiteGlass) Color(0xFF4C73F8) else Color(0xFF4877FF)
internal val FocusMuted get() = if (LumenPalette.whiteGlass) Color(0xFFA1A5B1) else Color(0xFFAFBDDA)
internal val FocusInk get() = if (LumenPalette.whiteGlass) Color(0xFF0B0E14) else Color(0xFF071017)
internal val FocusCard get() = if (LumenPalette.whiteGlass) Color(0xFF1A1F2A) else Color(0xFF0F1721)
internal val FocusCardSelected get() = if (LumenPalette.whiteGlass) Color(0xFF252B4A) else Color(0xFF262F74)
internal val FocusBorder get() = if (LumenPalette.whiteGlass) Color(0xFF2A3140) else Color(0xFF263347)
internal val FocusBorderGlow get() = if (LumenPalette.whiteGlass) Color(0xFF6E78F0) else Color(0xFF9C94FF)
internal val FocusIconTint get() = if (LumenPalette.whiteGlass) Color(0xFFACB4E0) else Color(0xFFACB4E0)
internal val FocusCtaText get() = if (LumenPalette.whiteGlass) Color(0xFF0B0E18) else Color(0xFF0B0E18)
internal val FocusSelectionGradient get() = Brush.verticalGradient(listOf(Color(0xFF303171), Color(0xFF243680)))
internal val FocusGradient get() = if (LumenPalette.whiteGlass) Brush.horizontalGradient(listOf(FocusAccent, FocusAccentEnd)) else Brush.horizontalGradient(listOf(FocusAccent, FocusAccentEnd))
internal val FocusTitleGradient get() = if (LumenPalette.whiteGlass) Brush.horizontalGradient(listOf(Color(0xFFB4C8FF), Color(0xFFB49BFF))) else Brush.horizontalGradient(listOf(Color(0xFF88B5FF), Color(0xFF967AFF)))
internal val FocusSurface get() = if (LumenPalette.whiteGlass) Brush.linearGradient(listOf(Color(0xF21A1F2A), Color(0xF0121620))) else Brush.linearGradient(listOf(Color(0xFF101A23), Color(0xFF0B151D)))

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

@Composable
internal fun FocusPrimaryAction(label: String, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(28.dp)).background(FocusGradient).clickable(onClick = action), contentAlignment = Alignment.Center) {
        Text(label, color = FocusCtaText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun FocusPageBanner(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().height(110.dp)) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.lumen.launcher.R.drawable.focus_moonlit_lake), null,
            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(FocusInk, FocusInk.copy(alpha = 0.3f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp)) {
            Text(title, style = androidx.compose.ui.text.TextStyle(brush = FocusTitleGradient), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = FocusMuted, fontSize = 12.sp)
        }
    }
}

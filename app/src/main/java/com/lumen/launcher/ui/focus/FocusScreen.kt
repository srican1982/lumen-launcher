package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.ui.theme.Outfit

@Composable
fun FocusActiveScreen(remainingMs: Long, totalMs: Long, paused: Boolean, people: List<FocusPerson>,
                      capabilityNote: String, onPause: () -> Unit, onResume: () -> Unit,
                      onExtend: () -> Unit, onEnd: () -> Unit, onAddSomeone: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    val minutes = (remainingMs + 59_999L) / 60_000L
    val fraction = (remainingMs.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Quiet,\nwithout becoming\nunreachable.", color = Color(0xFFA9CEFF), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(10.dp))
                Text("Silence distractions.\nKeep the people who matter.", color = FocusMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val inset = 10.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawCircle(Brush.radialGradient(listOf(Color(0x303C9FEF), Color.Transparent)), radius = size.width / 2)
                    drawArc(Color(0xFF253B4B), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(Color(0xFF8EA9FF), Color(0xFFAA8DFF), Color(0xFF44BFEF), Color(0xFF8EA9FF))),
                        -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$minutes", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 48.sp)
                    Text(if (paused) "paused" else "minutes left", color = Color(0xFFD8E4F5), fontFamily = Outfit, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        FocusGlass(Modifier.clickable { details = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Eco, null, tint = Color(0xFF9AF0B2), modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(if (paused) "Focus is paused" else "Focus is ON", color = Color.White, fontFamily = Outfit, fontSize = 15.sp, lineHeight = 20.sp)
                    Text(if (paused) "Your previous sound settings are restored." else "Selected callers can reach you.", color = FocusMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Text("›", color = FocusMuted, fontSize = 24.sp)
            }
        }
        FocusRingCheck()
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Selected people", Modifier.weight(1f), color = Color.White, fontFamily = Outfit, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            Text("${people.size}", Modifier.clip(CircleShape).background(Color(0xFF24313E)).padding(horizontal = 9.dp, vertical = 3.dp), color = FocusMuted, fontSize = 12.sp)
        }
        if (people.isEmpty()) Text("Add people to allow their ordinary phone calls during Focus.", color = FocusMuted, fontSize = 12.sp, lineHeight = 17.sp)
        people.forEach { person ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(FocusSurface)
                .border(1.dp, Color(0xFF283540), RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                FocusContactAvatar(person, 34.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(person.name, color = Color.White, fontFamily = Outfit, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(person.reach.label() + " · saved", color = FocusMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
                FocusContactIndicators(person)
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(FocusSurface).clickable(onClick = onAddSomeone).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF455D77)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(23.dp)) }
            Text("Add someone", Modifier.weight(1f).padding(start = 10.dp), color = Color.White, fontFamily = Outfit, fontSize = 14.sp, lineHeight = 19.sp)
            Text("›", color = FocusMuted, fontSize = 22.sp)
        }
        FocusRingCheck()
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusControl(if (paused) "Resume" else "Pause", if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (paused) onResume else onPause, Modifier.weight(1f))
            FocusControl("+15 min", Icons.Outlined.Schedule, onExtend, Modifier.weight(1f))
            FocusControl("End", Icons.Outlined.Stop, onEnd, Modifier.weight(1f), danger = true)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsOff, null, tint = FocusMuted, modifier = Modifier.size(13.dp))
            Text("  Quiet mode · Android manages interruptions", color = FocusMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0xFF36475F), RoundedCornerShape(20.dp)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AllInclusive, null, tint = FocusAccent, modifier = Modifier.size(18.dp))
            Text("  Focus keeps running when you switch Spaces.", color = FocusAccent, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, containerColor = Color(0xFF15202C),
        title = { Text("Your Focus policy", color = Color.White) }, text = { Text(capabilityNote, color = FocusMuted) },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Got it", color = FocusAccent) } })
}

@Composable
private fun FocusControl(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, danger: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(if (danger) Brush.verticalGradient(listOf(Color(0xFF381D23), Color(0xFF25181D))) else FocusSurface)
        .border(1.dp, if (danger) Color(0xFF72343D) else Color(0xFF344454), RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (danger) Color(0xFFFF8795) else Color.White, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (danger) Color(0xFFFF8795) else Color.White, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = Outfit)
    }
}

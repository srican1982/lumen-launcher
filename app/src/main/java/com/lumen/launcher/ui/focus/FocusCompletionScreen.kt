package com.lumen.launcher.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.FocusCompletion
import com.lumen.launcher.ui.theme.Outfit
import java.util.Date

@Composable
fun FocusCompletionScreen(completion: FocusCompletion, onRestart: () -> Unit, onDismiss: () -> Unit) {
    var summary by remember { mutableStateOf(false) }
    BackHandler { if (summary) summary = false else onDismiss() }
    val orb = (LocalConfiguration.current.screenHeightDp * .27f).coerceIn(156f, 240f).dp
    val minutes = completion.focusedMs / 60_000
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.Eco, null, Modifier.size(30.dp), tint = Color(0xFFC0AAFF))
        Text("Well done!", color = Color.White, fontFamily = Outfit, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.size(orb)) {
            val r = size.minDimension * .39f
            val gold = Color(0xFFFFC367)
            drawCircle(Brush.radialGradient(listOf(gold.copy(alpha=.42f), Color.Transparent)), radius = size.minDimension / 2)
            drawCircle(Brush.radialGradient(listOf(Color(0x22FFCB8B), Color(0x66FFAA55))), radius = r)
            for (w in listOf(18f, 9f, 3f)) drawCircle(gold.copy(alpha=if(w==3f) 1f else .12f), radius=r, style=Stroke(w))
            drawCircle(Color(0xFFFFF5CD), radius=r, style=Stroke(1.5f))
            val stem = Path().apply { moveTo(center.x-5, center.y+r*.48f); cubicTo(center.x+20,center.y,center.x-22,center.y-r*.12f,center.x+16,center.y-r*.5f) }
            drawPath(stem, Color(0xFFFFF6D4), style=Stroke(3.dp.toPx()))
            fun leaf(x: Float, y: Float, dx: Float, dy: Float) {
                val path = Path().apply { moveTo(x,y); quadraticTo(x+dx,y,x+dx,y+dy); quadraticTo(x,y+dy,x,y); close() }
                drawPath(path,Color(0xFFFFF4D0))
            }
            leaf(center.x,center.y-r*.12f,r*.28f,-r*.39f)
            leaf(center.x,center.y+r*.1f,-r*.3f,-r*.28f)
            leaf(center.x,center.y+r*.29f,r*.3f,-r*.24f)
            for ((x,y) in listOf(-.48f to -.2f,.44f to .24f,.24f to -.6f,-.32f to .48f)) {
                val c=Offset(center.x+r*x,center.y+r*y)
                drawLine(Color.White,c-Offset(4f,0f),c+Offset(4f,0f),2f)
                drawLine(Color.White,c-Offset(0f,4f),c+Offset(0f,4f),2f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CompletionStat(if (minutes == 0L) "<1" else "$minutes", "minutes\nfocused")
            CompletionStat("${completion.sessions}", "sessions\ncompleted")
            CompletionStat("${completion.streak}", "day\nstreak")
        }
        Text("“A calmer you\nbuilds a brighter tomorrow.”", color = Color(0xFFC9CBD5), textAlign = TextAlign.Center, fontFamily = Outfit, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth().background(FocusGradient, RoundedCornerShape(22.dp)).border(1.dp,Color(0xFF9CA7FF),RoundedCornerShape(22.dp)).clickable(onClick=onRestart).padding(16.dp), horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayArrow,null,tint=Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Start another session",color=Color.White,fontFamily=Outfit,fontSize=17.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color(0xAA101D30),RoundedCornerShape(22.dp)).border(1.dp,Color(0x667C87C7),RoundedCornerShape(22.dp)).clickable { summary=true }.padding(14.dp), horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.BarChart,null,tint=Color.White)
            Spacer(Modifier.width(8.dp))
            Text("View summary",color=Color.White,fontFamily=Outfit,fontSize=16.sp)
        }
    }
    if (summary) {
        val context=LocalContext.current
        val format=remember { android.text.format.DateFormat.getTimeFormat(context) }
        var details by remember { mutableStateOf(false) }
        FocusPopupSheet(onDismiss={summary=false},heightFraction=.78f,maxWidth=480.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Your Focus summary",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.SemiBold,fontFamily=Outfit,textAlign=TextAlign.Center)
                Text("${format.format(Date(completion.startedAt))} – ${format.format(Date(completion.endedAt))}",color=Color.LightGray,fontSize=17.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    SummaryMetric(if(minutes==0L) "<1" else "$minutes", "min focused", Icons.Outlined.Schedule, Modifier.weight(1f))
                    SummaryMetric("${completion.sessions}", "sessions completed", Icons.Outlined.BarChart, Modifier.weight(1f))
                    SummaryMetric("${completion.streak}", "day streak", Icons.Outlined.LocalFireDepartment, Modifier.weight(1f))
                }
                HorizontalDivider(color=Color.White.copy(alpha=.16f))
                if(completion.sound.isNotBlank()) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Headphones,null,tint=Color.White,modifier=Modifier.size(22.dp))
                        val sound = if(completion.sound=="Off") "Sound off" else "${completion.sound} sound"
                        val people = when(completion.peopleCount) { -1 -> ""; 0 -> "No contacts allowed"; 1 -> "1 contact allowed"; else -> "${completion.peopleCount} contacts allowed" }
                        Text(listOf(sound,people).filter { it.isNotBlank() }.joinToString(" · "),color=Color.LightGray,fontFamily=Outfit,fontSize=14.sp)
                    }
                }
                Text("Paused time is excluded.",color=FocusMuted,fontSize=14.sp)
                FocusPrimaryAction("Done  →",action={summary=false},showCheck=false)
                OutlinedButton(onClick={details=!details},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(24.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.4f))) {
                    Text(if(details) "Hide details" else "View details",color=Color.White,fontSize=17.sp,fontFamily=Outfit)
                }
                if(details) {
                    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.04f),RoundedCornerShape(16.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Session details",color=Color.White,fontWeight=FontWeight.SemiBold)
                        Text("Focused time: ${completion.focusedMs / 60_000} min ${completion.focusedMs / 1000 % 60} sec",color=Color.LightGray)
                        Text("Elapsed time: ${((completion.endedAt-completion.startedAt).coerceAtLeast(0))/60_000} min",color=Color.LightGray)
                        Text("Sessions completed is your all-time total. Your streak counts consecutive days with a finished Focus session.",color=FocusMuted,fontSize=13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionStat(value: String, label: String) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text(value,color=Color.White,fontFamily=Outfit,fontSize=30.sp,fontWeight=FontWeight.Medium)
        Text(label,color=Color.LightGray,fontFamily=Outfit,fontSize=13.sp,textAlign=TextAlign.Center)
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.heightIn(min=132.dp).background(Color.White.copy(alpha=.045f),RoundedCornerShape(18.dp)).border(1.dp,Color.White.copy(alpha=.14f),RoundedCornerShape(18.dp)).padding(horizontal=6.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Icon(icon,null,tint=Color.White,modifier=Modifier.size(24.dp))
        Text(value,color=Color.White,fontSize=27.sp,fontFamily=Outfit,fontWeight=FontWeight.SemiBold)
        Text(label,color=Color.LightGray,fontSize=12.sp,fontFamily=Outfit,textAlign=TextAlign.Center,minLines=2)
    }
}

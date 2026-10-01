package com.lumen.launcher.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.AppCategory
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.ui.social.StickerPackScreen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SocialSpaceCard(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    var usage by remember { mutableStateOf<Map<String, Long>?>(null) }
    var details by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf(false) }
    val apps = state.visibleApps.filter { it.category == AppCategory.Social }
    LaunchedEffect(apps) {
        while (true) {
            usage = withContext(Dispatchers.IO) {
                val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                if (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) != AppOpsManager.MODE_ALLOWED) null
                else runCatching {
                    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                    manager.queryAndAggregateUsageStats(start, System.currentTimeMillis())
                        .filterKeys { key -> apps.any { it.packageName == key } }
                        .mapValues { it.value.totalTimeInForeground }
                }.getOrNull()
            }
            delay(15000)
        }
    }
    fun duration(ms: Long): String {
        val minutes = ms / 60000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
    val accent = Color(0xFFE0B7FF)
    val shape = RoundedCornerShape(26.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(Color(0x99542F70), Color(0x99505286), Color(0x99304651)))).border(1.dp, Color.White.copy(alpha=.25f), shape).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SOCIAL TODAY", color=accent, fontFamily=Outfit, fontSize=12.sp, letterSpacing=2.sp, modifier=Modifier.weight(1f))
                IconButton(onClick = vm::openPrivateSpace) { Icon(Icons.Outlined.Lock, "Open Private Space", tint=accent) }
                IconButton(onClick = { vm.openVoice() }) { Icon(Icons.Outlined.MicNone, "Talk to Lumen", tint=accent) }
            }
            Text(usage?.let { duration(it.values.sum()) } ?: "Your social time", color=Color.White, fontFamily=Outfit, fontSize=32.sp, fontWeight=FontWeight.SemiBold)
            Text(if (usage == null) "See time spent in your social apps." else "Time in social apps today", color=Color.White.copy(alpha=.75f), fontFamily=Outfit, fontSize=13.sp)
            TextButton(onClick = {
                if (usage == null) context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) else details = true
            }) { Text(if (usage == null) "Enable usage access  ›" else "View details  ›", color=accent) }
        }
        Column(Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha=.09f)).border(1.dp, Color.White.copy(alpha=.22f), shape).padding(16.dp)) {
            Text("Create something", color=Color.White, fontFamily=Outfit, fontSize=19.sp, fontWeight=FontWeight.Medium)
            Text("Turn moments into memories", color=Color.White.copy(alpha=.65f), fontFamily=Outfit, fontSize=12.sp)
            Spacer(Modifier.height(14.dp))
            val labels = listOf("Photo", "Scribble", "Quote", "Stickers")
            val icons = listOf(Icons.Outlined.PhotoCamera, Icons.Outlined.Edit, Icons.Outlined.FormatQuote, Icons.Outlined.EmojiEmotions)
            val colors = listOf(Color(0xFFE584B3), Color(0xFF8795F5), Color(0xFF7ACBB9), Color(0xFFF2B66F))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable {
                        when(index) {
                            0 -> vm.openSocialTool(SocialCreateTool.Photo)
                            1 -> vm.openSocialTool(SocialCreateTool.Scribble)
                            2 -> vm.openSocialTool(SocialCreateTool.Quote)
                            else -> stickers = true
                        }
                    }.padding(vertical=4.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(colors[index], colors[index].copy(alpha=.45f)))), contentAlignment=Alignment.Center) {
                            Icon(icons[index], label, tint=Color.White, modifier=Modifier.size(25.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, color=Color.White, fontFamily=Outfit, fontSize=11.sp)
                    }
                }
            }
        }
    }
    if (stickers) StickerPackScreen(onClose = { stickers = false })
    if (details) AlertDialog(onDismissRequest={details=false}, title={Text("Social Today")}, text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val entries = usage.orEmpty().entries.filter { it.value > 0 }.sortedByDescending { it.value }
            if(entries.isEmpty()) Text("No social app usage recorded today.")
            entries.take(12).forEach { entry ->
                Text("${apps.firstOrNull { it.packageName == entry.key }?.label ?: entry.key} · ${duration(entry.value)}")
            }
        }
    }, confirmButton={TextButton(onClick={details=false}) { Text("Done") }})
}

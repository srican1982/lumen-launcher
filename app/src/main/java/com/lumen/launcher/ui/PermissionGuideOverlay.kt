package com.lumen.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.PermissionGuide
import com.lumen.launcher.vm.PermissionGuideKind
import kotlinx.coroutines.delay

/**
 * Short animated coach card before jumping into the exact Android Settings page.
 */
@Composable
fun PermissionGuideOverlay(
    guide: PermissionGuide,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "perm-pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    // Auto-open the deep link after a brief coach moment so the user sees what to toggle.
    LaunchedEffect(guide.kind) {
        delay(1100)
        onOpenSettings()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC05081D))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A2248), Color(0xFF0B101C))))
                    .border(1.dp, Color(0xFF6E7CFF).copy(alpha = glow), RoundedCornerShape(28.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Color(0xFF6E7CFF).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Eco, null, tint = Color(0xFFB8C2FF), modifier = Modifier.size(30.dp))
                }
                Text(
                    guide.title,
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    guide.body,
                    color = Color(0xFF8E99B3),
                    fontFamily = Outfit,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                if (guide.kind == PermissionGuideKind.DndAccess || guide.kind == PermissionGuideKind.NotificationListener) {
                    Spacer(Modifier.height(4.dp))
                    AnimatedLumenToggleRow(glow)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onOpenSettings) {
                    Text("Open settings now", color = Color(0xFF8B80F8), fontFamily = Outfit, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDismiss) {
                    Text("Not now", color = Color(0xFF8E99B3), fontFamily = Outfit)
                }
            }
        }
    }
}

@Composable
private fun AnimatedLumenToggleRow(glow: Float) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF12182A))
            .border(1.dp, Color(0xFF6E7CFF).copy(alpha = glow), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Lumen", Modifier.weight(1f), color = Color.White, fontFamily = Outfit, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        // Fake switch that pulses "on" to show what to look for.
        Box(
            Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF6E7CFF).copy(alpha = 0.55f + glow * 0.35f))
                .padding(3.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

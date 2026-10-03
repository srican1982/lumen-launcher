package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import kotlinx.coroutines.delay

/**
 * Compact Focus control for the TouchPad right panel while a session is running.
 * Tap returns to Focus Space — does not end the session.
 */
@Composable
fun FocusTouchpadButton(
    state: LauncherUiState,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.focusRunning, state.focusPaused) {
        while (state.focusRunning || state.focusPaused) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = if (state.focusPaused) state.focusPausedRemainingMs
    else (state.focusUntil - now).coerceAtLeast(0L)
    val timeLabel = when {
        state.focusPaused -> "Paused"
        remaining < 60_000L -> {
            val secs = ((remaining + 999L) / 1_000L).coerceAtLeast(0)
            "$secs sec left"
        }
        else -> {
            val mins = ((remaining + 59_999L) / 60_000L).coerceAtLeast(0)
            "$mins min left"
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxSize()
            .shadow(10.dp, shape, ambientColor = FocusAccent.copy(0.35f), spotColor = FocusAccent.copy(0.45f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A335F), Color(0xFF1A2248))))
            .border(1.dp, FocusGradient, shape)
            .clickable(onClick = onOpenFocus)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FocusAccent.copy(0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Eco, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "FOCUS",
                color = Color.White.copy(alpha = 0.75f),
                fontFamily = Outfit,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                tint = Color.White.copy(0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
        Column {
            Text(
                if (state.focusPaused) "Paused" else "On",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                timeLabel,
                color = Color(0xFFB8C2FF),
                fontFamily = Outfit,
                fontSize = 13.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap to open",
                color = Color.White.copy(alpha = 0.55f),
                fontFamily = Outfit,
                fontSize = 11.sp
            )
        }
    }
}

@Deprecated("Use FocusTouchpadButton in the TouchPad panel", ReplaceWith("FocusTouchpadButton(state, onOpenFocus, modifier)"))
@Composable
fun FocusStatusPill(
    state: LauncherUiState,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusTouchpadButton(state, onOpenFocus, modifier.fillMaxWidth().height(88.dp))
}

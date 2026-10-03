package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import kotlinx.coroutines.delay

private val FocusGreen = Color(0xFF34D399)

/**
 * Compact pill on other Spaces while Focus keeps running.
 * Tap returns to Focus Space — does not end the session.
 */
@Composable
fun FocusStatusPill(
    state: LauncherUiState,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.focusRunning) {
        while (state.focusRunning) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = if (state.focusPaused) state.focusPausedRemainingMs
    else (state.focusUntil - now).coerceAtLeast(0L)
    val mins = ((remaining + 59_999L) / 60_000L).coerceAtLeast(0)
    val label = when {
        state.focusPaused -> "Focus · Paused"
        else -> "Focus · $mins min left"
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xCC142018))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(FocusGreen.copy(0.55f), Color(0xFF60A5FA).copy(0.35f))),
                shape
            )
            .clickable(onClick = onOpenFocus)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(FocusGreen.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Eco, null, tint = FocusGreen, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = Color.White.copy(0.55f),
            modifier = Modifier.size(18.dp)
        )
    }
}

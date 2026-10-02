package com.lumen.launcher.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
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
import kotlinx.coroutines.delay

private val FocusGreen = Color(0xFF34D399)
private val FocusGreenDeep = Color(0xFF059669)
private val FocusBlue = Color(0xFF60A5FA)
private val FocusPurple = Color(0xFFA78BFA)
private val FocusRed = Color(0xFFFB7185)

@Composable
fun FocusActiveScreen(
    remainingMs: Long,
    totalMs: Long,
    paused: Boolean,
    people: List<FocusPerson>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtend: () -> Unit,
    onEnd: () -> Unit,
    onAddSomeone: () -> Unit
) {
    var nowTick by remember { mutableStateOf(0) }
    LaunchedEffect(paused) {
        while (!paused) {
            nowTick++
            delay(1000)
        }
    }
    val remaining = remainingMs // parent refreshes while running
    val total = totalMs.takeIf { it > 0 } ?: remaining.coerceAtLeast(1L)
    val progress = ((total - remaining).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val mins = (remaining / 60_000L).coerceAtLeast(0)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Quiet, without becoming unreachable.",
            color = Color.White.copy(0.65f),
            fontFamily = Outfit,
            fontSize = 13.sp
        )

        FocusHeroRing(
            progress = progress,
            label = if (paused) "Paused" else "$mins minutes left"
        )

        FocusOnCard()

        CanReachYouCard(people = people, onAddSomeone = onAddSomeone)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FocusControlButton(
                label = if (paused) "Resume" else "Pause",
                icon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                tint = FocusGreen,
                onClick = if (paused) onResume else onPause,
                modifier = Modifier.weight(1f)
            )
            FocusControlButton(
                label = "+15 min",
                icon = Icons.Outlined.Timer,
                tint = FocusBlue,
                onClick = onExtend,
                modifier = Modifier.weight(1f)
            )
            FocusControlButton(
                label = "End",
                icon = Icons.Outlined.Stop,
                tint = FocusRed,
                filled = true,
                onClick = onEnd,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.05f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.NotificationsOff, null, tint = Color.White.copy(0.55f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Silencing distractions · ${people.size} can reach you",
                color = Color.White.copy(0.6f),
                fontFamily = Outfit,
                fontSize = 12.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(Icons.Outlined.AllInclusive, null, tint = FocusGreen.copy(0.7f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Focus keeps running when you switch Spaces.",
                color = Color.White.copy(0.45f),
                fontFamily = Outfit,
                fontSize = 11.sp
            )
        }
        // keep tick referenced
        @Suppress("UNUSED_EXPRESSION")
        nowTick
    }
}

@Composable
private fun FocusHeroRing(progress: Float, label: String) {
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arc.width) / 2f, (size.height - arc.height) / 2f)
            drawArc(
                color = Color.White.copy(0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(FocusBlue, FocusPurple, FocusGreen, FocusBlue)),
                startAngle = -90f,
                sweepAngle = 360f * (1f - progress).coerceIn(0.02f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            label,
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun FocusOnCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC142018))
            .border(1.dp, FocusGreen.copy(0.3f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(FocusGreen.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Eco, null, tint = FocusGreen, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Focus is ON", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                "Everything is silenced except your selected people.",
                color = Color.White.copy(0.6f),
                fontFamily = Outfit,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CanReachYouCard(people: List<FocusPerson>, onAddSomeone: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(0.06f))
            .border(0.8.dp, Color.White.copy(0.12f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Can reach you",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(FocusGreen.copy(0.18f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("${people.size}", color = FocusGreen, fontFamily = Outfit, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (people.isEmpty()) {
            Text(
                "No one selected — everyone is silenced.",
                color = Color.White.copy(0.5f),
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            people.forEach { person ->
                PersonReachRow(person)
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onAddSomeone)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, null, tint = FocusGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("+ Add someone", color = FocusGreen, fontFamily = Outfit, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PersonReachRow(person: FocusPerson) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(person.name, person.avatarColor, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.name,
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                person.reach.label(),
                color = Color.White.copy(0.5f),
                fontFamily = Outfit,
                fontSize = 11.sp
            )
        }
        if (person.reach.allowsCalls) {
            Icon(Icons.Outlined.Call, null, tint = Color.White.copy(0.55f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        if (person.reach.allowsMessages) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White.copy(0.55f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FocusControlButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) tint.copy(0.18f) else Color.White.copy(0.07f))
            .border(1.dp, if (filled) tint.copy(0.45f) else Color.White.copy(0.12f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontFamily = Outfit, fontSize = 12.sp)
    }
}

package com.lumen.launcher.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.focus.FocusAccent
import com.lumen.launcher.ui.focus.FocusCtaText
import com.lumen.launcher.ui.focus.FocusInk
import com.lumen.launcher.ui.focus.FocusMuted
import com.lumen.launcher.ui.focus.FocusPopupSheet
import com.lumen.launcher.ui.focus.FocusPrimaryAction
import com.lumen.launcher.ui.theme.Outfit
import java.util.Calendar
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun FocusDurationDialog(
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
    initialMinutes: Int = 25,
    confirmLabel: String = "Start focus",
    /** Epoch millis of the next calendar event; null hides that quick suggestion. */
    nextEventAtMs: Long? = null
) {
    val clampedInitial = initialMinutes.coerceIn(1, 24 * 60)
    var hours by rememberSaveable { mutableIntStateOf(clampedInitial / 60) }
    var minutes by rememberSaveable { mutableIntStateOf(clampedInitial % 60) }
    val total = (hours * 60 + minutes).coerceIn(0, 24 * 60)
    val valid = total in 1..1440

    fun applyTotal(mins: Int) {
        val safe = mins.coerceIn(1, 1440)
        hours = safe / 60
        minutes = safe % 60
    }

    val untilFivePm = minutesUntilHour(17)
    val untilEvent = nextEventAtMs?.let { target ->
        val mins = ((target - System.currentTimeMillis() + 59_999L) / 60_000L).toInt()
        mins.takeIf { it in 1..1440 }
    }

    FocusPopupSheet(onDismiss = onDismiss, heightFraction = 0.82f) {
        Text(
            "Custom Focus Time",
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
        Text(
            "Swipe the wheels up or down to set hours and minutes.",
            color = FocusMuted,
            fontFamily = Outfit,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassTimeColumn(
                label = "HOURS",
                value = hours,
                display = { it.toString() },
                range = 0..24,
                onChange = { next ->
                    hours = next
                    if (hours == 24) minutes = 0
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                ":",
                color = FocusAccent,
                fontFamily = Outfit,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                modifier = Modifier.padding(top = 18.dp)
            )
            GlassTimeColumn(
                label = "MINUTES",
                value = minutes,
                display = { it.toString().padStart(2, '0') },
                range = if (hours >= 24) 0..0 else 0..59,
                onChange = { minutes = it },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            "Quick suggestions",
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip("45 min", selected = total == 45) { applyTotal(45) }
            SuggestionChip("90 min", selected = total == 90) { applyTotal(90) }
            SuggestionChip("2 hr", selected = total == 120) { applyTotal(120) }
            if (untilEvent != null) {
                SuggestionChip(
                    "Until next event",
                    selected = total == untilEvent,
                    icon = Icons.Outlined.CalendarMonth
                ) { applyTotal(untilEvent) }
            }
            if (untilFivePm != null) {
                SuggestionChip(
                    "Until 5:00 PM",
                    selected = total == untilFivePm,
                    icon = Icons.Outlined.Schedule
                ) { applyTotal(untilFivePm) }
            }
        }

        FocusPrimaryAction(
            label = confirmLabel,
            action = { if (valid) onStart(total) },
            showCheck = false
        )
        if (!valid) {
            Text(
                "Choose at least 1 minute.",
                color = Color(0xFFFF8FA3),
                fontFamily = Outfit,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GlassTimeColumn(
    label: String,
    value: Int,
    display: (Int) -> String,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1
) {
    val values = remember(range, step) { range.step(step).toList() }
    val itemHeight = 48.dp
    val wheelHeight = itemHeight * 3
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(value).coerceAtLeast(0)
    )
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onChange)
    val wellShape = RoundedCornerShape(20.dp)
    val bandShape = RoundedCornerShape(14.dp)
    val idx = values.indexOf(value).coerceAtLeast(0)
    val canUp = idx > 0
    val canDown = idx < values.lastIndex

    fun stepBy(delta: Int) {
        val next = (idx + delta).coerceIn(0, values.lastIndex)
        if (next == idx) return
        scope.launch { listState.animateScrollToItem(next) }
    }

    LaunchedEffect(value, values) {
        val target = values.indexOf(value).coerceAtLeast(0)
        if (listState.isScrollInProgress) return@LaunchedEffect
        val layout = listState.layoutInfo
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        val centerIdx = layout.visibleItemsInfo.minByOrNull { info ->
            abs((info.offset + info.size / 2) - viewportCenter)
        }?.index
        if (centerIdx != target) listState.animateScrollToItem(target)
    }

    LaunchedEffect(listState, values) {
        var lastIdx = -1
        snapshotFlow {
            val layout = listState.layoutInfo
            if (layout.visibleItemsInfo.isEmpty()) return@snapshotFlow -1
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { info ->
                abs((info.offset + info.size / 2) - viewportCenter)
            }?.index ?: -1
        }
            .distinctUntilChanged()
            .filter { it in values.indices }
            .collect { center ->
                if (lastIdx >= 0 && center != lastIdx) {
                    view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= 29) HapticFeedbackConstants.CLOCK_TICK
                        else HapticFeedbackConstants.KEYBOARD_TAP
                    )
                }
                lastIdx = center
                val next = values[center]
                if (next != latestValue) latestOnChange(next)
            }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = FocusMuted, fontFamily = Outfit, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(4.dp))
        Icon(
            Icons.Outlined.KeyboardArrowUp,
            contentDescription = "Higher",
            tint = if (canUp) FocusAccent.copy(alpha = 0.9f) else FocusMuted.copy(alpha = 0.25f),
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = canUp) { stepBy(-1) }
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(wheelHeight)
                .shadow(14.dp, wellShape, ambientColor = Color.Black.copy(0.45f), spotColor = FocusAccent.copy(0.35f))
                .clip(wellShape)
                // Recessed 3D well: darker top lip, lighter floor.
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A0E1C),
                            Color(0xFF161C32),
                            Color(0xFF0E1324)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), FocusAccent.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
                    ),
                    wellShape
                )
        ) {
            // Embossed center selection band
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .padding(horizontal = 8.dp)
                    .shadow(12.dp, bandShape, ambientColor = FocusAccent.copy(0.55f), spotColor = FocusAccent.copy(0.65f))
                    .clip(bandShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF9AA6FF), Color(0xFF5175FF), Color(0xFF3A5AE0))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(0.55f), Color.White.copy(0.08f))
                        ),
                        bandShape
                    )
            )
            LazyColumn(
                state = listState,
                flingBehavior = fling,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = itemHeight),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(values, key = { _, v -> v }) { _, item ->
                    val selected = item == value
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            display(item),
                            color = if (selected) FocusCtaText else FocusMuted.copy(alpha = 0.4f),
                            fontFamily = Outfit,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (selected) 28.sp else 20.sp,
                            modifier = Modifier.graphicsLayer {
                                alpha = if (selected) 1f else 0.5f
                                scaleX = if (selected) 1.08f else 0.92f
                                scaleY = if (selected) 1.08f else 0.92f
                                translationY = if (selected) 0f else 0f
                                // Perspective tilt for off-center rows.
                                rotationX = if (selected) 0f else if (item < value) 18f else -18f
                                cameraDistance = 12f * density
                            }
                        )
                    }
                }
            }
            // Top / bottom fog so neighboring values peek through — scroll cue.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(FocusInk.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, FocusInk.copy(alpha = 0.72f))
                        )
                    )
            )
        }
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = "Lower",
            tint = if (canDown) FocusAccent.copy(alpha = 0.9f) else FocusMuted.copy(alpha = 0.25f),
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = canDown) { stepBy(1) }
        )
    }
}

@Composable
private fun SuggestionChip(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .shadow(
                if (selected) 8.dp else 4.dp,
                shape,
                ambientColor = FocusAccent.copy(if (selected) 0.35f else 0.12f),
                spotColor = FocusAccent.copy(if (selected) 0.4f else 0.15f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (selected) 0.16f else 0.10f),
                        Color.White.copy(alpha = if (selected) 0.04f else 0.03f)
                    )
                )
            )
            .border(
                1.dp,
                if (selected) FocusAccent.copy(alpha = 0.75f) else FocusAccent.copy(alpha = 0.35f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = FocusAccent, modifier = Modifier.size(15.dp))
        Text(
            label,
            color = if (selected) Color.White else FocusAccent,
            fontFamily = Outfit,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun minutesUntilHour(hour24: Int): Int? {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour24)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    val mins = ((target.timeInMillis - now.timeInMillis + 59_999L) / 60_000L).toInt()
    return mins.takeIf { it in 1..1440 }
}

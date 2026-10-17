package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.data.CaptureKind
import com.lumen.launcher.data.EventPresentation
import com.lumen.launcher.data.TodoTime
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay

private val CardGold = Color(0xFFFAC775)
private val CardShape = RoundedCornerShape(28.dp)
private val PillShape = RoundedCornerShape(999.dp)

private val TightLabelStyle = TextStyle(
    fontFamily = Outfit,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.1.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

/** Home calendar / next-item card — layout matched to the glass mock. */
@Composable
internal fun HomeNextUpCard(state: LauncherUiState, vm: LauncherViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val event = EventPresentation.next(state.upcomingEvents, now, state.activeSpace,
        state.workCalendars, state.personalCalendars)
    val task = state.spaceTodos.firstOrNull { !it.done }
    val hasNext = event != null || task != null
    val location = event?.location?.takeIf { it.isNotBlank() }
    val target = event?.begin ?: task?.dueAt
    val countdown = target?.let { countdownLabel(it, now, event != null) }
    val whenText = when {
        event != null -> EventPresentation.whenText(event, now, includeEnd = true)
        task?.dueAt != null -> TodoTime.dayLabel(task.dueAt, now) + " \u00b7 " + TodoTime.timeLabel(task.dueAt)
        else -> null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(0.14f),
                        Color.White.copy(0.07f),
                        Color(0xFF101820).copy(0.42f)
                    )
                )
            )
            .border(
                0.9.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.42f), Color.White.copy(0.12f))
                ),
                CardShape
            )
            .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            // Header: calendar chip + "Next Up" optically centered
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.8.dp, Color.White.copy(0.28f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        null,
                        tint = Color.White.copy(0.95f),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    if (hasNext) "Next Up" else if (state.activeSpace == SpaceKind.Work) "Your work day" else "Your day is clear",
                    color = Color.White.copy(0.95f),
                    style = TightLabelStyle
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                event?.title ?: task?.text ?: "Capture what\u2019s next",
                color = Color.White,
                fontFamily = Outfit,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 23.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )

            // Row 3: time only
            if (whenText != null) {
                Spacer(Modifier.height(5.dp))
                HomeEventLine(Icons.Outlined.Schedule, whenText)
            }

            // Row 4: location (if any) + gold countdown, left-aligned with fields above
            if (location != null || countdown != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (location != null) {
                        Row(
                            Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Place,
                                null,
                                tint = Color.White.copy(0.75f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                location,
                                color = Color.White.copy(0.90f),
                                fontFamily = Outfit,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                    if (countdown != null) CountdownPill(countdown)
                }
            }
        }

        Box(Modifier.width(0.7.dp).height(80.dp).background(Color.White.copy(0.18f)))
        Spacer(Modifier.width(9.dp))
        // Matching glass pills with a shared width and centered icon-label groups.
        Column(
            modifier = Modifier.width(106.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            HomeCardSlimAction(
                if (hasNext) "Remind Me" else "+ Task",
                Icons.Outlined.Alarm
            ) {
                when {
                    event != null -> vm.openCapture(CaptureKind.Reminder)
                    task != null -> vm.openTodoList()
                    else -> vm.openCapture(CaptureKind.Task)
                }
            }
            HomeCardSlimAction(
                if (hasNext) "View Details" else "+ Note",
                Icons.Outlined.Description
            ) {
                when {
                    event != null -> vm.openCalendarEvent(event)
                    task != null -> vm.openTodoList()
                    else -> vm.openCapture(CaptureKind.Note)
                }
            }
        }
    }
}

private fun countdownLabel(target: Long, now: Long, isEvent: Boolean): String {
    val minutes = ((target - now + 59_999) / 60_000).coerceAtLeast(0)
    return when {
        target <= now -> if (isEvent) "Happening now" else "Due now"
        minutes >= 1440 -> "in ${minutes / 1440}d ${(minutes % 1440) / 60}h"
        minutes >= 60 -> "in ${minutes / 60}h ${minutes % 60}m"
        else -> "in ${minutes}m"
    }
}

@Composable
private fun CountdownPill(label: String) {
    Row(
        Modifier
            .clip(PillShape)
            .background(Color.Black.copy(0.22f))
            .border(1.dp, CardGold.copy(0.90f), PillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            Icons.Outlined.Schedule,
            null,
            tint = CardGold,
            modifier = Modifier.size(11.dp)
        )
        Text(
            label,
            color = CardGold,
            fontFamily = Outfit,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun HomeEventLine(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = Color.White.copy(0.75f), modifier = Modifier.size(13.dp))
        Text(
            text,
            color = Color.White.copy(0.92f),
            fontFamily = Outfit,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun HomeCardSlimAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(PillShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFADC9E6).copy(0.23f), Color(0xFF8BB3CF).copy(0.16f))))
            .border(0.7.dp, Color.White.copy(0.15f), PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(0.95f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            label,
            color = Color.White.copy(0.95f),
            fontFamily = Outfit,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

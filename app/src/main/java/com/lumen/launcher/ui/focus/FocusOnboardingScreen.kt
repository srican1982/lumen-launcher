package com.lumen.launcher.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.FocusAllowedPeopleRepository
import com.lumen.launcher.focus.FocusAppAccess
import com.lumen.launcher.focus.FocusPeopleGroup
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.focus.FocusSound
import com.lumen.launcher.focus.FocusSoundPrefs
import com.lumen.launcher.ui.theme.Outfit
import java.util.Calendar
import java.util.Locale

/**
 * Guided Focus setup matching the 4-step mock.
 * [singleStep]=true opens only [step] then returns via [onFinish] (daily edit).
 */
@Composable
internal fun FocusOnboardingScreen(
    step: Int,
    minutes: Int,
    dismissSignal: Int,
    onStep: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onCustom: () -> Unit,
    onFinish: () -> Unit,
    onStart: () -> Unit,
    singleStep: Boolean = false
) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val selected by repo.selectedGroups.collectAsState()
    val groups by repo.groups.collectAsState()
    val revision by repo.listsRevision.collectAsState()
    val people by repo.people.collectAsState()
    val sound = remember(dismissSignal, step) { FocusSoundPrefs.sound(context) }
    val appCount = remember(dismissSignal, step) { FocusAppAccess.selected(context).size }
    val groupTitles = groups.filter { it.id in selected }.joinToString { it.title }.ifBlank { "No one" }
    val start = FocusStartButtonAction(onReady = onStart)

    BackHandler {
        when {
            singleStep -> onFinish()
            step > 0 -> onStep(step - 1)
            else -> onFinish()
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (step == 0 && !singleStep) 8.dp else 10.dp)
    ) {
        if (!singleStep) {
            FocusGuideStepper(step = step, onJump = { if (it < step) onStep(it) })
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onFinish) { Text("Done", color = FocusMuted, fontFamily = Outfit) }
                Text(
                    listOf("Duration", "People", "Apps", "Sound")[step.coerceIn(0, 3)],
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Spacer(Modifier.width(64.dp))
            }
        }

        when (step) {
            0 -> DurationStep(
                minutes = minutes,
                onDuration = onDuration,
                onCustom = onCustom
            )
            1 -> {
                if (!singleStep) FocusSummaryStack(
                    items = listOf("Duration · $minutes min")
                )
                Text(
                    "Select who can still contact you. Calls, messages and important alerts.",
                    color = FocusMuted,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                FocusGuideGroupGrid(dismissSignal)
            }
            2 -> {
                if (!singleStep) FocusSummaryStack(
                    items = listOf(
                        "Duration · $minutes min",
                        "People · $groupTitles"
                    )
                )
                Text(
                    "Choose apps that stay available. Only these apps will be accessible during Focus.",
                    color = FocusMuted,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                FocusAppsSection(dismissSignal, showHeading = false)
            }
            else -> {
                if (singleStep) {
                    Text(
                        "Set the tone for this Focus session.",
                        color = FocusMuted,
                        fontFamily = Outfit,
                        fontSize = 13.sp
                    )
                    FocusSoundsSection(dismissSignal, heading = "Focus sound")
                } else {
                    FocusSoundsSection(dismissSignal, heading = "Focus sound")
                    ReadyStep(
                        minutes = minutes,
                        peopleLabel = groupTitles,
                        appCount = appCount,
                        sound = FocusSoundPrefs.sound(context),
                        editable = true,
                        onEdit = { target -> onStep(target) }
                    )
                }
            }
        }

        val primaryLabel = when {
            singleStep -> "Save"
            step < 3 -> "Continue →"
            else -> "Start Focus · $minutes min"
        }
        FocusContinueButton(
            label = primaryLabel,
            onClick = {
                when {
                    singleStep -> onFinish()
                    step < 3 -> onStep(step + 1)
                    else -> start()
                }
            }
        )

        when {
            !singleStep && step == 0 -> Text(
                "You can change these later.",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 0.dp)
            )
            singleStep || step in 1..2 -> TextButton(
                onClick = {
                    if (!singleStep) onFinish()
                    start()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Start now · $minutes min", color = FocusAccent, fontFamily = Outfit, fontWeight = FontWeight.SemiBold)
            }
            step >= 3 && !singleStep -> Text(
                "You can change these later.",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Keep people count referenced so collect stays warm for summary.
        @Suppress("UNUSED_EXPRESSION")
        people.size
        @Suppress("UNUSED_EXPRESSION")
        revision
    }
}

@Composable
private fun FocusGuideStepper(step: Int, onJump: (Int) -> Unit) {
    val titles = listOf("Duration", "People", "Apps", "Ready")
    val strip = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp)
            .clip(strip)
            .background(Color(0x66101828))
            .border(1.dp, Color.White.copy(alpha = 0.10f), strip)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Center
        ) {
            titles.forEachIndexed { index, title ->
                val done = index < step
                val active = index == step
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (index > 0) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .padding(end = 4.dp)
                                    .background(
                                        if (index - 1 < step) Color(0xFF5B8CFF).copy(alpha = 0.85f)
                                        else Color.White.copy(alpha = 0.22f)
                                    )
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Box(
                            Modifier
                                .size(34.dp)
                                .then(
                                    if (active) Modifier.shadow(
                                        10.dp,
                                        CircleShape,
                                        ambientColor = Color(0xAA5B8CFF),
                                        spotColor = Color(0xCC2F62F0)
                                    ) else Modifier
                                )
                                .clip(CircleShape)
                                .then(
                                    if (done || active) Modifier.background(
                                        Brush.horizontalGradient(listOf(Color(0xFF5B8CFF), Color(0xFF2F62F0)))
                                    )
                                    else Modifier.background(Color(0x88101828))
                                )
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = when {
                                        active -> Color(0xFF9BB6FF)
                                        done -> Color(0xFF5B8CFF).copy(alpha = 0.7f)
                                        else -> Color.White.copy(alpha = 0.28f)
                                    },
                                    shape = CircleShape
                                )
                                .clickable(enabled = done) { onJump(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            } else {
                                Text(
                                    "${index + 1}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = Outfit
                                )
                            }
                        }
                        if (index < 3) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .padding(start = 4.dp)
                                    .background(
                                        if (index < step) Color(0xFF5B8CFF).copy(alpha = 0.85f)
                                        else Color.White.copy(alpha = 0.22f)
                                    )
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        title,
                        modifier = Modifier.padding(top = 5.dp),
                        color = if (active) Color.White else Color(0xFFD5DCEB),
                        fontSize = 12.sp,
                        fontFamily = Outfit,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusContinueButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(18.dp, shape, ambientColor = Color(0xAA3D6FF0), spotColor = Color(0xCC5B8CFF))
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF6EA0FF), Color(0xFF3D6FF0), Color(0xFF2456E0))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Outfit
        )
    }
}

@Composable
private fun ColumnScope.DurationStep(
    minutes: Int,
    onDuration: (Int) -> Unit,
    onCustom: () -> Unit
) {
    FocusGuideRing(
        primary = "$minutes min",
        secondary = "Choose your time",
        compact = true
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(15, 30, 45, 60, 90).forEach { value ->
            DurationChip(
                top = "$value",
                bottom = "min",
                active = minutes == value,
                modifier = Modifier.weight(1f),
                onClick = { onDuration(value) }
            )
        }
        DurationChip(
            top = null,
            bottom = "Custom",
            active = minutes !in listOf(15, 30, 45, 60, 90),
            modifier = Modifier.weight(1f),
            onClick = onCustom,
            icon = true
        )
    }
}

@Composable
private fun DurationChip(
    top: String?,
    bottom: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .height(54.dp)
            .then(
                if (active) Modifier.shadow(
                    12.dp,
                    shape,
                    ambientColor = Color(0xAA5B8CFF),
                    spotColor = Color(0xCC3D6FF0)
                ) else Modifier
            )
            .clip(shape)
            .background(
                if (active) Brush.verticalGradient(listOf(Color(0xCC2A3F78), Color(0xBB152448)))
                else Brush.verticalGradient(listOf(Color(0x99101828), Color(0x88101828)))
            )
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) Color(0xFF7AA0FF) else Color.White.copy(alpha = 0.14f),
                shape = shape
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon) {
            Icon(Icons.Outlined.Tune, null, tint = Color.White, modifier = Modifier.size(17.dp))
        } else if (top != null) {
            Text(top, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Outfit)
        }
        Text(
            bottom,
            color = if (active) Color.White.copy(alpha = 0.92f) else FocusMuted,
            fontSize = 11.sp,
            fontFamily = Outfit
        )
    }
}

@Composable
private fun ReadyStep(
    minutes: Int,
    peopleLabel: String,
    appCount: Int,
    sound: FocusSound,
    editable: Boolean,
    onEdit: (Int) -> Unit
) {
    FocusSummaryStack(
        items = listOf(
            "Duration · $minutes min",
            "People · $peopleLabel",
            "Apps · $appCount selected",
            "Sound · ${if (sound == FocusSound.Off) "Off" else sound.title}"
        ),
        onTap = if (editable) onEdit else null
    )
    val endHint = remember(minutes) {
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        val hour = cal.get(Calendar.HOUR)
        val displayHour = if (hour == 0) 12 else hour
        val min = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        String.format(Locale.US, "until %d:%02d %s", displayHour, min, amPm)
    }
    val calm = when {
        minutes >= 60 -> "An hour of calm, $endHint."
        minutes >= 30 -> "A stretch of calm, $endHint."
        else -> "A quiet window, $endHint."
    }
    FocusGuideRing(
        primary = "%d:%02d".format(minutes, 0),
        secondary = calm
    )
}

@Composable
internal fun FocusGuideRing(
    primary: String,
    secondary: String,
    showPlay: Boolean = false,
    onPlay: (() -> Unit)? = null,
    onPrimaryClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val boxH = if (compact) 192.dp else 220.dp
    val glowSize = if (compact) 218.dp else 240.dp
    val ringSize = if (compact) 182.dp else 196.dp
    val primarySize = if (compact) 34.sp else 36.sp
    val secondarySize = if (compact) 13.sp else 14.sp
    val leafSize = if (compact) 22.dp else 24.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(boxH),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(glowSize)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0x55FFB84A),
                            Color(0x335B8CFF),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            Modifier
                .size(ringSize)
                .shadow(20.dp, CircleShape, ambientColor = Color(0x88E5A84A), spotColor = Color(0x665B8CFF)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension / 2f
                val thick = (if (compact) 11.dp else 12.dp).toPx()
                val thin = (if (compact) 5.dp else 6.dp).toPx()
                val inset = thick * 0.55f
                val arcBox = Offset(inset, inset)
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

                // Recessed center
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Color(0xE6182238), Color(0xCC0A121F), Color(0x99050910)),
                        center = Offset(cx - r * 0.1f, cy - r * 0.15f),
                        radius = r - inset
                    ),
                    radius = r - inset - 1.dp.toPx()
                )

                // Soft amber bloom (bottom-left bias)
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Color(0x55FFB84A), Color(0x22FFB84A), Color.Transparent),
                        center = Offset(cx - r * 0.25f, cy + r * 0.28f),
                        radius = r * 1.05f
                    ),
                    radius = r
                )

                // Quiet dark track
                drawCircle(
                    color = Color(0xFF252B3C),
                    radius = r - inset,
                    style = Stroke(width = thick * 0.85f)
                )

                // Thick warm gold tube — bottom / left (like the mock)
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0x66E8A050),
                            0.20f to Color(0xFFFFD27A),
                            0.38f to Color(0xFFFFB84A),
                            0.55f to Color(0xFFE89A3A),
                            0.72f to Color(0xFFFFC96A),
                            0.88f to Color(0x88FFD27A),
                            1.00f to Color(0x66E8A050)
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 55f,
                    sweepAngle = 175f,
                    useCenter = false,
                    topLeft = arcBox,
                    size = arcSize,
                    style = Stroke(width = thick, cap = StrokeCap.Round)
                )

                // Bright inner core on the gold arc
                drawArc(
                    color = Color(0xBBFFF3D0),
                    startAngle = 80f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(inset + thick * 0.22f, inset + thick * 0.22f),
                    size = Size(size.width - inset * 2 - thick * 0.44f, size.height - inset * 2 - thick * 0.44f),
                    style = Stroke(width = thick * 0.28f, cap = StrokeCap.Round)
                )

                // Thin luminous purple/white arc — top / right
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFB8C8FF), Color(0xFF8B80F8), Color(0xFFFFFFFF), Color(0xFF6EA0FF)),
                        start = Offset(cx + r * 0.2f, cy - r),
                        end = Offset(cx + r, cy + r * 0.1f)
                    ),
                    startAngle = -70f,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = arcBox,
                    size = arcSize,
                    style = Stroke(width = thin, cap = StrokeCap.Round)
                )

                // Soft purple glow on that thin arc
                drawArc(
                    color = Color(0x66A78BFA),
                    startAngle = -70f,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = Offset(inset - 2.dp.toPx(), inset - 2.dp.toPx()),
                    size = Size(size.width - inset * 2 + 4.dp.toPx(), size.height - inset * 2 + 4.dp.toPx()),
                    style = Stroke(width = thin + 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Inner lip shadow
                drawCircle(
                    color = Color(0x66000000),
                    radius = r - inset - thick * 0.5f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
                modifier = Modifier.padding(bottom = if (showPlay) 18.dp else 0.dp)
            ) {
                Icon(Icons.Outlined.Eco, null, tint = Color(0xFF7AA0FF), modifier = Modifier.size(leafSize))
                Text(
                    primary,
                    color = Color.White,
                    fontSize = primarySize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Outfit,
                    letterSpacing = (-0.5).sp,
                    modifier = if (onPrimaryClick != null) Modifier.clickable(onClick = onPrimaryClick) else Modifier
                )
                Text(
                    secondary,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = secondarySize,
                    fontFamily = Outfit,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showPlay && onPlay != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-12).dp)
                        .size(if (compact) 40.dp else 44.dp)
                        .shadow(10.dp, CircleShape, ambientColor = Color(0xAA3D6FF0), spotColor = Color(0xAA5B8CFF))
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF6EA0FF), Color(0xFF2F62F0))))
                        .border(1.dp, Color(0xFFB8CFFF), CircleShape)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Start Focus", tint = Color.White, modifier = Modifier.size(if (compact) 24.dp else 26.dp))
                }
            }
        }
    }
}

@Composable
private fun FocusSummaryStack(items: List<String>, onTap: ((Int) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, label ->
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(shape)
                    .background(Color(0xBB101828))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                    .then(if (onTap != null) Modifier.clickable { onTap(index) } else Modifier)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    Modifier.weight(1f),
                    color = Color.White,
                    fontFamily = Outfit,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(FocusAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun FocusGuideGroupGrid(dismissSignal: Int) {
    val context = LocalContext.current
    val repo = remember { FocusAllowedPeopleRepository.get(context) }
    val groups by repo.groups.collectAsState()
    val selected by repo.selectedGroups.collectAsState()
    val revision by repo.listsRevision.collectAsState()
    var manage by remember { mutableStateOf(false) }
    LaunchedEffect(dismissSignal) { manage = false }
    val members = remember(groups, revision) { groups.associate { it.id to repo.peopleForGroup(it.id) } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val rows = groups.chunked(2)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { group ->
                    FocusGuideGroupCard(
                        group = group,
                        members = members[group.id].orEmpty(),
                        selected = group.id in selected,
                        onToggle = { repo.selectGroup(group.id, group.id !in selected) },
                        onOpen = { manage = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        TextButton(onClick = { manage = true }, modifier = Modifier.align(Alignment.Start)) {
            Text("Manage groups →", color = FocusAccent, fontFamily = Outfit, fontWeight = FontWeight.SemiBold)
        }
    }
    if (manage) {
        FocusListsDialog(onDismiss = { manage = false }, dismissSignal = dismissSignal)
    }
}

@Composable
private fun FocusGuideGroupCard(
    group: FocusPeopleGroup,
    members: List<FocusPerson>,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .aspectRatio(1.15f)
            .clip(shape)
            .background(if (selected) FocusCardSelected else Color(0xCC121C30))
            .border(1.dp, if (selected) FocusAccent else Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.clickable(onClick = onOpen)) {
                if (members.isEmpty()) FocusGroupAvatar(group, members, 40.dp)
                else Box(Modifier.width((40 + (minOf(members.size, 3) - 1) * 16).dp).height(40.dp)) {
                    members.take(3).forEachIndexed { i, person ->
                        Box(Modifier.offset(x = (i * 16).dp)) { FocusContactAvatar(person, 40.dp) }
                    }
                }
            }
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) FocusAccent else Color.Transparent)
                    .border(1.5.dp, if (selected) FocusAccent else FocusMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Column {
            Text(group.title, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            Text(
                if (members.isEmpty()) "Tap to add" else "${members.size} contacts",
                color = FocusMuted,
                fontFamily = Outfit,
                fontSize = 12.sp
            )
        }
    }
}

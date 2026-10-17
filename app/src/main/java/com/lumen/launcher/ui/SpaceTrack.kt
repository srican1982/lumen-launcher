package com.lumen.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun SpaceTrack(selected: SpaceKind, onSelect: (SpaceKind) -> Unit, icon: (SpaceKind) -> ImageVector,
    meEnabled: Boolean = false, meSelected: Boolean = false, avatar: String = "", meProgress: (() -> Float)? = null, onMe: () -> Unit = {}) {
    val spaces = remember(meEnabled) { (if (meEnabled) listOf<SpaceKind?>(null) else emptyList()) + SpaceKind.entries.filter { it != SpaceKind.Private } }
    val showIntroLabels = true
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val activeIndex = (if (meProgress == null && meSelected && meEnabled) 0 else spaces.indexOf(selected)).coerceAtLeast(0)
    var trackWidth by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableStateOf<Float?>(null) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    val widths = spaces.map { space ->
        animateFloatAsState(if (meEnabled && space == null) 1.25f else if (showIntroLabels) 1f else if (space == selected) 2f else 1f,
            animationSpec = spring(dampingRatio = .88f, stiffness = 420f),
            label = "space-width-${space?.name ?: "me"}").value
    }
    val motionDuration = rememberMeMotionDuration()
    val selectionAlpha by animateFloatAsState(if (meSelected) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (motionDuration == 0) 0 else motionDuration,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        ), label = "me-space-highlight")
    val total = widths.sum()
    val latestSelect by rememberUpdatedState<(SpaceKind?) -> Unit>({ space -> if (space == null) onMe() else onSelect(space) })
    val latestWidths by rememberUpdatedState(widths)
    val latestWidth by rememberUpdatedState(trackWidth)
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp).height(if (meEnabled) 64.dp else 52.dp).clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.White.copy(.06f))))
        .border(.8.dp, Brush.verticalGradient(listOf(Color.White.copy(.42f), Color.White.copy(.12f))), shape)
        .padding(3.dp)) {
        val start = widths.take(activeIndex).sum() / total * maxWidth.value
        val pillWidth = widths[activeIndex] / total * maxWidth.value
        val pillX = dragX?.let { (it / density.density - pillWidth / 2).coerceIn(0f, (maxWidth.value - pillWidth).coerceAtLeast(0f)) } ?: start
        val animatedPillX by animateFloatAsState(pillX,
            animationSpec = if (dragX != null || motionDuration == 0) androidx.compose.animation.core.snap()
                else spring(dampingRatio = .9f, stiffness = 450f), label = "space-pill-position")
        Box(Modifier.graphicsLayer { alpha = meProgress?.let { 1f - it() } ?: selectionAlpha }
            .offset {
                val p = meProgress?.invoke() ?: 0f
                val startPx = if (p > 0f) pillX.dp.toPx() else animatedPillX.dp.toPx()
                IntOffset((startPx * (1f - p)).roundToInt(), 0)
            }
            .width(pillWidth.dp).fillMaxHeight()
            .shadow(8.dp, RoundedCornerShape(23.dp), spotColor = Lumen.BrandGold.copy(.35f))
            .clip(RoundedCornerShape(23.dp)).background(Lumen.AccentFill)
            .border(.7.dp, Lumen.BrandGold.copy(.65f), RoundedCornerShape(23.dp)))
        Row(Modifier.fillMaxSize().onSizeChanged { trackWidth = it.width.toFloat() }
            .pointerInput(spaces) {
                fun indexAt(x: Float): Int {
                    val weights = latestWidths
                    val fraction = (x / latestWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    var edge = 0f
                    weights.forEachIndexed { index, weight ->
                        edge += weight / weights.sum()
                        if (fraction <= edge) return index
                    }
                    return spaces.lastIndex
                }
                detectHorizontalDragGestures(
                    onDragStart = { point -> dragX = point.x; dragIndex = indexAt(point.x) },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val nextX = ((dragX ?: 0f) + amount).coerceIn(0f, latestWidth)
                        dragX = nextX
                        val next = indexAt(nextX)
                        if (next != dragIndex) {
                            dragIndex = next
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        val next = dragIndex
                        dragX = null; dragIndex = null
                        if (next != null) latestSelect(spaces[next])
                    },
                    onDragCancel = { dragX = null; dragIndex = null }
                )
            }, verticalAlignment = Alignment.CenterVertically) {
            spaces.forEachIndexed { index, space ->
                val active = if (space == null) meSelected else !meSelected && index == activeIndex
                val tint = when {
                    meSelected && space != null -> Color.White
                    active -> Lumen.OnBrandGold
                    else -> Color.White.copy(.88f)
                }
                Row(Modifier.weight(widths[index]).fillMaxHeight().clip(RoundedCornerShape(23.dp))
                    .semantics { contentDescription = space?.title ?: "Me"; this.selected = active }
                    .clickable {
                        if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (space == null) onMe() else onSelect(space)
                    }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (space == null) {
                        val ring = if (meSelected) Lumen.BrandGold else Color.White.copy(.40f)
                        Box(
                            Modifier
                                .sizeIn(maxWidth = 56.dp, maxHeight = 56.dp).aspectRatio(1f)
                                .then(
                                    if (meSelected) Modifier.shadow(12.dp, CircleShape, spotColor = Lumen.BrandGold.copy(.55f), ambientColor = Lumen.BrandGold.copy(.28f))
                                    else Modifier
                                )
                                .border(if (meSelected) 2.2.dp else 1.dp, ring, CircleShape)
                                .padding(2.5.dp)
                                .border(.6.dp, Color.White.copy(if (meSelected) .35f else .18f), CircleShape)
                                .clip(CircleShape)
                                .background(Color.White.copy(.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatar.isNotBlank()) AsyncImage(
                                java.io.File(avatar), "Me",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                            else Icon(
                                Icons.Outlined.Person, null,
                                tint = if (meSelected) Lumen.BrandGold else Color.White.copy(.92f),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon(space), null, tint = tint, modifier = Modifier.size(if (meEnabled) 19.dp else 18.dp))
                            Text(space.title, color = tint, fontFamily = Outfit,
                                fontSize = if (meEnabled) 9.sp else 11.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

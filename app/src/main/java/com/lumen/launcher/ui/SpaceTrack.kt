package com.lumen.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
internal fun SpaceTrack(selected: SpaceKind, onSelect: (SpaceKind) -> Unit, icon: (SpaceKind) -> ImageVector) {
    val spaces = remember { SpaceKind.entries.filter { it != SpaceKind.Private } }
    val context = LocalContext.current
    val showIntroLabels = remember(context) {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        System.currentTimeMillis() - installed < java.util.concurrent.TimeUnit.DAYS.toMillis(3)
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val activeIndex = spaces.indexOf(selected).coerceAtLeast(0)
    var trackWidth by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableStateOf<Float?>(null) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    val widths = spaces.map { space ->
        animateFloatAsState(if (showIntroLabels) 1f else if (space == selected) 2f else 1f,
            animationSpec = spring(dampingRatio = .88f, stiffness = 420f),
            label = "space-width-${space.name}").value
    }
    val total = widths.sum()
    val latestSelect by rememberUpdatedState(onSelect)
    val latestWidths by rememberUpdatedState(widths)
    val latestWidth by rememberUpdatedState(trackWidth)
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp).clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.White.copy(.06f))))
        .border(.8.dp, Brush.verticalGradient(listOf(Color.White.copy(.42f), Color.White.copy(.12f))), shape)
        .padding(4.dp)) {
        val start = widths.take(activeIndex).sum() / total * maxWidth.value
        val pillWidth = widths[activeIndex] / total * maxWidth.value
        val pillX = dragX?.let { (it / density.density - pillWidth / 2).coerceIn(0f, (maxWidth.value - pillWidth).coerceAtLeast(0f)) } ?: start
        val animatedPillX by animateFloatAsState(pillX,
            animationSpec = if (dragX != null) androidx.compose.animation.core.snap()
                else spring(dampingRatio = .9f, stiffness = 450f), label = "space-pill-position")
        Box(Modifier.offset(x = animatedPillX.dp).width(pillWidth.dp).fillMaxHeight()
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
                val active = index == activeIndex
                val tint = if (active) Lumen.OnBrandGold else Color.White.copy(.88f)
                Row(Modifier.weight(widths[index]).fillMaxHeight().clip(RoundedCornerShape(23.dp))
                    .semantics { contentDescription = space.title; this.selected = active }
                    .clickable {
                        if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(space)
                    }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon(space), null, tint = tint, modifier = Modifier.size(18.dp))
                    if (active || showIntroLabels) {
                        Spacer(Modifier.width(5.dp))
                        Text(space.title, color = tint, fontFamily = Outfit,
                            fontSize = if (showIntroLabels) 11.sp else 13.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

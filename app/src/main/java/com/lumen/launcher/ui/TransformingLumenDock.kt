package com.lumen.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Apps
import com.lumen.launcher.data.IconCache
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.GestureAction
import com.lumen.launcher.data.TouchpadHaptics
import com.lumen.launcher.vm.LauncherUiState
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal class DockGestureRegion {
    var bounds: Rect? = null
    var active by mutableStateOf(false)
}
internal class DockPadState {
    val expanded = mutableStateOf(false)
    val selected = mutableStateOf<TouchpadSwipe?>(null)
}
internal val LocalDockGestureRegion = staticCompositionLocalOf { DockGestureRegion() }

private val DockGold = Color(0xFFE7C27A)
private val DockGoldBright = Color(0xFFFFE7B0)
private val DockGoldHot = Color(0xFFFFD991)

/**
 * Gesture dock: touch the ring → slide toward a shortcut → release.
 * Expands inline above the dock — no modal panel or home blur.
 */
@Composable
internal fun TransformingLumenDock(
    state: LauncherUiState,
    icons: IconCache,
    padState: DockPadState,
    enabled: Boolean,
    onSearch: () -> Unit,
    onVoice: () -> Unit = {},
    onGesture: (String) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
    onRingBounds: (Rect) -> Unit = {},
    dockHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    normalDock: @Composable (ring: @Composable () -> Unit) -> Unit
) {
    val region = LocalDockGestureRegion.current
    var expanded by padState.expanded
    var selected by padState.selected
    var touching by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(220), label = "dock-pad")
    val touchBoost by animateFloatAsState(if (touching) 1f else 0f, tween(120), label = "dock-touch")
    val haptics = LocalHapticFeedback.current
    val hapticEnabled = state.touchpadHaptics != TouchpadHaptics.OFF
    val expandExtra = 168.dp
    fun gesture(direction: TouchpadSwipe) = when (direction) {
        TouchpadSwipe.Up -> "swipeUp"
        TouchpadSwipe.Down -> "swipeDown"
        TouchpadSwipe.Left -> "swipeLeft"
        TouchpadSwipe.Right -> "swipeRight"
    }
    fun action(direction: TouchpadSwipe) = GestureAction.resolveDefault(when (direction) {
        TouchpadSwipe.Up -> state.swipeUpAction
        TouchpadSwipe.Down -> state.swipeDownAction
        TouchpadSwipe.Left -> state.swipeLeftAction
        TouchpadSwipe.Right -> state.swipeRightAction
    }, state.apps)
    fun collapse() {
        expanded = false
        selected = null
        finger = Offset.Zero
        onActiveChanged(false)
    }
    fun runDirection(direction: TouchpadSwipe) {
        onGesture(gesture(direction))
        collapse()
    }
    SideEffect { region.active = expanded }
    DisposableEffect(Unit) { onDispose { region.bounds = null; region.active = false; onActiveChanged(false) } }
    LaunchedEffect(enabled) {
        if (!enabled) {
            touching = false
            collapse()
        }
    }
    Box(
        modifier.height(dockHeight + expandExtra * progress),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Directional shortcuts grow into the space above the dock (home stays clear).
        if (progress > 0.01f) {
            InlineDockShortcuts(
                state = state,
                icons = icons,
                selected = selected,
                progress = progress,
                // New users often tap the chips — honor that as well as slide-to-select.
                onPick = { direction -> runDirection(direction) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(expandExtra)
                    .align(Alignment.TopCenter)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(dockHeight)
                .graphicsLayer { alpha = 1f - progress * 0.28f }
        ) {
            normalDock {
                Box(
                    Modifier
                        .requiredSize(92.dp)
                        .onGloballyPositioned {
                            region.bounds = it.boundsInRoot()
                            onRingBounds(it.boundsInWindow())
                        }
                        .semantics {
                            contentDescription = "Lumen ring. Slide to a shortcut, or tap a shortcut."
                            onClick("Open Lumen search") { onSearch(); true }
                            customActions = TouchpadSwipe.entries.map { direction ->
                                CustomAccessibilityAction(GestureAction.label(action(direction), state.apps)) {
                                    runDirection(direction); true
                                }
                            }
                        }
                        .touchpadGestures(
                            enabled = enabled,
                            holdToSelect = true,
                            onPress = { down, _ ->
                                touching = down
                                if (!down) {
                                    // Keep shortcuts open after lift so a tap on a chip still works.
                                    finger = Offset.Zero
                                    selected = null
                                }
                            },
                            onDrag = { finger = it },
                            onLongPress = {
                                if (!expanded) {
                                    expanded = true
                                    onActiveChanged(true)
                                    if (hapticEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                            onArmed = { direction ->
                                if (direction != selected && direction != null && hapticEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                selected = direction
                            },
                            onTap = {
                                if (expanded) collapse()
                                onSearch()
                            },
                            // Tap = search; double-tap = Voice.
                            onDoubleTap = {
                                if (expanded) collapse()
                                onVoice()
                            },
                            onTripleTap = {},
                            onSwipe = { direction -> runDirection(direction) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Soft glow that leans toward the finger while selecting.
                    if (progress > 0.05f || touchBoost > 0.05f) {
                        val glow = (progress * 0.55f + touchBoost * 0.35f).coerceIn(0f, 0.85f)
                        val lean = Offset(
                            (finger.x * 0.18f).coerceIn(-18f, 18f),
                            (finger.y * 0.18f).coerceIn(-18f, 18f)
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            val c = center() + lean
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(DockGoldHot.copy(glow * 0.55f), DockGold.copy(glow * 0.12f), Color.Transparent),
                                    c,
                                    size.minDimension * 0.55f
                                ),
                                size.minDimension * 0.55f,
                                c
                            )
                        }
                    }
                    // Slight press-in when held — physical home-button feel.
                    val press = 1f - touchBoost * 0.045f
                    SearchOrbMark(
                        Modifier
                            .size(76.dp)
                            .graphicsLayer {
                                scaleX = press
                                scaleY = press
                                shadowElevation = (10f + touchBoost * 2f).dp.toPx()
                                shape = androidx.compose.foundation.shape.CircleShape
                                clip = false
                            },
                        prominent = true
                    )
                    RingDirectionArrows(
                        emphasis = maxOf(touchBoost, progress * 0.85f),
                        selected = selected,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** Tiny champagne arrows just outside the crystal — discoverability without clutter. */
@Composable
internal fun RingDirectionArrows(
    emphasis: Float,
    selected: TouchpadSwipe?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val orbR = size.minDimension * 0.40f
        val base = orbR + 6.dp.toPx()
        val outward = base + emphasis * 3.5.dp.toPx()
        val arrowLen = 6.2.dp.toPx()
        val arrowHalf = 3.8.dp.toPx()
        TouchpadSwipe.entries.forEach { direction ->
            val radians = when (direction) {
                TouchpadSwipe.Up -> -Math.PI / 2
                TouchpadSwipe.Right -> 0.0
                TouchpadSwipe.Down -> Math.PI / 2
                TouchpadSwipe.Left -> Math.PI
            }
            val ux = cos(radians).toFloat()
            val uy = sin(radians).toFloat()
            val tip = c + Offset(ux * (outward + arrowLen * 0.15f), uy * (outward + arrowLen * 0.15f))
            val basePoint = c + Offset(ux * outward, uy * outward)
            val px = -uy
            val py = ux
            val left = basePoint + Offset(px * arrowHalf, py * arrowHalf) - Offset(ux * arrowLen * 0.35f, uy * arrowLen * 0.35f)
            val right = basePoint + Offset(-px * arrowHalf, -py * arrowHalf) - Offset(ux * arrowLen * 0.35f, uy * arrowLen * 0.35f)
            // Always readable at rest so swipe directions are obvious; brighter when active.
            val alpha = when {
                selected == direction -> 0.98f
                emphasis > 0.05f -> 0.62f + emphasis * 0.28f
                else -> 0.52f
            }
            val color = if (selected == direction) DockGoldHot.copy(alpha) else DockGoldBright.copy(alpha)
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(left.x, left.y)
                lineTo(right.x, right.y)
                close()
            }
            drawPath(path, color)
            if (selected == direction) {
                drawCircle(DockGoldHot.copy(0.22f), 7.dp.toPx(), tip)
            }
        }
    }
}

@Composable
private fun InlineDockShortcuts(
    state: LauncherUiState,
    icons: IconCache,
    selected: TouchpadSwipe?,
    progress: Float,
    onPick: (TouchpadSwipe) -> Unit,
    modifier: Modifier = Modifier
) {
    fun action(direction: TouchpadSwipe) = GestureAction.resolveDefault(when (direction) {
        TouchpadSwipe.Up -> state.swipeUpAction
        TouchpadSwipe.Down -> state.swipeDownAction
        TouchpadSwipe.Left -> state.swipeLeftAction
        TouchpadSwipe.Right -> state.swipeRightAction
    }, state.apps)
    Box(modifier.graphicsLayer { alpha = progress }) {
        Canvas(Modifier.fillMaxSize()) {
            // Spokes originate from where the ring sits (just below this layer).
            val center = Offset(size.width / 2f, size.height + 8.dp.toPx())
            val ringR = 36.dp.toPx()
            val ends = listOf(
                TouchpadSwipe.Up to Offset(center.x, 36.dp.toPx()),
                TouchpadSwipe.Down to Offset(center.x, size.height - 36.dp.toPx()),
                TouchpadSwipe.Left to Offset(36.dp.toPx(), size.height * 0.55f),
                TouchpadSwipe.Right to Offset(size.width - 36.dp.toPx(), size.height * 0.55f)
            )
            ends.forEach { (direction, end) ->
                val vector = end - center
                val distance = hypot(vector.x.toDouble(), vector.y.toDouble()).toFloat()
                if (distance > ringR) {
                    val start = center + vector / distance * ringR
                    val active = selected == direction
                    drawLine(
                        DockGold.copy(if (active) .88f else .32f * progress),
                        start,
                        end,
                        (if (active) 1.4.dp else .55.dp).toPx(),
                        StrokeCap.Round
                    )
                    drawCircle(DockGold.copy(if (active) .35f else .14f), 5.dp.toPx(), end)
                    drawCircle(DockGoldHot.copy(if (active) .9f else .45f), 2.dp.toPx(), end)
                }
            }
        }
        TouchpadSwipe.entries.forEach { direction ->
            val alignment = when (direction) {
                TouchpadSwipe.Up -> Alignment.TopCenter
                TouchpadSwipe.Down -> Alignment.BottomCenter
                TouchpadSwipe.Left -> Alignment.CenterStart
                TouchpadSwipe.Right -> Alignment.CenterEnd
            }
            val pad = when (direction) {
                TouchpadSwipe.Up -> PaddingValues(top = 2.dp)
                TouchpadSwipe.Down -> PaddingValues(bottom = 4.dp)
                TouchpadSwipe.Left -> PaddingValues(start = 4.dp, bottom = 20.dp)
                TouchpadSwipe.Right -> PaddingValues(end = 4.dp, bottom = 20.dp)
            }
            DockShortcutChip(
                state = state,
                icons = icons,
                spec = action(direction),
                chosen = selected == direction,
                onClick = { onPick(direction) },
                modifier = Modifier.align(alignment).padding(pad)
            )
        }
    }
}

@Composable
private fun DockShortcutChip(
    state: LauncherUiState,
    icons: IconCache,
    spec: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = state.apps.find { GestureAction.isApp(spec) && it.key == GestureAction.appKey(spec) }
    val isVault = spec == GestureAction.PRIVATE
    val scale by animateFloatAsState(if (chosen) 1.06f else 1f, tween(110), label = "dock-chip")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    Column(
        modifier
            .width(70.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(if (chosen) .18f else .07f),
                        Color(0xFFE7C27A).copy(if (chosen) .14f else .035f),
                        Color.White.copy(if (chosen) .05f else .02f)
                    )
                ),
                shape
            )
            .border(
                if (chosen) .7.dp else .4.dp,
                if (chosen) DockGoldHot.copy(.9f) else Color.White.copy(.16f),
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 5.dp, vertical = 5.dp)
            .semantics { contentDescription = if (isVault) "Vault, hidden apps" else GestureAction.label(spec, state.apps) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (app != null) AppIcon(app.packageName, app.activityName, 28.dp, icons)
        else Icon(
            when (spec) {
                GestureAction.DEFAULT_CAMERA -> Icons.Outlined.CameraAlt
                GestureAction.DEFAULT_MAPS -> Icons.Outlined.Map
                GestureAction.DEFAULT_MUSIC -> Icons.Outlined.MusicNote
                GestureAction.PRIVATE, GestureAction.LOCK -> Icons.Outlined.Lock
                GestureAction.SEARCH -> Icons.Outlined.Search
                GestureAction.DRAWER -> Icons.Outlined.Apps
                else -> Icons.Outlined.Add
            },
            null,
            tint = DockGoldBright,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (isVault) "Vault" else GestureAction.label(spec, state.apps),
            color = if (chosen) DockGoldBright else Color.White.copy(.92f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (isVault) {
            Text(
                "Hidden apps",
                color = Color.White.copy(.48f),
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private fun DrawScope.center() = Offset(size.width / 2f, size.height / 2f)

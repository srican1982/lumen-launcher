package com.lumen.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun Modifier.homeGestures(
    enabled: Boolean,
    topZonePx: Float,
    bottomDeadPx: Float,
    edgeDeadPx: Float,
    drawerZonePx: Float,
    onSwipeUp: () -> Unit,
    onNotifications: () -> Unit,
    onQuickSettings: () -> Unit,
    onPinch: () -> Unit
): Modifier {
    val swipeSlop = with(LocalDensity.current) { 40.dp.toPx() }
    val currentUp by rememberUpdatedState(onSwipeUp)
    val currentNotifications by rememberUpdatedState(onNotifications)
    val currentQuickSettings by rememberUpdatedState(onQuickSettings)
    val currentPinch by rememberUpdatedState(onPinch)
    val enabledState by rememberUpdatedState(enabled)

    return this
        .pointerInput(swipeSlop, topZonePx, bottomDeadPx, edgeDeadPx, drawerZonePx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!enabledState) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                    }
                    return@awaitEachGesture
                }
                val x = down.position.x
                val y = down.position.y
                val inNav = y > size.height - bottomDeadPx
                val inEdge = x < edgeDeadPx || x > size.width - edgeDeadPx
                if (inNav || inEdge) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                    }
                    return@awaitEachGesture
                }
                val fromTop = y <= topZonePx
                val fromDock = y >= size.height - drawerZonePx
                val startX = x
                var totalX = 0f
                var totalY = 0f
                var committed = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    totalX = change.position.x - down.position.x
                    totalY = change.position.y - down.position.y
                    val vertical = abs(totalY) > swipeSlop && abs(totalY) > abs(totalX) * 1.35f
                    if (fromTop && totalY > swipeSlop && vertical) {
                        change.consume()
                        committed = true
                    } else if (fromDock && totalY < -swipeSlop && vertical) {
                        change.consume()
                        committed = true
                    } else if (committed) {
                        change.consume()
                    }
                    if (!change.pressed) break
                }
                if (!committed) return@awaitEachGesture
                when {
                    fromTop && totalY > swipeSlop && startX < size.width * 0.5f -> currentNotifications()
                    fromTop && totalY > swipeSlop -> currentQuickSettings()
                    fromDock && totalY < -swipeSlop -> currentUp()
                }
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!enabledState) {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    return@awaitEachGesture
                }
                if (down.position.y > size.height - bottomDeadPx) {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    return@awaitEachGesture
                }
                var zoom = 1f
                var twoFingers = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        twoFingers = true
                        val previous = (pressed[0].previousPosition - pressed[1].previousPosition).getDistance()
                        val current = (pressed[0].position - pressed[1].position).getDistance()
                        if (previous > 0f) zoom *= current / previous
                        event.changes.fastForEach { it.consume() }
                    }
                    if (pressed.isEmpty()) break
                }
                if (twoFingers && zoom < 0.88f) currentPinch()
            }
        }
}

@Composable
fun Modifier.homeEmptyTaps(
    enabled: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit,
    onLongPress: () -> Unit = {}
): Modifier {
    val slop = with(LocalDensity.current) { 16.dp.toPx() }
    val currentTap by rememberUpdatedState(onTap)
    val currentDouble by rememberUpdatedState(onDoubleTap)
    val currentTriple by rememberUpdatedState(onTripleTap)
    val currentLong by rememberUpdatedState(onLongPress)
    val enabledState by rememberUpdatedState(enabled)
    return pointerInput(slop) {
        awaitPointerEventScope {
            while (true) {
                val first = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Final)
                if (!enabledState || first.isConsumed) {
                    waitForUp(first.id, slop)
                    continue
                }
                val held = withTimeoutOrNull(500L) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == first.id }
                            ?: return@withTimeoutOrNull "lost"
                        if (change.isConsumed || first.isConsumed) return@withTimeoutOrNull "consumed"
                        if (!change.pressed) return@withTimeoutOrNull "up"
                        if (change.positionChange().getDistance() > slop) return@withTimeoutOrNull "move"
                    }
                }
                if (held == null) {
                    if (!first.isConsumed) currentLong()
                    waitForUp(first.id, slop)
                    continue
                }
                if (held != "up") continue
                var count = 1
                while (count < 3) {
                    val extra = withTimeoutOrNull(280) {
                        awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Final)
                    } ?: break
                    if (waitForUp(extra.id, slop)) break
                    count++
                }
                when (count) {
                    1 -> currentTap()
                    2 -> currentDouble()
                    else -> currentTriple()
                }
            }
        }
    }
}

@Composable
fun Modifier.emptySpaceLongPress(
    enabled: Boolean,
    onLongPress: () -> Unit
): Modifier {
    val enabledState by rememberUpdatedState(enabled)
    val currentLong by rememberUpdatedState(onLongPress)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Final)
            if (!enabledState || down.isConsumed) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            val held = withTimeoutOrNull(800L) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull "lost"
                    if (change.isConsumed || down.isConsumed) return@withTimeoutOrNull "consumed"
                    if (!change.pressed) return@withTimeoutOrNull "up"
                    if (change.positionChange().getDistance() > slop) return@withTimeoutOrNull "move"
                }
            }
            if (held == null && enabledState && !down.isConsumed) currentLong()
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUp(
    id: androidx.compose.ui.input.pointer.PointerId,
    slop: Float
): Boolean {
    var moved = false
    var start: Offset? = null
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: break
        if (start == null) start = change.previousPosition
        val dist = (change.position - (start ?: change.previousPosition)).getDistance()
        change.consume()
        if (dist > slop) moved = true
        if (!change.pressed) break
    }
    return moved
}

@Composable
fun Modifier.topShadeGestures(
    onNotifications: () -> Unit,
    onQuickSettings: () -> Unit
): Modifier {
    val swipeSlop = with(LocalDensity.current) { 28.dp.toPx() }
    val currentNotifications by rememberUpdatedState(onNotifications)
    val currentQuickSettings by rememberUpdatedState(onQuickSettings)
    return pointerInput(swipeSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            var totalY = 0f
            var committed = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                totalY += change.positionChange().y
                if (totalY > swipeSlop) {
                    change.consume()
                    committed = true
                }
                if (!change.pressed) break
            }
            if (!committed) return@awaitEachGesture
            if (startX < size.width * 0.5f) currentNotifications() else currentQuickSettings()
        }
    }
}

enum class TouchpadSwipe { Up, Down, Left, Right }

private const val TouchpadHoldMs = 600L
private const val TouchpadTapWindowMs = 380L

@Composable
fun Modifier.touchpadGestures(
    enabled: Boolean,
    onPress: (Boolean, Offset) -> Unit = { _, _ -> },
    onHoldProgress: (Float) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onArmed: (TouchpadSwipe?) -> Unit = {},
    onTapPulse: (Int, Offset) -> Unit = { _, _ -> },
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipe: (TouchpadSwipe) -> Unit
): Modifier {
    val trackSlop = with(LocalDensity.current) { 14.dp.toPx() }
    val activateSlop = with(LocalDensity.current) { 28.dp.toPx() }
    val currentTap by rememberUpdatedState(onTap)
    val currentDouble by rememberUpdatedState(onDoubleTap)
    val currentTriple by rememberUpdatedState(onTripleTap)
    val currentLong by rememberUpdatedState(onLongPress)
    val currentSwipe by rememberUpdatedState(onSwipe)
    val currentPress by rememberUpdatedState(onPress)
    val currentHold by rememberUpdatedState(onHoldProgress)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentArmed by rememberUpdatedState(onArmed)
    val currentPulse by rememberUpdatedState(onTapPulse)
    val enabledState by rememberUpdatedState(enabled)
    return pointerInput(trackSlop, activateSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            if (!enabledState) {
                waitForUp(down.id, trackSlop)
                return@awaitEachGesture
            }
            currentPress(true, down.position)
            currentDrag(Offset.Zero)
            currentHold(0f)
            currentArmed(null)
            val start = down.position
            val startNs = System.nanoTime()
            var total = Offset.Zero
            var longFired = false
            var swiping = false
            var armed: TouchpadSwipe? = null

            fun holdOf(): Float {
                val ms = (System.nanoTime() - startNs) / 1_000_000f
                return (ms / TouchpadHoldMs).coerceIn(0f, 1f)
            }

            fun direction(delta: Offset = total): TouchpadSwipe? {
                val dist = hypot(delta.x, delta.y)
                if (dist < activateSlop) return null
                val horizontal = abs(delta.x) > abs(delta.y)
                return when {
                    horizontal && delta.x > 0f -> TouchpadSwipe.Right
                    horizontal && delta.x < 0f -> TouchpadSwipe.Left
                    !horizontal && delta.y < 0f -> TouchpadSwipe.Up
                    !horizontal && delta.y > 0f -> TouchpadSwipe.Down
                    else -> null
                }
            }

            while (true) {
                val event = if (!swiping && !longFired) {
                    withTimeoutOrNull(16L) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    val p = holdOf()
                    currentHold(p)
                    if (p >= 1f && !longFired) {
                        longFired = true
                        currentHold(1f)
                        currentLong()
                    }
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                total = change.position - start
                change.consume()
                val dist = hypot(total.x, total.y)
                if (!longFired && dist > trackSlop) {
                    swiping = true
                    currentHold(0f)
                }
                if (swiping) {
                    currentDrag(total)
                    val next = direction()
                    if (next != armed) {
                        armed = next
                        currentArmed(next)
                    }
                } else if (!longFired) {
                    currentHold(holdOf())
                }
                if (change.pressed) continue

                currentPress(false, change.position)
                if (longFired) {
                    currentDrag(Offset.Zero)
                    currentArmed(null)
                    currentHold(0f)
                    return@awaitEachGesture
                }
                if (swiping) {
                    val commit = direction()
                    currentDrag(Offset.Zero)
                    currentArmed(null)
                    currentHold(0f)
                    if (commit != null) currentSwipe(commit)
                    return@awaitEachGesture
                }
                currentHold(0f)
                currentDrag(Offset.Zero)
                break
            }

            if (longFired || swiping) return@awaitEachGesture
            var count = 1
            currentPulse(1, down.position)
            while (count < 3) {
                val extra = withTimeoutOrNull(TouchpadTapWindowMs) {
                    awaitFirstDown(requireUnconsumed = false)
                } ?: break
                extra.consume()
                currentPress(true, extra.position)
                val moved = waitForUp(extra.id, trackSlop)
                currentPress(false, extra.position)
                if (moved) break
                count++
                currentPulse(count, extra.position)
            }
            when (count) {
                1 -> currentTap()
                2 -> currentDouble()
                else -> currentTriple()
            }
        }
    }
}

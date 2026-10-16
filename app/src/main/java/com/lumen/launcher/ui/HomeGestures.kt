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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
    onPinch: () -> Unit,
    isReservedTouch: (Offset) -> Boolean = { false }
): Modifier {
    val swipeSlop = with(LocalDensity.current) { 40.dp.toPx() }
    val reservedTouch by rememberUpdatedState(isReservedTouch)
    val currentUp by rememberUpdatedState(onSwipeUp)
    val currentNotifications by rememberUpdatedState(onNotifications)
    val currentQuickSettings by rememberUpdatedState(onQuickSettings)
    val currentPinch by rememberUpdatedState(onPinch)
    val enabledState by rememberUpdatedState(enabled)

    return this
        .pointerInput(swipeSlop, topZonePx, bottomDeadPx, edgeDeadPx, drawerZonePx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!enabledState || reservedTouch(down.position)) {
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
                if (!enabledState || reservedTouch(down.position)) {
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
                        if ((change.position - first.position).getDistance() > slop) return@withTimeoutOrNull "move"
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

/**
 * Cardinal pick from a slide delta (Compose Y grows downward).
 *
 * Left/Right stay wide on the upper side so Camera doesn't steal them. Down uses a
 * wider lower cone for swipe gestures; the dock pad uses [touchpadDockSelection]
 * instead so Vault never needs travel into the system nav bar.
 */
internal fun touchpadDirection(delta: Offset, threshold: Float, downThreshold: Float = threshold): TouchpadSwipe? {
    val dist = hypot(delta.x, delta.y)
    if (dist <= 0f) return null
    val angle = atan2(delta.y, delta.x) * (180f / PI.toFloat())
    if (delta.y >= downThreshold && delta.y >= abs(delta.x) * 0.3f) {
        return TouchpadSwipe.Down
    }
    val downward = angle in 20f..160f
    val required = if (downward) downThreshold else threshold
    if (dist < required) return null
    return when {
        angle >= -55f && angle < 20f -> TouchpadSwipe.Right
        angle >= 20f && angle <= 160f -> TouchpadSwipe.Down
        // Keep Up relatively narrow so left/right-up diagonals stay Maps/Spotify.
        angle >= -125f && angle < -55f -> TouchpadSwipe.Up
        else -> TouchpadSwipe.Left
    }
}

/** Keep the armed card until the finger clearly enters another sector. */
internal fun touchpadDirectionSticky(
    delta: Offset,
    threshold: Float,
    downThreshold: Float,
    armed: TouchpadSwipe?,
    stickBoost: Float = 1.25f
): TouchpadSwipe? {
    val next = touchpadDirection(delta, threshold, downThreshold) ?: return null
    if (armed == null || next == armed) return next
    if (next == TouchpadSwipe.Down) return next
    return touchpadDirection(delta, threshold * stickBoost, downThreshold * stickBoost) ?: armed
}

/**
 * Map a drag from the bottom ring into pad-centered space. The expanded pad's visual
 * center sits well above the ring, so Vault (bottom card) is "down from center" even
 * when the finger is still slightly above the press point.
 */
internal fun touchpadDockDelta(dragFromRing: Offset, padCenterLift: Float): Offset =
    Offset(dragFromRing.x, dragFromRing.y + padCenterLift)

/**
 * Dock hold-to-select using a vector from the pad visual center. Crossing back below
 * center selects Vault immediately — the whole bottom card, not only its lower edge.
 */
internal fun touchpadDockSelection(
    deltaFromPadCenter: Offset,
    threshold: Float,
    armed: TouchpadSwipe? = null,
    stickBoost: Float = 1.1f
): TouchpadSwipe {
    val next = touchpadDirection(deltaFromPadCenter, threshold, threshold)
        ?: return armed ?: TouchpadSwipe.Down
    if (armed == null || next == armed) return next
    // After visiting Camera, entering the lower half must pick Vault without stickiness.
    if (next == TouchpadSwipe.Down) return next
    return touchpadDirection(deltaFromPadCenter, threshold * stickBoost, threshold * stickBoost) ?: armed
}

private const val TouchpadHoldMs = 600L
private const val TouchpadTapWindowMs = 380L

@Composable
fun Modifier.touchpadGestures(
    enabled: Boolean,
    holdToSelect: Boolean = false,
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
    // Gesture dock: short arm distance from the ring. Vault uses a softer down threshold.
    val activateSlop = with(LocalDensity.current) { if (holdToSelect) 14.dp.toPx() else 28.dp.toPx() }
    val dockDownSlop = with(LocalDensity.current) { if (holdToSelect) 8.dp.toPx() else activateSlop }
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
    return pointerInput(trackSlop, activateSlop, dockDownSlop, holdToSelect) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            if (!enabledState) {
                waitForUp(down.id, trackSlop)
                return@awaitEachGesture
            }
            try {
            currentPress(true, down.position)
            currentDrag(Offset.Zero)
            currentHold(0f)
            currentArmed(null)
            val origin = down.position
            val startNs = System.nanoTime()
            var total = Offset.Zero
            // selectMode: pad is live (drag past slop OR optional hold preview).
            var selectMode = false
            var swiping = false
            var armed: TouchpadSwipe? = null

            fun holdOf(): Float {
                val ms = (System.nanoTime() - startNs) / 1_000_000f
                return (ms / TouchpadHoldMs).coerceIn(0f, 1f)
            }

            fun direction(delta: Offset = total, sticky: Boolean = holdToSelect): TouchpadSwipe? {
                if (holdToSelect && selectMode) {
                    // Ring is the radial center — no modal pad-center lift.
                    return if (sticky) {
                        touchpadDirectionSticky(delta, activateSlop, dockDownSlop, armed)
                    } else {
                        touchpadDirection(delta, activateSlop, dockDownSlop)
                    }
                }
                return if (sticky) touchpadDirectionSticky(delta, activateSlop, activateSlop, armed)
                else touchpadDirection(delta, activateSlop)
            }

            fun enterSelectMode() {
                if (selectMode) return
                selectMode = true
                currentHold(1f)
                // Expand the dock + haptic. Continuous from press — no re-zero.
                currentLong()
            }

            while (true) {
                if (holdToSelect && !enabledState) return@awaitEachGesture
                val event = if (!swiping && !selectMode) {
                    withTimeoutOrNull(16L) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    val p = holdOf()
                    currentHold(p)
                    // Optional hold: preview shortcuts before moving.
                    if (holdToSelect && p >= 1f) enterSelectMode()
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@awaitEachGesture
                if (holdToSelect && (change.isConsumed || event.changes.count { it.pressed } > 1 || !enabledState))
                    return@awaitEachGesture
                total = change.position - origin
                change.consume()
                val dist = hypot(total.x, total.y)
                if (dist > trackSlop) {
                    if (holdToSelect) {
                        // Touch + drag past slop → LumenPad mode (no long-press required).
                        enterSelectMode()
                        swiping = true
                    } else if (!selectMode) {
                        swiping = true
                        currentHold(0f)
                    }
                }
                if (swiping || (holdToSelect && selectMode)) {
                    currentDrag(total)
                    val next = direction()
                    if (next != armed) {
                        armed = next
                        currentArmed(next)
                    }
                } else if (!selectMode) {
                    currentHold(holdOf())
                }
                if (change.pressed) continue

                currentPress(false, change.position)
                if (holdToSelect && selectMode) {
                    direction(sticky = false)?.let { currentSwipe(it) }
                    currentDrag(Offset.Zero)
                    currentArmed(null)
                    currentHold(0f)
                    return@awaitEachGesture
                }
                if (swiping) {
                    val commit = direction(sticky = false)
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

            if (selectMode || swiping) return@awaitEachGesture
            // Dock ring: fire Search immediately on release — don't wait for a double-tap window.
            // A second tap within the window still opens Voice.
            if (holdToSelect) {
                currentPulse(1, down.position)
                currentTap()
                val extra = withTimeoutOrNull(TouchpadTapWindowMs) {
                    awaitFirstDown(requireUnconsumed = false)
                }
                if (extra != null) {
                    extra.consume()
                    currentPress(true, extra.position)
                    val moved = waitForUp(extra.id, trackSlop)
                    currentPress(false, extra.position)
                    if (!moved) {
                        currentPulse(2, extra.position)
                        currentDouble()
                    }
                }
                return@awaitEachGesture
            }
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
            } finally {
                currentPress(false, Offset.Zero)
                currentDrag(Offset.Zero)
                currentArmed(null)
                currentHold(0f)
            }
        }
    }
}

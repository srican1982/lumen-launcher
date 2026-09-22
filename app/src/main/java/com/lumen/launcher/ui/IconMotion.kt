package com.lumen.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

enum class IconPhase {
    Rest,
    Pressed,
    Launching,
    Lifted,
    Dragging
}

private const val LIFT_HOLD_MS = 420L

@Composable
fun Modifier.iconContact(
    key: Any,
    enabled: Boolean = true,
    allowDrag: Boolean = false,
    onPhase: (IconPhase) -> Unit,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
    onLift: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {}
): Modifier {
    val latestPhase by rememberUpdatedState(onPhase)
    val latestLaunch by rememberUpdatedState(onLaunch)
    val latestLong by rememberUpdatedState(onLongPress)
    val latestLift by rememberUpdatedState(onLift)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onDragEnd)
    val enabledState by rememberUpdatedState(enabled)
    val allowDragState by rememberUpdatedState(allowDrag)
    return pointerInput(key) {
        if (!enabledState) return@pointerInput
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabledState) {
                waitForUpOrCancellation()
                return@awaitEachGesture
            }
            latestPhase(IconPhase.Pressed)
            var finger = down.position
            val first = withTimeoutOrNull(LIFT_HOLD_MS) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull "lost"
                    finger = change.position
                    if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                        return@withTimeoutOrNull "up"
                    }
                    if ((finger - down.position).getDistance() > slop) {
                        return@withTimeoutOrNull "swipe"
                    }
                }
            }
            when (first) {
                "up" -> {
                    if ((finger - down.position).getDistance() <= slop) {
                        down.consume()
                        latestPhase(IconPhase.Launching)
                        latestLaunch()
                    } else {
                        latestPhase(IconPhase.Rest)
                    }
                    return@awaitEachGesture
                }
                "swipe", "lost" -> {
                    latestPhase(IconPhase.Rest)
                    return@awaitEachGesture
                }
            }
            down.consume()
            val pickup = finger
            latestPhase(IconPhase.Lifted)
            latestLift()
            if (!allowDragState) {
                val held = waitForUpOrCancellation()
                latestPhase(IconPhase.Rest)
                if (held != null) latestLong()
                return@awaitEachGesture
            }
            var dragging = false
            val dragSlop = slop * 2.5f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.changedToUpIgnoreConsumed()) {
                    latestPhase(IconPhase.Rest)
                    if (dragging) latestEnd() else latestLong()
                    break
                }
                finger = change.position
                val drag = finger - pickup
                if (dragging || drag.getDistance() > dragSlop) {
                    if (!dragging) {
                        dragging = true
                        latestPhase(IconPhase.Dragging)
                    }
                    change.consume()
                    latestDrag(drag)
                }
            }
        }
    }
}

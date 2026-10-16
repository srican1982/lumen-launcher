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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Native widget stays tappable.
 * Long-press opens the edit menu; dragging after that (or while already selected) moves the card.
 */
@Composable
internal fun Modifier.widgetBoardDrag(
    key: Any,
    onLongPress: () -> Unit,
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    immediate: Boolean = false
): Modifier {
    val longPress by rememberUpdatedState(onLongPress)
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(key, immediate) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val edge = 32.dp.toPx()
            // Leave room for the menu and gold resize handles when already selected.
            if (immediate && (
                    down.position.y < edge ||
                        down.position.x < edge ||
                        down.position.x > size.width - edge ||
                        down.position.y > size.height - edge
                    )
            ) return@awaitEachGesture

            var active = false
            var completed = false
            try {
                if (!immediate) {
                    // Match Smart Launcher: fire a bit before the system long-press timeout.
                    val holdMs = (viewConfiguration.longPressTimeoutMillis * 0.75f).toLong()
                        .coerceAtLeast(280L)
                    val cancelled = withTimeoutOrNull(holdMs) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull true
                            if (!change.pressed ||
                                event.changes.count { it.pressed } > 1 ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            ) return@withTimeoutOrNull true
                        }
                        @Suppress("UNREACHABLE_CODE") true
                    }
                    if (cancelled != null) return@awaitEachGesture

                    longPress()

                    // Hold opened the menu — only start a move if the finger then slides.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                }

                if (immediate) {
                    // A tap reopens widget options; only a slide starts a move.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                }
                active = true
                start()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                    val amount = change.positionChange()
                    change.consume()
                    drag(amount)
                    if (!change.pressed) {
                        completed = true
                        end()
                        return@awaitEachGesture
                    }
                }
            } finally {
                if (active && !completed) cancel()
            }
        }
    }
}

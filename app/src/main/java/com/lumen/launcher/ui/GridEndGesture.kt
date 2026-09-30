package com.lumen.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A fresh swipe at the bottom opens Apps; reaching the bottom in a scroll does not. */
@Composable
internal fun Modifier.drawerAtGridEnd(enabled: Boolean, gridState: LazyGridState, onOpen: () -> Unit): Modifier {
    val currentOpen by rememberUpdatedState(onOpen)
    val currentEnabled by rememberUpdatedState(enabled)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return pointerInput(gridState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pad = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "touchpad-row" }
            val onPad = pad != null && down.position.y >= pad.offset.y && down.position.y < pad.offset.y + pad.size.height
            if (!currentEnabled || gridState.canScrollForward || gridState.isScrollInProgress || onPad) return@awaitEachGesture
            var committed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - down.position
                if (!committed) {
                    if (delta.y > viewConfiguration.touchSlop || abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) >= abs(delta.y)) break
                    if (delta.y < -viewConfiguration.touchSlop && -delta.y > abs(delta.x) * 1.35f) committed = true
                }
                if (committed) change.consume()
                if (!change.pressed) {
                    if (committed && delta.y < -threshold && -delta.y > abs(delta.x) * 1.35f) currentOpen()
                    break
                }
            }
        }
    }
}

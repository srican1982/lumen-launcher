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

/** Fresh vertical pulls at a grid edge open Search or Apps without interrupting scrolling. */
@Composable
internal fun Modifier.gridEdgeGestures(
    enabled: Boolean,
    gridState: LazyGridState,
    scrollEnabled: Boolean,
    onOpenSearch: () -> Unit,
    onOpenDrawer: () -> Unit
): Modifier {
    val currentSearch by rememberUpdatedState(onOpenSearch)
    val currentDrawer by rememberUpdatedState(onOpenDrawer)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentScrollEnabled by rememberUpdatedState(scrollEnabled)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return pointerInput(gridState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pad = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "touchpad-row" }
            val onPad = pad != null && down.position.y >= pad.offset.y && down.position.y < pad.offset.y + pad.size.height
            if (!currentEnabled || gridState.isScrollInProgress || onPad) return@awaitEachGesture
            val canSearch = !currentScrollEnabled || !gridState.canScrollBackward
            val canOpenDrawer = !currentScrollEnabled || !gridState.canScrollForward
            if (!canSearch && !canOpenDrawer) return@awaitEachGesture
            var direction = 0
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - down.position
                if (direction == 0) {
                    if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) >= abs(delta.y)) break
                    if (delta.y > viewConfiguration.touchSlop) {
                        if (!canSearch || delta.y <= abs(delta.x) * 1.35f) break
                        direction = 1
                    } else if (delta.y < -viewConfiguration.touchSlop) {
                        if (!canOpenDrawer || -delta.y <= abs(delta.x) * 1.35f) break
                        direction = -1
                    }
                }
                if (direction != 0) change.consume()
                if (!change.pressed) {
                    if (currentEnabled && direction == 1 && delta.y > threshold && delta.y > abs(delta.x) * 1.35f) currentSearch()
                    if (currentEnabled && direction == -1 && delta.y < -threshold && -delta.y > abs(delta.x) * 1.35f) currentDrawer()
                    break
                }
            }
        }
    }
}

package com.lumen.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Pager gestures stay off until every visible widget selection is dismissed. */
internal class WidgetEditSession {
    private var owners by mutableStateOf(emptyMap<Any, Boolean>())
    private val bounds = mutableMapOf<Any, Rect>()
    private val outsideHandlers = mutableMapOf<Any, () -> Unit>()
    @Volatile var guardCoords: LayoutCoordinates? = null

    val active: Boolean get() = owners.isNotEmpty()
    val exclusive: Boolean get() = owners.values.any { it }

    fun acquire(owner: Any, exclusive: Boolean = true, onOutside: () -> Unit = {}) {
        owners = owners + (owner to exclusive)
        outsideHandlers[owner] = onOutside
    }

    fun updateBounds(owner: Any, rect: Rect) {
        bounds[owner] = rect
    }

    /** True when [rootPoint] is on/near a selected widget (includes resize edge hits). */
    fun allows(rootPoint: Offset, padPx: Float = 56f): Boolean =
        !exclusive || owners.any { (owner, locked) ->
            locked && bounds[owner]?.inflate(padPx)?.contains(rootPoint) == true
        }

    /** Tap outside any selected widget — finish resize / clear selection. */
    fun dismissOutside(rootPoint: Offset) {
        if (!exclusive || allows(rootPoint)) return
        outsideHandlers.filterKeys { owners[it] == true }.values.toList().forEach { it() }
    }

    fun release(owner: Any) {
        owners = owners - owner
        bounds.remove(owner)
        outsideHandlers.remove(owner)
    }
}

internal val LocalWidgetEditSession = staticCompositionLocalOf { WidgetEditSession() }

/**
 * End edit only when the press misses every selected widget.
 * Touches on the gold border / resize edges must keep selection and allow drag-resize.
 */
internal fun Modifier.widgetEditGuard(session: WidgetEditSession): Modifier =
    this
        .onGloballyPositioned { session.guardCoords = it }
        .pointerInput(session) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val mapped = session.guardCoords
                    ?.takeIf { it.isAttached }
                    ?.localToRoot(down.position)
                    ?: down.position
                if (session.exclusive && !session.allows(mapped)) {
                    down.consume()
                    session.dismissOutside(mapped)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }

package com.lumen.launcher.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/** Progress through the bottom quarter of the visible list, with a smooth join. */
internal fun drawerStackAmount(center: Float, viewportStart: Int, viewportEnd: Int): Float {
    val height = (viewportEnd - viewportStart).toFloat()
    if (height <= 0f || !center.isFinite()) return 0f
    val progress = ((center - viewportStart - height * 0.75f) / (height * 0.25f)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}

/** Draw-only transform: scrolling and row measurements stay unchanged. */
internal fun Modifier.drawerStack(state: LazyListState, key: String, enabled: Boolean): Modifier = graphicsLayer {
    val layout = state.layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
    val amount = if (enabled && item != null) {
        drawerStackAmount(item.offset + item.size / 2f, layout.viewportStartOffset, layout.viewportEndOffset)
    } else 0f
    scaleX = 1f - 0.07f * amount
    scaleY = 1f
    alpha = 1f - 0.08f * amount
    transformOrigin = TransformOrigin.Center
}

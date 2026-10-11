package com.lumen.launcher.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ignore trailing padding and the icon tiles' blank bottom edge when deciding to scroll. */
@Composable
internal fun rememberGridNeedsScroll(state: LazyGridState, blankEdge: Dp = 0.dp): Boolean {
    val tolerance = with(LocalDensity.current) { blankEdge.roundToPx() }
    val needed by remember(state, tolerance) {
        derivedStateOf {
            val layout = state.layoutInfo
            val visible = layout.visibleItemsInfo
            if (layout.totalItemsCount == 0) false
            else if (visible.size < layout.totalItemsCount) true
            else {
                val first = visible.minOf { it.offset.y }
                val last = visible.maxOf { it.offset.y + it.size.height }
                val viewport = layout.viewportEndOffset - layout.viewportStartOffset
                last - first + layout.beforeContentPadding > viewport + tolerance
            }
        }
    }
    return needed
}

package com.lumen.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Rounded once in both the header and viewport to keep child constraints stable. */
internal object MeMotionGeometry {
    fun expansion(expanded: Int, collapsed: Int, progress: Float): Int =
        ((expanded - collapsed).coerceAtLeast(0) * progress.coerceIn(0f, 1f)).roundToInt()

    fun contentHeight(viewport: Int, expanded: Int, collapsed: Int, progress: Float, personal: Boolean): Int =
        (viewport + expansion(expanded, collapsed, progress) -
            if (personal) (expanded - collapsed).coerceAtLeast(0) else 0).coerceAtLeast(0)
}

/** The image/text are measured at their final sizes; only the header's layout height changes. */
@Composable
internal fun MeCollapsingHeader(
    expandedHeight: Dp, progress: () -> Float, personalSelected: Boolean,
    compact: @Composable () -> Unit, expanded: @Composable () -> Unit
) {
    Layout(modifier = Modifier.clipToBounds(), content = { compact(); expanded() }) { children, constraints ->
        val compactHeight = 72.dp.roundToPx()
        val fullHeight = expandedHeight.roundToPx().coerceAtLeast(compactHeight)
        val compact = children[0].measure(Constraints.fixed(constraints.maxWidth, compactHeight))
        val expanded = children[1].measure(Constraints.fixed(constraints.maxWidth, fullHeight))
        val height = compactHeight + MeMotionGeometry.expansion(fullHeight, compactHeight, progress())
        layout(constraints.maxWidth, height) {
            compact.placeRelative(0, height - compactHeight, zIndex = if (personalSelected) 0f else 1f)
            expanded.placeRelative(0, height - fullHeight, zIndex = if (personalSelected) 1f else 0f)
        }
    }
}

/** Moving the navigation resizes the viewport, without remeasuring widgets or decoding photos each frame. */
internal fun Modifier.meStableViewport(expandedHeight: Dp, progress: () -> Float, personal: Boolean = false, placeContent: Boolean = true): Modifier =
    layout { child, constraints ->
        if (!constraints.hasBoundedHeight) {
            val placeable = child.measure(constraints)
            layout(placeable.width, placeable.height) { if (placeContent) placeable.placeRelative(0, 0) }
        } else {
            val stableHeight = MeMotionGeometry.contentHeight(constraints.maxHeight,
                expandedHeight.roundToPx(), 72.dp.roundToPx(), progress(), personal)
            val placeable = child.measure(constraints.copy(minHeight = stableHeight, maxHeight = stableHeight))
            layout(placeable.width, constraints.maxHeight) { if (placeContent) placeable.placeRelative(0, 0) }
        }
    }

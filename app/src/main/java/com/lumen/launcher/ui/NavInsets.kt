package com.lumen.launcher.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Reliable bottom inset for dialogs / sheets. Compose WindowInsets alone can report 0
 * on some OEM dialog windows even when the 3-button nav bar is visible.
 */
@Composable
fun rememberNavBottomPadding(minimum: Dp = 28.dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val compose = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fromView = remember(view, view.rootWindowInsets) {
        val px = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?: 0
        with(density) { px.toDp() }
    }
    val fromResources = remember(view) {
        val id = view.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) with(density) { view.resources.getDimensionPixelSize(id).toDp() } else 0.dp
    }
    return max(max(max(compose, fromView), fromResources), minimum)
}

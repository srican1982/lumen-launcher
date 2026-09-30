package com.lumen.launcher.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.tappableElement
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
 * Reliable bottom inset for dialogs / sheets.
 * OEM dialog windows often report 0 for Compose navigationBars alone.
 */
@Composable
fun rememberNavBottomPadding(minimum: Dp = 56.dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val composeNav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val composeSys = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val composeTap = WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding()
    val fromView = remember(view) {
        val root = ViewCompat.getRootWindowInsets(view)
        val nav = root?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val sys = root?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        val tap = root?.getInsets(WindowInsetsCompat.Type.tappableElement())?.bottom ?: 0
        with(density) { maxOf(nav, sys, tap).toDp() }
    }
    val fromResources = remember(view) {
        val id = view.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) with(density) { view.resources.getDimensionPixelSize(id).toDp() } else 0.dp
    }
    // Samsung 3-button nav is often ~48–56dp; keep a hard floor so actions never sit under it.
    return max(max(max(max(max(composeNav, composeSys), composeTap), fromView), fromResources), minimum)
}

package com.lumen.launcher.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Shared drawer purple glass palette (matches app-drawer mockup). */
object DrawerTheme {
    val gradient = Brush.verticalGradient(
        0f to Color(0xFF3D2468),
        0.32f to Color(0xFF2A1450),
        0.72f to Color(0xFF1A0C32),
        1f to Color(0xFF0E0818)
    )
    val veil = Color(0xB3180C28)
    val sheetTint = Color(0xFF2A1848)
}

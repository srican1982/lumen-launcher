package com.lumen.launcher.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Shared drawer pink glass palette (matches app-drawer mockup). */
object DrawerTheme {
    val gradient = Brush.verticalGradient(
        0f to Color(0xFFA22E77),
        0.35f to Color(0xFF6E1E5C),
        0.72f to Color(0xFF3A1236),
        1f to Color(0xFF190A18)
    )
    val veil = Color(0x40200A1C)
    val sheetTint = Color(0xFF6E1E5C)
}

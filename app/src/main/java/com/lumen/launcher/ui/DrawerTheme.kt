package com.lumen.launcher.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Warm wallpaper glass palette from the gold drawer reference. */
object DrawerTheme {
    val gradient = Brush.verticalGradient(
        0f to Color(0xFF9B3B0D),
        0.35f to Color(0xFFA80032),
        0.72f to Color(0xFFAC6237),
        1f to Color(0xFF77746C)
    )
    val veil = Color(0x120F0809)
    val sheetTint = Color(0xFFA80032)
}

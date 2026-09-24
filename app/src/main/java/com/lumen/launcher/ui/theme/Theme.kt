package com.lumen.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumen.launcher.R

val Outfit = FontFamily(
    Font(R.font.outfit_extralight, FontWeight.ExtraLight),
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold)
)

/** Which palette is active. Set by the view model from Settings → Theme. */
object LumenPalette {
    var whiteGlass by mutableStateOf(false)
}

/**
 * Lumen's shared colors. Each one follows the active theme (Lumen Violet or White Glass),
 * so every screen that uses these switches automatically.
 */
object Lumen {
    private val w: Boolean get() = LumenPalette.whiteGlass

    val Night: Color get() = if (w) Color(0xFF14171C) else Color(0xFF14061F)
    val Dusk: Color get() = if (w) Color(0xFF2A2F37) else Color(0xFF3B1568)
    val Bloom: Color get() = if (w) Color(0xFF4A5260) else Color(0xFF6D3AA0)
    val Accent: Color get() = if (w) Color(0xFFFFFFFF) else Color(0xFFE4C6FF)
    val AccentDeep: Color get() = if (w) Color(0xFFE6EBF2) else Color(0xFFC084FC)
    val Pink: Color get() = if (w) Color(0xFFF3F4F6) else Color(0xFFF0ABFC)
    val Text = Color(0xFFF8F1FF)
    val Muted = Color(0xC4F8F1FF)
    val Faint = Color(0x8AF8F1FF)
    val GlassHi: Color get() = if (w) Color(0x4DFFFFFF) else Color(0x38FFFFFF)
    val GlassMid: Color get() = if (w) Color(0x2EFFFFFF) else Color(0x1EFFFFFF)
    val GlassLo: Color get() = if (w) Color(0x1FFFFFFF) else Color(0x14FFFFFF)
    val StrokeHi: Color get() = if (w) Color(0x80FFFFFF) else Color(0x55FFFFFF)
    val StrokeLo: Color get() = if (w) Color(0x26FFFFFF) else Color(0x18FFFFFF)
    val Ripple = Color(0x33FFFFFF)
    val Scrim: Color get() = if (w) Color(0x99101216) else Color(0x9911061C)
    val OnAccent: Color get() = if (w) Color(0xFF1F2430) else Color(0xFF2A1048)
    val IconCorner = 32
    val DockRadius = 40.dp
    val PillRadius = 28.dp
    val SheetRadius = 32.dp
    val TileRadius = 22.dp

    private val violetMesh = Brush.verticalGradient(
        0f to Color(0xFF4A1D86),
        0.42f to Color(0xFF2E1060),
        1f to Color(0xFF14061F)
    )
    private val whiteMesh = Brush.verticalGradient(
        0f to Color(0xFF3A414B),
        0.42f to Color(0xFF252A31),
        1f to Color(0xFF14171C)
    )
    private val violetFill = Brush.linearGradient(
        listOf(Color(0xFFE9D5FF), Color(0xFFD8B4FE), Color(0xFFF0ABFC))
    )
    private val whiteFill = Brush.linearGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFF1F4F8), Color(0xFFE3E8EF))
    )

    val Mesh: Brush get() = if (w) whiteMesh else violetMesh
    val AccentFill: Brush get() = if (w) whiteFill else violetFill
}

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Lumen.AccentDeep,
        onPrimary = Lumen.OnAccent,
        background = Color.Transparent,
        surface = Color.Transparent,
        onBackground = Lumen.Text,
        onSurface = Lumen.Text
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

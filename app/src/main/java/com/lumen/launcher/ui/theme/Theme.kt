package com.lumen.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

@Immutable
object Lumen {
    val Night = Color(0xFF14061F)
    val Dusk = Color(0xFF3B1568)
    val Bloom = Color(0xFF6D3AA0)
    val Accent = Color(0xFFE4C6FF)
    val AccentDeep = Color(0xFFC084FC)
    val Pink = Color(0xFFF0ABFC)
    val Text = Color(0xFFF8F1FF)
    val Muted = Color(0xC4F8F1FF)
    val Faint = Color(0x8AF8F1FF)
    val GlassHi = Color(0x38FFFFFF)
    val GlassMid = Color(0x1EFFFFFF)
    val GlassLo = Color(0x14FFFFFF)
    val StrokeHi = Color(0x55FFFFFF)
    val StrokeLo = Color(0x18FFFFFF)
    val Ripple = Color(0x33FFFFFF)
    val Scrim = Color(0x9911061C)
    val OnAccent = Color(0xFF2A1048)
    val IconCorner = 32
    val DockRadius = 40.dp
    val PillRadius = 28.dp
    val SheetRadius = 32.dp
    val TileRadius = 22.dp

    val Mesh = Brush.verticalGradient(
        0f to Color(0xFF4A1D86),
        0.42f to Color(0xFF2E1060),
        1f to Color(0xFF14061F)
    )
    val AccentFill = Brush.linearGradient(
        listOf(Color(0xFFE9D5FF), Color(0xFFD8B4FE), Color(0xFFF0ABFC))
    )
}

private val scheme = darkColorScheme(
    primary = Lumen.AccentDeep,
    onPrimary = Lumen.OnAccent,
    background = Color.Transparent,
    surface = Color.Transparent,
    onBackground = Lumen.Text,
    onSurface = Lumen.Text
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

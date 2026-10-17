package com.lumen.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Gold = Color(0xFFE7C27A)
private val GoldBright = Color(0xFFFFE7B0)
private val GoldDeep = Color(0xFFB38745)
private val GoldFlare = Color(0xFFFFDA86)
private val Neon = Color(0xFFFFB84A)

/**
 * Search pill surface matching the mock: dark translucent glass with
 * intense neon-gold filaments along the top and bottom edges.
 */
@Composable
internal fun SearchOrbSurface(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "search-orb")
    val shimmer by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "orb-shimmer"
    )
    val pulse by transition.animateFloat(
        0.82f, 1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rim-pulse"
    )
    Canvas(modifier) {
        val radius = size.height / 2f
        val pad = 1.6.dp.toPx()

        // Soft floating shadow under the pill.
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF1A0808).copy(0.35f), Color.Transparent)
            ),
            topLeft = Offset(size.width * 0.06f, size.height * 0.55f),
            size = Size(size.width * 0.88f, size.height * 0.7f),
            cornerRadius = CornerRadius(radius)
        )

        // Dark translucent glass body — wallpaper shows through.
        drawRoundRect(
            Brush.verticalGradient(
                listOf(
                    Color(0x55FFFFFF),
                    Color(0x3324181C),
                    Color(0x66140C10),
                    Color(0x442A1814)
                )
            ),
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(
                    Color(0x22FFF0CD),
                    Color.Transparent,
                    Color(0x189C5329),
                    Color(0x18F6C982)
                )
            ),
            cornerRadius = CornerRadius(radius)
        )
        // Top glass sheen.
        drawOval(
            Brush.radialGradient(
                listOf(Color.White.copy(0.22f), Color.White.copy(0.05f), Color.Transparent),
                Offset(size.width * (0.28f + 0.08f * sin(shimmer * 6.28f)), size.height * 0.05f),
                size.width * 0.55f
            ),
            topLeft = Offset(size.width * 0.05f, -size.height * 0.45f),
            size = Size(size.width * 0.9f, size.height * 0.95f)
        )

        // Outer soft neon halo.
        drawRoundRect(
            Brush.verticalGradient(
                listOf(
                    Neon.copy(0.28f * pulse),
                    Color.Transparent,
                    Neon.copy(0.22f * pulse)
                )
            ),
            cornerRadius = CornerRadius(radius),
            style = Stroke(5.5.dp.toPx())
        )

        // Intense top + bottom gold filaments (mock’s lit rims).
        val filament = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                GoldFlare.copy(0.35f),
                Neon.copy(0.95f * pulse),
                GoldBright.copy(0.9f),
                Neon.copy(0.95f * pulse),
                GoldFlare.copy(0.35f),
                Color.Transparent
            )
        )
        drawLine(
            filament,
            Offset(radius * 0.55f, pad + 0.8.dp.toPx()),
            Offset(size.width - radius * 0.55f, pad + 0.8.dp.toPx()),
            2.2.dp.toPx(),
            StrokeCap.Round
        )
        drawLine(
            filament,
            Offset(radius * 0.55f, size.height - pad - 0.8.dp.toPx()),
            Offset(size.width - radius * 0.55f, size.height - pad - 0.8.dp.toPx()),
            2.2.dp.toPx(),
            StrokeCap.Round
        )
        // Crisp metallic rim.
        drawRoundRect(
            Brush.sweepGradient(
                listOf(
                    GoldBright.copy(0.85f),
                    Gold.copy(0.35f),
                    GoldDeep.copy(0.55f),
                    GoldBright.copy(0.7f),
                    Gold.copy(0.3f),
                    GoldBright.copy(0.85f)
                ),
                center = Offset(size.width * 0.55f, size.height * 0.45f)
            ),
            topLeft = Offset(pad, pad),
            size = Size(size.width - pad * 2f, size.height - pad * 2f),
            cornerRadius = CornerRadius(radius - pad),
            style = Stroke(1.15.dp.toPx())
        )
    }
}

/**
 * Lumen ring mark.
 * Prominent = fluid glass orb (Siri-like) in Lumen golds, with a bright rim dot
 * orbiting the sphere. Compact = smaller glass pearl for the search bar.
 */
@Composable
internal fun SearchOrbMark(modifier: Modifier = Modifier, prominent: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "orb-ring")
    val spin by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (prominent) 14000 else 11000, easing = LinearEasing)),
        label = "spin"
    )
    val morph by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(7800, easing = LinearEasing)),
        label = "morph"
    )
    val pulse by transition.animateFloat(
        0.78f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val dotAngle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (prominent) 4200 else 9000, easing = LinearEasing)),
        label = "rim-dot"
    )
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * if (prominent) .40f else .40f
        val t = morph * (2f * PI.toFloat())

        if (prominent) {
            // Soft Lumen gold under-glow on the shelf.
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        GoldFlare.copy(0.36f * pulse),
                        Gold.copy(0.16f * pulse),
                        Color.Transparent
                    ),
                    center,
                    r * 1.65f
                ),
                r * 1.65f,
                center
            )

            // Smoky transparent glass — wallpaper peeks through, warmed with champagne.
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        Color(0x55FFF3D6),
                        Color(0x55D4B07A),
                        Color(0x403A2420),
                        Color(0x28181014)
                    ),
                    center - Offset(r * 0.18f, r * 0.22f),
                    r * 1.35f
                ),
                r,
                center
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(0.10f), Gold.copy(0.12f), Color.Transparent),
                    center,
                    r * 0.95f
                ),
                r * 0.95f,
                center
            )

            val glass = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center.x - r * 0.96f,
                        center.y - r * 0.96f,
                        center.x + r * 0.96f,
                        center.y + r * 0.96f
                    )
                )
            }
            clipPath(glass) {
                fun blob(phase: Float, color: Color, dist: Float, sizeMul: Float, alpha: Float = 0.72f) {
                    val a = (spin + phase) * (PI.toFloat() / 180f)
                    val wobble = 0.62f + 0.38f * sin(t * 1.6f + phase * 0.02f)
                    val c = center + Offset(
                        cos(a) * r * dist * wobble,
                        sin(a * 1.12f + t * 0.4f) * r * dist * wobble
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(color.copy(alpha * pulse), color.copy(alpha * 0.35f), Color.Transparent),
                            c,
                            r * sizeMul
                        ),
                        r * sizeMul,
                        c,
                        blendMode = BlendMode.Screen
                    )
                }
                // Signature Lumen gold leads; a soft violet accent keeps it alive.
                blob(20f, Color(0xFF4285FF), 0.36f, 0.76f, 0.92f)
                blob(280f, Color(0xFFB943FF), 0.34f, 0.64f, 0.90f)
                blob(150f, Color(0xFFC9A0FF), 0.34f, 0.48f, 0.42f)
                blob(90f + morph * 40f, Color(0xFFFFC4FA), 0.14f, 0.44f, 0.82f)
                blob(200f, Gold, 0.30f, 0.38f, 0.40f)


            }

            // Home-chip gold rim (#FAC775) — ties orb to Home / + tile without loud fill.
            val homeGold = Color(0xFFFAC775)
            drawCircle(homeGold.copy(0.28f), r + 0.6.dp.toPx(), center, style = Stroke(3.2.dp.toPx()))
            drawCircle(homeGold.copy(0.92f), r, center, style = Stroke(2.dp.toPx()))
            drawOval(
                Brush.verticalGradient(listOf(Color(0xFFFFF6E0).copy(0.28f), Color.Transparent)),
                topLeft = center - Offset(r * 0.52f, r * 0.76f),
                size = Size(r * 1.0f, r * 0.40f)
            )

            // Small bead in the same Home gold.
            val da = dotAngle * (PI.toFloat() / 180f)
            val rim = center + Offset(cos(da) * r, sin(da) * r)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(0.9f), homeGold.copy(0.9f), homeGold.copy(0.25f), Color.Transparent),
                    rim,
                    10.dp.toPx()
                ),
                10.dp.toPx(),
                rim
            )
            drawCircle(homeGold, 2.4.dp.toPx(), rim)
            drawCircle(Color.White.copy(0.85f), 1.15.dp.toPx(), rim)
        } else {
            // Compact search-bar orb — glass pearl + orbiting glint.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFC34D).copy(0.32f * pulse), Gold.copy(0.1f), Color.Transparent),
                    center + Offset(0f, r * 0.2f),
                    r * 1.55f
                ),
                r * 1.55f,
                center + Offset(0f, r * 0.12f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF2A1A16), Color(0xFF140C0C)),
                    center - Offset(r * 0.12f, r * 0.18f),
                    r
                ),
                r,
                center
            )
            val glass = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center.x - r, center.y - r, center.x + r, center.y + r
                    )
                )
            }
            clipPath(glass) {
                val a1 = (spin) * (PI.toFloat() / 180f)
                val a2 = (spin + 140f) * (PI.toFloat() / 180f)
                val c1 = center + Offset(cos(a1) * r * 0.25f, sin(a1) * r * 0.25f)
                val c2 = center + Offset(cos(a2) * r * 0.3f, sin(a2) * r * 0.28f)
                drawCircle(
                    Brush.radialGradient(listOf(GoldBright.copy(0.7f), Color.Transparent), c1, r * 0.55f),
                    r * 0.55f, c1, blendMode = BlendMode.Plus
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFF9A5C).copy(0.55f), Color.Transparent), c2, r * 0.45f),
                    r * 0.45f, c2, blendMode = BlendMode.Plus
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(0.8f), GoldFlare.copy(0.3f), Color.Transparent), center, r * 0.32f),
                    r * 0.32f, center, blendMode = BlendMode.Plus
                )
            }
            drawCircle(
                Brush.sweepGradient(listOf(GoldBright, Gold, GoldFlare, GoldBright), center),
                r,
                center,
                style = Stroke(1.2.dp.toPx())
            )
            val da = dotAngle * (PI.toFloat() / 180f)
            val dot = center + Offset(cos(da) * r, sin(da) * r)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(0.95f), GoldBright.copy(0.55f), Color.Transparent),
                    dot,
                    8.dp.toPx()
                ),
                8.dp.toPx(),
                dot
            )
            drawCircle(Color.White, 1.8.dp.toPx(), dot)
        }
    }
}

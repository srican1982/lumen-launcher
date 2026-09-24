package com.lumen.launcher.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.R
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.GestureAction
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.data.TouchpadHaptics
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val Gold = Color(0xFFE7C27A)
private val GoldHi = Color(0xFFF3D7A0)
private val GoldGlow = Color(0xFFF8E7C4)

private data class PadPulse(val id: Int, val origin: Offset, val strength: Float)

@Composable
fun TouchpadIsland(
    enabled: Boolean,
    state: LauncherUiState,
    icons: IconCache,
    onGesture: (String) -> Unit,
    onPrivateArmed: () -> Unit,
    onBoundsInWindow: (Float, Float, Float, Float) -> Unit,
    dropReady: Boolean = false,
    modifier: Modifier = Modifier,
    /** false = no own glass frame / gold rim (used inside the big TouchPad card). */
    framed: Boolean = true,
    /** Size of the gold Lumen ring. */
    markSize: Dp = 60.dp
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    var hold by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var armed by remember { mutableStateOf<TouchpadSwipe?>(null) }
    var lockMorph by remember { mutableFloatStateOf(0f) }
    var pulses by remember { mutableStateOf(listOf<PadPulse>()) }
    var pulseSeq by remember { mutableStateOf(0) }
    val pulseAges = remember { mutableStateMapOf<Int, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    val scale = remember { Animatable(1f) }
    val holdShown by animateFloatAsState(
        hold,
        animationSpec = tween(if (hold == 0f) 160 else 16, easing = LinearEasing),
        label = "touchpad-hold"
    )
    val lockShown by animateFloatAsState(lockMorph, tween(280), label = "touchpad-lock")
    val dropShown by animateFloatAsState(if (dropReady) 1f else 0f, tween(160), label = "touchpad-drop")
    val trailX by animateFloatAsState(drag.x, spring(dampingRatio = 0.88f, stiffness = 380f), label = "tx")
    val trailY by animateFloatAsState(drag.y, spring(dampingRatio = 0.88f, stiffness = 380f), label = "ty")
    val mode = state.touchpadHaptics

    fun haptic(kind: PadHaptic) = view.playPadHaptic(mode, kind)

    fun springUp() {
        scope.launch {
            scale.animateTo(1.015f, spring(dampingRatio = 0.42f, stiffness = 900f))
            scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 700f))
        }
    }

    fun pulseAt(count: Int, origin: Offset) {
        pulseSeq += 1
        val id = pulseSeq
        val strength = when (count) {
            1 -> 0.55f
            2 -> 0.78f
            else -> 1f
        }
        val age = Animatable(0f)
        pulseAges[id] = age
        pulses = (pulses + PadPulse(id, origin, strength)).takeLast(3)
        haptic(PadHaptic.Tick)
        scope.launch {
            age.animateTo(1f, tween(380, easing = LinearEasing))
            pulses = pulses.filter { it.id != id }
            pulseAges.remove(id)
        }
    }

    LaunchedEffect(state.privatePageActive) {
        if (state.privatePageActive) haptic(PadHaptic.Success)
    }
    LaunchedEffect(state.privatePrompting, state.privatePageActive) {
        if (!state.privatePrompting && !state.privatePageActive && lockMorph > 0f) {
            kotlinx.coroutines.delay(80)
            if (!state.privatePrompting && !state.privatePageActive) lockMorph = 0f
        }
    }

    val swipeKind = when (armed) {
        TouchpadSwipe.Up -> "swipeUp"
        TouchpadSwipe.Down -> "swipeDown"
        TouchpadSwipe.Left -> "swipeLeft"
        TouchpadSwipe.Right -> "swipeRight"
        null -> null
    }
    val revealSpec = swipeKind?.let { specFor(state, it) }.orEmpty()
    val revealApp = if (GestureAction.isApp(revealSpec)) {
        state.apps.find { it.key == GestureAction.appKey(revealSpec) }
    } else null
    val trailMag = hypot(trailX, trailY)
    val reveal = ((trailMag / with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }) - 0.5f)
        .coerceIn(0f, 1f) * 2f

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val box = coords.boundsInWindow()
                onBoundsInWindow(box.left, box.top, box.right, box.bottom)
            }
            .graphicsLayer {
                val stretchX = (trailX / 180f).coerceIn(-1f, 1f)
                val stretchY = (trailY / 180f).coerceIn(-1f, 1f)
                scaleX = scale.value * (1f + 0.014f * kotlin.math.abs(stretchX) + 0.03f * dropShown)
                scaleY = scale.value * (1f + 0.014f * kotlin.math.abs(stretchY) + 0.03f * dropShown)
                translationX = stretchX * 3.4f
                translationY = stretchY * 3.4f
            }
            // Only the framed pill casts a shadow; inside the glass card it would show as a pale band.
            .then(
                if (framed) Modifier.shadow(
                    elevation = if (pressed) 6.dp else 8.dp,
                    shape = Squircle,
                    spotColor = Color(0x66000000),
                    ambientColor = Color(0x33000000)
                ) else Modifier
            )
            .touchpadGestures(
                enabled = enabled && !state.privatePageActive,
                onPress = { down, origin ->
                    pressed = down
                    touch = origin
                    if (down) {
                        lockMorph = 0f
                        scope.launch { scale.animateTo(0.985f, tween(90)) }
                    } else if (lockMorph < 0.5f) {
                        springUp()
                    }
                },
                onHoldProgress = { hold = it },
                onDrag = { drag = it },
                onArmed = { next ->
                    if (next != null && armed == null) haptic(PadHaptic.Click)
                    armed = next
                },
                onTapPulse = { count, origin -> pulseAt(count, origin) },
                onTap = { onGesture("tap") },
                onDoubleTap = { onGesture("double") },
                onTripleTap = { onGesture("triple") },
                onLongPress = {
                    haptic(PadHaptic.Deep)
                    hold = 1f
                    scope.launch {
                        lockMorph = 1f
                        kotlinx.coroutines.delay(280)
                        onPrivateArmed()
                    }
                },
                onSwipe = { dir ->
                    onGesture(
                        when (dir) {
                            TouchpadSwipe.Up -> "swipeUp"
                            TouchpadSwipe.Down -> "swipeDown"
                            TouchpadSwipe.Left -> "swipeLeft"
                            TouchpadSwipe.Right -> "swipeRight"
                        }
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val pressGlow = if (pressed) 1f else 0f
        if (framed) Box(
            modifier = Modifier
                .matchParentSize()
                .clip(Squircle)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.40f - 0.08f * pressGlow),
                        0.45f to Color.White.copy(alpha = 0.18f),
                        1f to Color.White.copy(alpha = 0.10f)
                    )
                )
                .border(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                        0f to Color(0x99FFFFFF),
                        1f to Color(0x22FFFFFF)
                    ),
                    shape = Squircle
                )
        )
        if (framed) Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp)
                .clip(Squircle)
                .background(Color(0x33000000))
        )
        val pulseTicks = pulses.associate { it.id to (pulseAges[it.id]?.value ?: 1f) }
        Canvas(Modifier.fillMaxSize()) {
            if (framed) drawGoldBezel(pressGlow + holdShown * 0.35f + dropShown * 0.55f)
            val highlight = if (touch == Offset.Unspecified) Offset(size.width / 2f, size.height * 0.38f) else touch
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f + 0.14f * pressGlow),
                        Color.Transparent
                    ),
                    center = highlight,
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = highlight
            )

            val markR = markSize.toPx() * 0.47f
            val textBlock = 42.dp.toPx()
            val contentH = markSize.toPx() + 8.dp.toPx() + textBlock
            val center = Offset(
                size.width / 2f,
                ((size.height - contentH) / 2f + markSize.toPx() / 2f).coerceAtLeast(markR)
            )
            val trail = Offset(trailX, trailY)
            val mag = hypot(trail.x, trail.y)
            val stretch = (mag / (88.dp.toPx())).coerceIn(0f, 1f)
            val centerGlow = ((holdShown - 0.25f) / 0.25f).coerceIn(0f, 1f)
            if (centerGlow > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GoldGlow.copy(alpha = 0.28f * centerGlow), Color.Transparent),
                        center = center,
                        radius = markR * 2.1f
                    ),
                    radius = markR * 2.1f,
                    center = center
                )
            }
            if (stretch > 0.02f && mag > 0.5f) {
                val dir = trail / mag
                val tip = center + dir * (markR * 0.2f + size.minDimension * 0.38f * stretch)
                drawLine(
                    color = Gold.copy(alpha = 0.12f + 0.42f * stretch),
                    start = center,
                    end = tip,
                    strokeWidth = markR * (0.42f + 0.62f * stretch),
                    cap = StrokeCap.Round
                )
                drawCircle(GoldGlow.copy(alpha = 0.3f + 0.45f * stretch), 5.dp.toPx() + 8.dp.toPx() * stretch, tip)
                val edge = when {
                    kotlin.math.abs(dir.x) > kotlin.math.abs(dir.y) && dir.x > 0 -> Offset(size.width - 10.dp.toPx(), center.y)
                    kotlin.math.abs(dir.x) > kotlin.math.abs(dir.y) -> Offset(10.dp.toPx(), center.y)
                    dir.y < 0 -> Offset(center.x, 10.dp.toPx())
                    else -> Offset(center.x, size.height - 10.dp.toPx())
                }
                if (armed != null) {
                    drawCircle(GoldHi.copy(alpha = 0.22f + 0.35f * stretch), 18.dp.toPx(), edge)
                }
            }

            val ringStart = ((holdShown - 0.5f) * 2f).coerceIn(0f, 1f)
            if (ringStart > 0.01f) {
                val ringR = markR + 7.dp.toPx()
                drawCircle(Gold.copy(alpha = 0.16f), ringR, center, style = Stroke(2.8.dp.toPx()))
                drawArc(
                    color = GoldGlow.copy(alpha = 0.95f),
                    startAngle = -90f,
                    sweepAngle = 360f * ringStart,
                    useCenter = false,
                    topLeft = Offset(center.x - ringR, center.y - ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            pulses.forEach { pulse ->
                val t = pulseTicks[pulse.id] ?: 1f
                drawCircle(
                    color = GoldHi.copy(alpha = (1f - t) * 0.52f * pulse.strength),
                    radius = 10.dp.toPx() + 34.dp.toPx() * t * pulse.strength,
                    center = pulse.origin,
                    style = Stroke(width = (2.4.dp.toPx()) * pulse.strength * (1f - 0.45f * t))
                )
            }
        }
        val seal = maxOf(lockShown, dropShown)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(markSize), contentAlignment = Alignment.Center) {
                if (framed) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Lumen",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .requiredSize(markSize * (118f / 60f))
                            .graphicsLayer { alpha = (1f - seal).coerceIn(0f, 1f) }
                    )
                } else {
                    // Card style: thin glowing gold ring with a gold dot, drawn directly.
                    GoldRing(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (1f - seal).coerceIn(0f, 1f) }
                    )
                }
                Icon(
                    if (lockShown > 0.55f) Icons.Outlined.Fingerprint else Icons.Outlined.Lock,
                    contentDescription = "Locked Space",
                    tint = GoldHi.copy(alpha = seal),
                    modifier = Modifier.size(28.dp)
                )
                if (revealApp != null && reveal > 0.04f && seal < 0.2f) {
                    AppIcon(
                        revealApp.packageName,
                        revealApp.activityName,
                        34.dp,
                        icons,
                        modifier = Modifier.graphicsLayer { alpha = reveal.coerceIn(0f, 1f) }
                    )
                } else if (revealSpec.isNotEmpty() && reveal > 0.04f && seal < 0.2f && revealApp == null) {
                    Icon(
                        systemIcon(revealSpec),
                        contentDescription = null,
                        tint = GoldHi.copy(alpha = reveal.coerceIn(0f, 1f)),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                if (seal > 0.6f) "Private" else "Lumen",
                color = Color.White.copy(alpha = if (pressed) 0.95f else 0.88f),
                fontFamily = Outfit,
                fontWeight = if (framed) FontWeight.Medium else FontWeight.SemiBold,
                fontSize = if (framed) 13.sp else 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                if (seal > 0.6f) "Space" else "TouchPad",
                color = Color.White.copy(alpha = if (framed) 0.52f else 0.72f),
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = if (framed) 10.sp else 13.sp,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun specFor(state: LauncherUiState, kind: String): String = when (kind) {
    "tap" -> state.tapAction
    "double" -> state.doubleAction
    "triple" -> state.tripleAction
    "swipeUp" -> state.swipeUpAction
    "swipeDown" -> state.swipeDownAction
    "swipeLeft" -> state.swipeLeftAction
    "swipeRight" -> state.swipeRightAction
    else -> GestureAction.NONE
}

private fun systemIcon(spec: String) = when (spec) {
    GestureAction.SEARCH -> Icons.Outlined.Search
    GestureAction.VOICE -> Icons.Outlined.MicNone
    GestureAction.DRAWER -> Icons.Outlined.Apps
    GestureAction.LOCK, GestureAction.PRIVATE -> Icons.Outlined.Lock
    GestureAction.NOTIFICATIONS -> Icons.Outlined.Notifications
    else -> Icons.Outlined.Settings
}

private enum class PadHaptic { Tick, Click, Deep, Success }

private fun View.playPadHaptic(mode: String, kind: PadHaptic) {
    if (mode == TouchpadHaptics.OFF) return
    val light = mode == TouchpadHaptics.LIGHT
    val code = when (kind) {
        PadHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        PadHaptic.Click -> if (light) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK
        PadHaptic.Deep -> if (light) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.LONG_PRESS
        PadHaptic.Success -> if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else if (light) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK
    }
    performHapticFeedback(code)
}

private fun DrawScope.drawGoldBezel(energy: Float) {
    val inset = 10.dp.toPx()
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val corner = min(right - left, bottom - top) * 0.30f
    val count = 28
    val base = 0.18f + 0.22f * energy.coerceIn(0f, 1.4f)
    for (i in 0 until count) {
        val point = pointOnRoundRect(i / count.toFloat(), left, top, right, bottom, corner)
        val cardinal = i % (count / 4) == 0
        val radius = if (cardinal) 1.85.dp.toPx() else 1.15.dp.toPx()
        val alpha = if (cardinal) base + 0.16f else base
        drawCircle(GoldHi.copy(alpha = alpha.coerceAtMost(0.62f)), radius, point)
    }
}

private fun pointOnRoundRect(
    t: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    r: Float
): Offset {
    val w = (right - left - 2f * r).coerceAtLeast(0f)
    val h = (bottom - top - 2f * r).coerceAtLeast(0f)
    val arc = (Math.PI.toFloat() / 2f) * r
    val perim = 2f * w + 2f * h + 4f * arc
    var d = ((t % 1f) + 1f) % 1f * perim
    if (d <= w) return Offset(left + r + d, top)
    d -= w
    if (d <= arc) return arcPoint(right - r, top + r, r, -Math.PI / 2, (d / arc).toDouble())
    d -= arc
    if (d <= h) return Offset(right, top + r + d)
    d -= h
    if (d <= arc) return arcPoint(right - r, bottom - r, r, 0.0, (d / arc).toDouble())
    d -= arc
    if (d <= w) return Offset(right - r - d, bottom)
    d -= w
    if (d <= arc) return arcPoint(left + r, bottom - r, r, Math.PI / 2, (d / arc).toDouble())
    d -= arc
    if (d <= h) return Offset(left, bottom - r - d)
    d -= h
    return arcPoint(left + r, top + r, r, Math.PI, (d / arc).coerceIn(0f, 1f).toDouble())
}

private fun arcPoint(cx: Float, cy: Float, r: Float, start: Double, fraction: Double): Offset {
    val a = start + fraction * (Math.PI / 2)
    return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
}

@Composable
fun FloatingPrivateIsland(
    visible: Boolean,
    state: LauncherUiState,
    icons: IconCache,
    dropReady: Boolean,
    onBoundsInWindow: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)) +
            slideInVertically { -it / 2 },
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.86f) + slideOutVertically { -it / 3 },
        modifier = modifier
    ) {
        TouchpadIsland(
            enabled = false,
            state = state,
            icons = icons,
            dropReady = dropReady,
            onGesture = {},
            onPrivateArmed = {},
            onBoundsInWindow = onBoundsInWindow,
            modifier = Modifier.size(168.dp)
        )
    }
}

/** Thin gold ring with a soft glow, a faint outer ring and a gold dot (TouchPad card style). */
@Composable
private fun GoldRing(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.40f
        val gold = Color(0xFFF5CB7E)
        // Faint outer ring
        drawCircle(Color.White.copy(alpha = 0.30f), radius = r * 1.14f, center = c, style = Stroke(1.2.dp.toPx()))
        // Soft inner light
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c
        )
        // Glow, then the crisp ring
        drawCircle(gold.copy(alpha = 0.10f), radius = r, center = c, style = Stroke(10.dp.toPx()))
        drawCircle(gold.copy(alpha = 0.22f), radius = r, center = c, style = Stroke(5.dp.toPx()))
        drawCircle(gold, radius = r, center = c, style = Stroke(2.dp.toPx()))
        // Gold dot on the outer ring, top-right
        val a = Math.toRadians(-48.0)
        val dot = Offset(c.x + (kotlin.math.cos(a) * r * 1.14f).toFloat(), c.y + (kotlin.math.sin(a) * r * 1.14f).toFloat())
        drawCircle(gold.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = dot)
        drawCircle(gold, radius = 5.5.dp.toPx(), center = dot)
    }
}

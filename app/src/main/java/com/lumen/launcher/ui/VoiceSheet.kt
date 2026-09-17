package com.lumen.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.R
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState

private val Gold = Color(0xFFE7C27A)
private val GoldHi = Color(0xFFF8E7C4)
private val Panel = RoundedCornerShape(36.dp)

@Composable
fun VoiceSheet(
    state: LauncherUiState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val submit = {
        val text = draft.trim()
        if (text.isNotBlank()) {
            draft = ""
            onSubmit(text)
        }
    }
    val processing = !state.voiceListening &&
        (state.voiceHint == "On it." || state.voiceHint == "Give me a second.")
    val thinking = processing
    val motion = rememberInfiniteTransition(label = "lumen-listen")
    val breath by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (thinking) 850 else 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val wave by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (thinking) 1050 else 4200, easing = FastOutSlowInEasing)
        ),
        label = "wave"
    )
    val live = if (state.voiceListening) {
        (0.28f + 0.72f * state.voiceLevel).coerceIn(0.28f, 1f)
    } else {
        breath
    }
    val title = when {
        state.voiceListening -> "I'm listening."
        processing && state.voiceHint == "Give me a second." -> "Give me a second."
        processing -> "On it."
        state.voiceHint.isNotBlank() -> state.voiceHint
        else -> "I'm listening."
    }
    val subtitle = when {
        state.voiceHeard.isNotBlank() -> state.voiceHeard
        state.voiceListening -> "Say an app or a command."
        state.voiceCanRetry -> "Tap Lumen to try again."
        processing -> "Working on that."
        else -> state.voiceHint.ifBlank { "Say Hey Lumen anytime." }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.5f to Color.Black.copy(alpha = 0.46f),
                        1f to Color.Black.copy(alpha = 0.62f)
                    )
                )
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 16.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = Panel,
                    spotColor = Lumen.Bloom.copy(alpha = 0.22f),
                    ambientColor = Color.Black.copy(alpha = 0.38f)
                )
                .clip(Panel)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x4AFFFFFF),
                        0.35f to Color(0x33241832),
                        1f to Color(0x6620162A)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.42f),
                        0.5f to GoldHi.copy(alpha = 0.28f),
                        1f to Color.White.copy(alpha = 0.16f)
                    ),
                    shape = Panel
                )
                .clickable(enabled = false) {}
                .padding(top = 28.dp, bottom = 26.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .graphicsLayer {
                        val pulse = if (state.voiceListening) live else breath
                        val range = when {
                            state.voiceListening -> 0.055f
                            thinking -> 0.02f
                            else -> 0.014f
                        }
                        val s = 0.986f + range * pulse
                        rotationZ = if (thinking) 360f * wave else 0f
                        scaleX = s
                        scaleY = s
                    }
                    .clickable(enabled = state.voiceCanRetry, onClick = onRetry),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val orb = 60.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Lumen.AccentDeep.copy(alpha = 0.22f + 0.10f * live),
                                Lumen.Bloom.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = orb * 1.78f
                        ),
                        radius = orb * 1.78f,
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Gold.copy(alpha = 0.16f + 0.10f * live),
                                Gold.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = orb * 1.28f
                        ),
                        radius = orb * 1.28f,
                        center = center
                    )
                    val t = wave
                    val radius = orb + 8.dp.toPx() + 18.dp.toPx() * t
                    val alpha = (1f - t) * 0.18f
                    val stroke = 1.15.dp.toPx()
                    drawArc(
                        color = GoldHi.copy(alpha = alpha),
                        startAngle = 118f,
                        sweepAngle = 124f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = GoldHi.copy(alpha = alpha),
                        startAngle = -62f,
                        sweepAngle = 124f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            spotColor = Lumen.AccentDeep.copy(alpha = 0.38f),
                            ambientColor = Gold.copy(alpha = 0.18f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0x28FFFFFF),
                                    Color(0x24180C28),
                                    Color(0xE60E0816)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                0f to GoldHi.copy(alpha = 0.55f),
                                1f to Lumen.Bloom.copy(alpha = 0.28f)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Lumen",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.requiredSize(236.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                fontSize = 28.sp,
                letterSpacing = (-0.2).sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.62f),
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            0f to GoldHi.copy(alpha = 0.50f),
                            0.45f to Color.White.copy(alpha = 0.16f),
                            1f to Gold.copy(alpha = 0.28f)
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Light,
                        fontSize = 16.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    cursorBrush = SolidColor(GoldHi),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    "Or type a command",
                                    color = Color.White.copy(alpha = 0.38f),
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    }
                )
                val ready = draft.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (ready) {
                                Brush.radialGradient(listOf(GoldHi, Gold))
                            } else {
                                Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            }
                        )
                        .clickable(enabled = ready, onClick = submit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (ready) Color(0xFF2A1810) else Color.White.copy(alpha = 0.28f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

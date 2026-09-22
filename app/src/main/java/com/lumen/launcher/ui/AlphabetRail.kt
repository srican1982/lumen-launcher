package com.lumen.launcher.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lumen.launcher.data.AlphabetIndex
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

/**
 * Slim A–Z rail. Hit target stays ~36dp so list long-press / dock drag still work.
 * Preview bubble is a Popup so it never widens the rail’s touch region.
 */
@Composable
fun AlphabetRail(
    index: AlphabetIndex,
    activeLetter: Char?,
    scrubbing: Boolean,
    space: SpaceKind,
    icons: IconCache,
    onLetter: (Char) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val latestIndex by rememberUpdatedState(index)
    val latestActive by rememberUpdatedState(activeLetter)
    var railHeight by remember { mutableIntStateOf(0) }

    fun pick(y: Float) {
        val height = railHeight.coerceAtLeast(1)
        val fraction = (y / height).coerceIn(0f, 0.999f)
        val raw = latestIndex.letterAt(fraction) ?: return
        val letter = latestIndex.resolve(raw) ?: return
        if (letter != latestActive) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 29) {
                    HapticFeedbackConstants.CLOCK_TICK
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
            )
            onLetter(letter)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(32.dp)
                .semantics {
                    contentDescription = "Alphabet navigator. Drag to jump to apps by letter."
                }
                .onSizeChanged { railHeight = it.height }
                .pointerInput(index.available) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            onScrubbingChange(true)
                            pick(offset.y)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            pick(change.position.y)
                        },
                        onDragEnd = { onScrubbingChange(false) },
                        onDragCancel = { onScrubbingChange(false) }
                    )
                }
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            index.letters.forEach { letter ->
                val present = letter in index.available
                val selected = scrubbing && activeLetter == letter
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (selected) 22.dp else 14.dp)
                        .graphicsLayer {
                            if (selected) {
                                scaleX = 1.35f
                                scaleY = 1.35f
                            }
                        }
                ) {
                    if (selected) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Lumen.Accent.copy(alpha = 0.95f),
                                            Lumen.Accent.copy(alpha = 0.40f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Lumen.Accent.copy(alpha = 0.70f))
                        )
                    }
                    Text(
                        text = letter.toString(),
                        color = when {
                            selected -> Color.White
                            present -> Color.White.copy(alpha = 0.78f)
                            else -> Color.White.copy(alpha = 0.22f)
                        },
                        fontFamily = Outfit,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Light,
                        fontSize = if (selected) 12.sp else 10.sp,
                        modifier = Modifier.semantics {
                            contentDescription = if (present) {
                                "Jump to apps starting with $letter"
                            } else {
                                "No apps starting with $letter"
                            }
                        }
                    )
                }
            }
        }

        if (scrubbing && activeLetter != null) {
            val offsetX = with(density) { -(164.dp).roundToPx() }
            Popup(
                alignment = Alignment.CenterStart,
                offset = IntOffset(offsetX, 0),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false
                )
            ) {
                AlphabetPreviewBubble(
                    visible = true,
                    letter = activeLetter,
                    apps = index.previews[activeLetter].orEmpty(),
                    icons = icons,
                    spaceBadge = activeLetter
                        .takeIf { it in index.spaceBoosted }
                        ?.let { space.title.uppercase() }
                )
            }
        }
    }
}

@Composable
private fun AlphabetPreviewBubble(
    visible: Boolean,
    letter: Char?,
    apps: List<AppInfo>,
    icons: IconCache,
    spaceBadge: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && letter != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(156.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xE62A1840),
                            Color(0xF214061F)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Lumen.Accent.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .semantics {
                    val names = apps.joinToString(", ") { it.label }
                    contentDescription = "Letter $letter. $names"
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Lumen.Accent.copy(alpha = 0.95f),
                                        Lumen.Accent.copy(alpha = 0.40f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Lumen.Accent.copy(alpha = 0.90f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter?.toString().orEmpty(),
                            color = Color.White,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp
                        )
                    }
                }
                if (!spaceBadge.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = spaceBadge,
                        color = Lumen.OnAccent,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Lumen.Accent.copy(alpha = 0.92f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                apps.forEach { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            activityName = app.activityName,
                            size = 28.dp,
                            icons = icons,
                            corner = 8.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = app.label,
                            color = Color.White.copy(alpha = 0.92f),
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Light,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

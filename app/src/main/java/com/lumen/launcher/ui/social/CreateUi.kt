package com.lumen.launcher.ui.social

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.theme.LumenPalette
import com.lumen.launcher.ui.theme.Outfit

/** Colors shared by the Create panel and the Scribble / Quote / Photo screens. */
object CreatePalette {
    private val w: Boolean get() = LumenPalette.whiteGlass

    val Base: Color get() = if (w) Color(0xFF16191E) else Color(0xFF1B1838)
    val Ink: Color get() = if (w) Color(0xFF1F2430) else Color(0xFF2A1048)
    val Label: Color get() = if (w) Color(0xFFC9CFD8) else Color(0xFFB8AEE6)
    val Subtitle: Color get() = if (w) Color(0xFFDDE2E9) else Color(0xFFC9BEF7)
    val Accent: Color get() = if (w) Color(0xFFFFFFFF) else Color(0xFFB9A6F5)
    val ChipFill: Color get() = if (w) Color(0x2EFFFFFF) else Color(0x1FFFFFFF)
    val ChipBorder: Color get() = if (w) Color(0x66FFFFFF) else Color(0x4DC4B5FD)

    private val violetSelected = Brush.horizontalGradient(listOf(Color(0xFFC7B2FA), Color(0xFFF0D6FA)))
    private val whiteSelected = Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE9EDF2)))
    private val violetShare = Brush.horizontalGradient(listOf(Color(0xFFA78BFA), Color(0xFFE7C6FA)))
    private val whiteShare = Brush.horizontalGradient(listOf(Color(0xFFF4F6F9), Color(0xFFFFFFFF)))

    val SelectedFill: Brush get() = if (w) whiteSelected else violetSelected
    val ShareFill: Brush get() = if (w) whiteShare else violetShare
}

/** Screen background for the Create screens; follows Settings → Theme. */
fun DrawScope.createScreenBackground() {
    if (LumenPalette.whiteGlass) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF3A414B),
                0.45f to Color(0xFF22272E),
                1f to Color(0xFF111317)
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0x40FFFFFF), Color(0x0DFFFFFF), Color.Transparent),
                center = Offset(size.width * 0.3f, -size.width * 0.1f),
                radius = size.width * 1.1f
            )
        )
        return
    }
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF36306E),
            0.45f to Color(0xFF221D4A),
            1f to Color(0xFF120F28)
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0x668B7CF6), Color(0x1A8B7CF6), Color.Transparent),
            center = Offset(size.width * 0.3f, -size.width * 0.1f),
            radius = size.width * 1.1f
        )
    )
}

/** Round close button · title (+ optional subtitle) · round share button. */
@Composable
fun CreateToolHeader(
    title: String,
    onClose: () -> Unit,
    onShare: () -> Unit,
    subtitle: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(
            onClick = onClose,
            fill = Color(0x1AFFFFFF),
            border = Color(0x2EFFFFFF)
        ) {
            Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(title, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 24.sp)
            if (subtitle != null) {
                Text(subtitle, color = CreatePalette.Subtitle, fontFamily = Outfit, fontSize = 14.sp)
            }
        }
        RoundIconButton(
            onClick = onShare,
            fill = Color(0x553A1C63),
            border = Color(0xAA9D63EE)
        ) {
            Icon(Icons.Outlined.Share, "Share", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun RoundIconButton(
    onClick: () -> Unit,
    fill: Color,
    border: Color,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Small uppercase section label: STYLE, BACKGROUND, SIZE, TOOLS. */
@Composable
fun CreateSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = CreatePalette.Label,
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        modifier = modifier.padding(top = 18.dp, bottom = 10.dp)
    )
}

/** Rounded pill chip. Selected = lilac→pink gradient with dark text. */
@Composable
fun CreateChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .then(if (selected) Modifier.shadow(10.dp, shape, spotColor = Color(0xFFC99AF2)) else Modifier)
            .clip(shape)
            .then(
                if (selected) Modifier.background(CreatePalette.SelectedFill)
                else Modifier
                    .background(CreatePalette.ChipFill)
                    .border(1.dp, CreatePalette.ChipBorder, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leading != null) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = if (selected) CreatePalette.Ink else Color.White.copy(alpha = 0.88f),
            fontFamily = Outfit,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            fontSize = 15.sp,
            maxLines = 1
        )
    }
}

/** Tint to use for a chip's leading icon so it reads on both states. */
fun chipIconTint(selected: Boolean): Color = if (selected) CreatePalette.Ink else Color.White.copy(alpha = 0.9f)

/**
 * Bottom bar. Two buttons ("Save to Gallery" + "Share"), or three when [onSticker] is given:
 * "Save" · "Sticker" (adds to Lumen Stickers) · "Share".
 */
@Composable
fun CreateActionBar(
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onSticker: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(30.dp)
    val compact = onSticker != null
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(58.dp)
                .clip(shape)
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x40A36BF0), shape)
                .clickable(onClick = onSave),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.FileDownload, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (compact) "Save" else "Save to Gallery", color = Color.White, fontFamily = Outfit, fontSize = 16.sp, maxLines = 1)
        }
        if (onSticker != null) {
            Row(
                Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(shape)
                    .background(Color(0x333A1C63))
                    .border(1.5.dp, CreatePalette.Accent, shape)
                    .clickable(onClick = onSticker),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(CreateIcons.Sticker, null, tint = CreatePalette.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sticker", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1)
            }
        }
        Row(
            Modifier
                .weight(1f)
                .height(58.dp)
                .shadow(16.dp, shape, spotColor = Color(0xFFB57BF5))
                .clip(shape)
                .background(CreatePalette.ShareFill)
                .clickable(onClick = onShare),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.FileUpload, null, tint = CreatePalette.Ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share", color = CreatePalette.Ink, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1)
        }
    }
}

/** Color swatch with a ring when selected. */
@Composable
fun ColorSwatch(color: Color, selected: Boolean, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, CreatePalette.Accent, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}

/** Grey checkerboard used to show "no background". */
fun DrawScope.checkerboard(cell: Float = 12.dp.toPx(), light: Color = Color(0xFF3A3346), dark: Color = Color(0xFF2A2436)) {
    drawRect(dark)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(light, topLeft = Offset(x, y), size = Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

/** Tiny checkerboard swatch for the "Clear" background chip. */
@Composable
fun CheckerSwatch() {
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .drawBehind { checkerboard(cell = 5.dp.toPx(), light = Color(0xFFE9E3F5), dark = Color(0xFF7D6C9A)) }
    )
}

/** Small sky-with-cloud swatch for the "Sky" background chip. */
@Composable
fun SkySwatch() {
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3FA2F2), Color(0xFF9AD7FF))))
            .drawBehind {
                drawCircle(Color.White, radius = size.width * 0.22f, center = Offset(size.width * 0.38f, size.height * 0.72f))
                drawCircle(Color.White, radius = size.width * 0.26f, center = Offset(size.width * 0.62f, size.height * 0.66f))
            }
    )
}

/** Solid dot, e.g. the purple background chip. */
@Composable
fun DotSwatch(color: Color) {
    Box(Modifier.size(18.dp).clip(CircleShape).background(color))
}

/** Glass orb for the Glass quote style chip. */
@Composable
fun GlassOrb() {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFE9DDFF), Color(0xFF6D4BB8), Color(0xFF1B1030))))
            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
    )
}

/** Outline rectangle in a given aspect, used for the 1:1 / 9:16 / 16:9 chips. */
@Composable
fun AspectGlyph(widthRatio: Float, heightRatio: Float, color: Color) {
    val max = 20f
    val scale = max / maxOf(widthRatio, heightRatio)
    Box(
        Modifier
            .size((widthRatio * scale).dp, (heightRatio * scale).dp)
            .border(2.dp, color, RoundedCornerShape(3.dp))
    )
}

/** Custom icons not in the Material set. Stroke-only, tinted by Icon(). */
object CreateIcons {
    val Eraser: ImageVector by lazy {
        ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 4f)
                lineTo(20.5f, 9.5f)
                lineTo(11.5f, 18.5f)
                lineTo(7f, 18.5f)
                lineTo(3.5f, 15f)
                close()
                moveTo(9.5f, 9.5f)
                lineTo(15f, 15f)
                moveTo(11.5f, 18.5f)
                lineTo(20.5f, 18.5f)
            }
        }.build()
    }

    val Sticker: ImageVector by lazy {
        ImageVector.Builder("Sticker", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 4f)
                lineTo(14f, 4f)
                lineTo(20f, 10f)
                lineTo(20f, 17f)
                quadTo(20f, 20f, 17f, 20f)
                lineTo(7f, 20f)
                quadTo(4f, 20f, 4f, 17f)
                lineTo(4f, 7f)
                quadTo(4f, 4f, 7f, 4f)
                close()
                moveTo(14f, 4f)
                lineTo(14f, 8f)
                quadTo(14f, 10f, 16f, 10f)
                lineTo(20f, 10f)
            }
        }.build()
    }

    /** Wavy line for the Sketch brush style. */
    val Squiggle: ImageVector by lazy {
        ImageVector.Builder("Squiggle", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 14f)
                quadTo(5.5f, 8f, 8f, 12f)
                quadTo(10.5f, 16f, 13f, 12f)
                quadTo(15.5f, 8f, 18f, 12f)
                quadTo(19.5f, 14.5f, 21f, 12f)
            }
        }.build()
    }
}

/** Small text action: "＋ Add to Lumen Stickers" (goes into the WhatsApp sticker pack). */
@Composable
fun AddToStickersLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(CreateIcons.Sticker, null, tint = CreatePalette.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add to Lumen Stickers", color = CreatePalette.Accent, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/**
 * Thin slider bar for fingers: tap or drag anywhere on it. [value] is 0..1
 * (vertical: 0 = top). Snaps to [snapTo] with a small haptic tick.
 * Uses one-direction drag detection so it doesn't fight page scrolling.
 */
@Composable
fun DragBar(
    value: Float,
    onValue: (Float) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    snapTo: Float? = 0.5f
) {
    val view = LocalView.current
    val current by rememberUpdatedState(value)
    val emit by rememberUpdatedState(onValue)
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    fun pick(pos: Offset) {
        val len = if (vertical) sizePx.height else sizePx.width
        if (len <= 0) return
        var f = ((if (vertical) pos.y else pos.x) / len).coerceIn(0f, 1f)
        if (snapTo != null && abs(f - snapTo) < 0.03f) {
            if (current != snapTo) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            f = snapTo
        }
        emit(f)
    }

    Box(
        modifier
            .onSizeChanged { sizePx = it }
            .pointerInput(vertical) { detectTapGestures(onTap = { pick(it) }) }
            .pointerInput(vertical) {
                if (vertical) {
                    detectVerticalDragGestures(onDragStart = { pick(it) }) { change, _ ->
                        change.consume()
                        pick(change.position)
                    }
                } else {
                    detectHorizontalDragGestures(onDragStart = { pick(it) }) { change, _ ->
                        change.consume()
                        pick(change.position)
                    }
                }
            }
            .drawBehind {
                val thick = 4.dp.toPx()
                val thumbR = 11.dp.toPx()
                val pad = thumbR + 2.dp.toPx()
                val track = Color.White.copy(alpha = 0.18f)
                val v = current.coerceIn(0f, 1f)
                if (vertical) {
                    val x = size.width / 2f
                    drawLine(track, Offset(x, pad), Offset(x, size.height - pad), strokeWidth = thick, cap = StrokeCap.Round)
                    if (snapTo != null) {
                        val cy = pad + (size.height - 2 * pad) * snapTo
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(x - 6.dp.toPx(), cy), Offset(x + 6.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
                    }
                    val ty = pad + (size.height - 2 * pad) * v
                    drawCircle(CreatePalette.Accent.copy(alpha = 0.35f), radius = thumbR + 5.dp.toPx(), center = Offset(x, ty))
                    drawCircle(Color.White, radius = thumbR, center = Offset(x, ty))
                    drawCircle(CreatePalette.Accent, radius = thumbR, center = Offset(x, ty), style = Stroke(2.dp.toPx()))
                } else {
                    val y = size.height / 2f
                    drawLine(track, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = thick, cap = StrokeCap.Round)
                    if (snapTo != null) {
                        val cx = pad + (size.width - 2 * pad) * snapTo
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(cx, y - 6.dp.toPx()), Offset(cx, y + 6.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                    }
                    val tx = pad + (size.width - 2 * pad) * v
                    drawCircle(CreatePalette.Accent.copy(alpha = 0.35f), radius = thumbR + 5.dp.toPx(), center = Offset(tx, y))
                    drawCircle(Color.White, radius = thumbR, center = Offset(tx, y))
                    drawCircle(CreatePalette.Accent, radius = thumbR, center = Offset(tx, y), style = Stroke(2.dp.toPx()))
                }
            }
    )
}

/** A horizontal [DragBar] with a label on the left and the current value on the right. */
@Composable
fun LabeledDragBar(
    label: String,
    valueText: String,
    value: Float,
    onValue: (Float) -> Unit,
    modifier: Modifier = Modifier,
    snapTo: Float? = 0.5f
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CreatePalette.Label, fontFamily = Outfit, fontSize = 14.sp, modifier = Modifier.width(64.dp))
        DragBar(
            value = value,
            onValue = onValue,
            vertical = false,
            snapTo = snapTo,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        )
        Text(
            valueText,
            color = Color.White,
            fontFamily = Outfit,
            fontSize = 14.sp,
            modifier = Modifier
                .width(52.dp)
                .padding(start = 8.dp)
        )
    }
}

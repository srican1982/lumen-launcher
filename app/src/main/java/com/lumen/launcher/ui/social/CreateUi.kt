package com.lumen.launcher.ui.social

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
import com.lumen.launcher.ui.theme.Outfit

/** Colors shared by the Create panel and the Scribble / Quote / Photo screens. */
object CreatePalette {
    val Base = Color(0xFF0D0716)
    val Ink = Color(0xFF2A1048)
    val Label = Color(0xFFA08BC2)
    val Subtitle = Color(0xFFB9A2E6)
    val Accent = Color(0xFFB57BF5)
    val ChipFill = Color(0x17FFFFFF)
    val ChipBorder = Color(0x2EC7A6F5)
    val SelectedFill = Brush.horizontalGradient(listOf(Color(0xFFC99AF2), Color(0xFFF2CFF7)))
    val ShareFill = Brush.horizontalGradient(listOf(Color(0xFFB57BF5), Color(0xFFE9BEF7)))
}

/** Deep purple screen background with a soft glow at the top, as in the mockups. */
fun DrawScope.createScreenBackground() {
        drawRect(CreatePalette.Base)
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0xAA4A1F8A), Color(0x334A1F8A), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.width * 0.15f),
                radius = size.width * 1.05f
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

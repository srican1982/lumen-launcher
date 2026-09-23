package com.lumen.launcher.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.social.scribble.ScribbleBrushStyle
import com.lumen.launcher.social.scribble.ScribbleExport
import com.lumen.launcher.social.scribble.ScribbleExportBackground
import com.lumen.launcher.social.scribble.InkStroke
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

@Composable
fun ScribbleExportSheet(
    strokes: List<InkStroke>,
    initialStyle: ScribbleBrushStyle = ScribbleBrushStyle.Sketch,
    onShare: (android.graphics.Bitmap) -> Unit,
    onSticker: (android.graphics.Bitmap) -> Unit = {},
    onDismiss: () -> Unit
) {
    val styles = ScribbleBrushStyle.entries
    val backgrounds = ScribbleExportBackground.entries
    var styleIdx by remember { mutableIntStateOf(styles.indexOf(initialStyle).coerceAtLeast(0)) }
    var bgIdx by remember { mutableIntStateOf(0) }
    val style = styles[styleIdx]
    val bg = backgrounds[bgIdx]

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .background(Color(0xFF1A1028), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            .padding(18.dp)
    ) {
        Text("Preview", color = Lumen.Text, fontFamily = Outfit, fontSize = 18.sp)
        val preview = remember(strokes.size, style, bg) {
            ScribbleExport.render(strokes, style, bg).asImageBitmap()
        }
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (bg == ScribbleExportBackground.Transparent) Modifier.drawBehind { checkerboard() }
                    else Modifier.background(Color.White.copy(alpha = 0.05f))
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = preview,
                contentDescription = "Preview of your scribble",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        }
        Text("Style", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            styles.forEachIndexed { i, s ->
                Chip(s.name, i == styleIdx) { styleIdx = i }
            }
        }
        Text("Background", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            backgrounds.forEachIndexed { i, b ->
                Chip(backgroundLabel(b), i == bgIdx) { bgIdx = i }
            }
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Cancel", false, modifier = Modifier.weight(1f)) { onDismiss() }
            Chip("Sticker", false, modifier = Modifier.weight(1f)) {
                // Stickers always render without a background; the cut-out border is added later.
                val bmp = ScribbleExport.render(strokes, style, ScribbleExportBackground.Transparent, sticker = true)
                onSticker(bmp)
            }
            Chip("Share", true, modifier = Modifier.weight(1f)) {
                val bmp = ScribbleExport.render(strokes, style, bg)
                onShare(bmp)
            }
        }
        Text(
            "Sticker = no background with a cut-out border. Works as a sticker in Telegram, Signal and Discord; " +
                "in WhatsApp open it and tap ⋮ → Create sticker.",
            color = Lumen.Faint,
            fontFamily = Outfit,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        label,
        color = if (selected) Lumen.OnAccent else Lumen.Text,
        fontFamily = Outfit,
        fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Lumen.Accent else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private fun backgroundLabel(b: ScribbleExportBackground): String = when (b) {
    ScribbleExportBackground.Transparent -> "Clear"
    ScribbleExportBackground.LumenGradient -> "Gradient"
    else -> b.name
}

/** Grey checkerboard so a transparent export visibly reads as "no background". */
private fun DrawScope.checkerboard() {
    val cell = 12.dp.toPx()
    val light = Color(0xFF3A3346)
    val dark = Color(0xFF2A2436)
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

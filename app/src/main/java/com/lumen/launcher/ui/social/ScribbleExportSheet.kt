package com.lumen.launcher.ui.social

import androidx.compose.foundation.background
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
    onShare: (android.graphics.Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var styleIdx by remember { mutableIntStateOf(0) }
    var bgIdx by remember { mutableIntStateOf(0) }
    val styles = ScribbleBrushStyle.entries
    val backgrounds = ScribbleExportBackground.entries
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
        Text("Style", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            styles.forEachIndexed { i, s ->
                Chip(s.name, i == styleIdx) { styleIdx = i }
            }
        }
        Text("Background", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            backgrounds.forEachIndexed { i, b ->
                Chip(b.name.replace(Regex("([a-z])([A-Z])"), "$1 $2"), i == bgIdx) { bgIdx = i }
            }
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Cancel", false, modifier = Modifier.weight(1f)) { onDismiss() }
            Chip("Share", true, modifier = Modifier.weight(1f)) {
                val bmp = ScribbleExport.render(strokes, style, bg)
                onShare(bmp)
            }
        }
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

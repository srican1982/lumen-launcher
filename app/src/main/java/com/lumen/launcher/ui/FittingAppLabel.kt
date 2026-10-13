package com.lumen.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.theme.Outfit

/** Prefer the complete app name; truncate only once shrinking would make it unreadable. */
@Composable
fun FittingAppLabel(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified, fontSize: TextUnit = 12.sp, fontFamily: FontFamily = Outfit,
    maxLines: Int = 1, overflow: TextOverflow = TextOverflow.Ellipsis, textAlign: TextAlign = TextAlign.Center) {
    val measurer = rememberTextMeasurer()
    val base = if (style.fontSize == TextUnit.Unspecified) fontSize else style.fontSize
    val effective = style.copy(fontFamily = fontFamily, fontSize = base)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = measurer.measure(text, effective, softWrap = false).size.width.coerceAtLeast(1)
        val fitted = (base.value * constraints.maxWidth / width * .98f).coerceIn(minOf(8f, base.value), base.value)
        Text(text, color = color, style = effective.copy(fontSize = fitted.sp),
            maxLines = 1, softWrap = false, overflow = overflow, textAlign = textAlign,
            modifier = Modifier.fillMaxWidth())
    }
}

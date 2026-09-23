package com.lumen.launcher.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.social.quote.QuoteAspect
import com.lumen.launcher.social.quote.QuoteBackgroundKind
import com.lumen.launcher.social.quote.QuoteStyle
import com.lumen.launcher.social.quote.QuoteStyleRenderer
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

@Composable
fun QuoteLivePreview(
    text: String,
    style: QuoteStyle,
    background: QuoteBackgroundKind,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preview = remember(text, style, background) {
        QuoteStyleRenderer.renderPreview(
            context = context,
            text = text.ifBlank { "Hello" },
            style = style,
            background = background
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
    ) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "Quote preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun QuoteStylePicker(
    selected: QuoteStyle,
    onSelect: (QuoteStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuoteStyle.entries.forEach { style ->
            val on = style == selected
            Text(
                style.name,
                color = if (on) Lumen.OnAccent else Lumen.Text,
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Lumen.Accent else Color.White.copy(alpha = 0.1f))
                    .clickable { onSelect(style) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun QuoteBackgroundPicker(
    selected: QuoteBackgroundKind,
    onSelect: (QuoteBackgroundKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = mapOf(
        QuoteBackgroundKind.SocialBlue to "Sky",
        QuoteBackgroundKind.PurpleGradient to "Purple",
        QuoteBackgroundKind.LumenDark to "Dark",
        QuoteBackgroundKind.Light to "Light",
        QuoteBackgroundKind.Transparent to "Clear"
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { (kind, label) ->
            val on = kind == selected
            Text(
                label,
                color = if (on) Lumen.OnAccent else Lumen.Text,
                fontFamily = Outfit,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Lumen.Accent else Color.White.copy(alpha = 0.1f))
                    .clickable { onSelect(kind) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
fun QuoteAspectPicker(
    selected: QuoteAspect,
    onSelect: (QuoteAspect) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuoteAspect.entries.forEach { aspect ->
            val on = aspect == selected
            Text(
                when (aspect) {
                    QuoteAspect.Square -> "1:1"
                    QuoteAspect.Story -> "9:16"
                    QuoteAspect.Landscape -> "16:9"
                },
                color = if (on) Lumen.OnAccent else Lumen.Text,
                fontFamily = Outfit,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Lumen.Accent else Color.White.copy(alpha = 0.1f))
                    .clickable { onSelect(aspect) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}

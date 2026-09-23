package com.lumen.launcher.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.social.scribble.InkStroke
import com.lumen.launcher.social.scribble.ScribbleBrushStyle
import com.lumen.launcher.social.scribble.ScribbleExport
import com.lumen.launcher.social.scribble.ScribbleExportBackground
import com.lumen.launcher.ui.theme.Outfit

/**
 * Opens from Share on the Scribble screen: live preview, style + background,
 * then Sticker (no background, die-cut border) or Share (image).
 */
@Composable
fun ScribbleExportSheet(
    strokes: List<InkStroke>,
    initialStyle: ScribbleBrushStyle = ScribbleBrushStyle.Sketch,
    onShare: (android.graphics.Bitmap) -> Unit,
    onSticker: (android.graphics.Bitmap) -> Unit = {},
    onSaveSticker: (android.graphics.Bitmap) -> Unit = {},
    onAddToPack: (android.graphics.Bitmap) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSaveImage: (android.graphics.Bitmap) -> Unit = {},
    onDismiss: () -> Unit
) {
    var style by remember { mutableStateOf(initialStyle) }
    // Dark by default: a transparent image turns black in WhatsApp; Sticker covers "no background".
    var bg by remember { mutableStateOf(ScribbleExportBackground.Dark) }
    val shape = RoundedCornerShape(32.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .shadow(24.dp, shape, spotColor = Color(0xFF6D28D9))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF24133F), Color(0xFF140B24))))
            .border(1.dp, Color(0x559D63EE), shape)
            .clickable(enabled = true, onClick = {}) // swallow taps so the scrim doesn't close it
            .padding(18.dp)
    ) {
        Text("Share scribble", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 20.sp)
        val preview = remember(strokes.size, style, bg) {
            ScribbleExport.render(strokes, style, bg).asImageBitmap()
        }
        Box(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(22.dp))
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }

        CreateSectionLabel("Style")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScribbleBrushStyle.entries.forEach { s ->
                CreateChip(s.name, style == s, leading = { BrushStyleIcon(s, style == s) }) { style = s }
            }
        }

        CreateSectionLabel("Background")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScribbleExportBackground.entries.forEach { b ->
                val sel = bg == b
                CreateChip(
                    label = when (b) {
                        ScribbleExportBackground.Transparent -> "Clear"
                        ScribbleExportBackground.LumenGradient -> "Gradient"
                        else -> b.name
                    },
                    selected = sel,
                    leading = {
                        when (b) {
                            ScribbleExportBackground.Transparent -> CheckerSwatch()
                            ScribbleExportBackground.Light -> Icon(Icons.Outlined.WbSunny, null, tint = chipIconTint(sel), modifier = Modifier.size(20.dp))
                            ScribbleExportBackground.Dark -> Icon(Icons.Outlined.DarkMode, null, tint = Color(0xFFF7B267), modifier = Modifier.size(20.dp))
                            ScribbleExportBackground.LumenGradient -> DotSwatch(Color(0xFF8B5CF6))
                        }
                    }
                ) { bg = b }
            }
        }

        val btnShape = RoundedCornerShape(28.dp)
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(btnShape)
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x40A36BF0), btnShape)
                    .clickable {
                        onSticker(ScribbleExport.render(strokes, style, ScribbleExportBackground.Transparent, sticker = true))
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(CreateIcons.Sticker, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sticker", color = Color.White, fontFamily = Outfit, fontSize = 16.sp)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .shadow(14.dp, btnShape, spotColor = Color(0xFFB57BF5))
                    .clip(btnShape)
                    .background(CreatePalette.ShareFill)
                    .clickable { onShare(ScribbleExport.render(strokes, style, bg)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.FileUpload, null, tint = CreatePalette.Ink, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share", color = CreatePalette.Ink, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }
        Row(
            Modifier
                .padding(top = 12.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    onSaveSticker(ScribbleExport.render(strokes, style, ScribbleExportBackground.Transparent, sticker = true))
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.FileDownload, null, tint = CreatePalette.Subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save sticker to Gallery", color = CreatePalette.Subtitle, fontFamily = Outfit, fontSize = 13.sp)
        }
        AddToStickersLink(
            onClick = {
                onAddToPack(ScribbleExport.render(strokes, style, ScribbleExportBackground.Transparent, sticker = true))
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "Cancel",
            color = Color.White.copy(alpha = 0.55f),
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Icon for a brush style chip: squiggle / pen / sparkle. */
@Composable
fun BrushStyleIcon(style: ScribbleBrushStyle, selected: Boolean) {
    val tint = chipIconTint(selected)
    when (style) {
        ScribbleBrushStyle.Sketch -> Icon(CreateIcons.Squiggle, null, tint = tint, modifier = Modifier.size(22.dp))
        ScribbleBrushStyle.Marker -> Icon(Icons.Outlined.Edit, null, tint = tint, modifier = Modifier.size(20.dp))
        ScribbleBrushStyle.Glow -> Icon(Icons.Outlined.AutoAwesome, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

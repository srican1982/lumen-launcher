package com.lumen.launcher.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.social.CreationItem
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.social.ShareContentManager
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import androidx.compose.ui.platform.LocalContext
import java.io.File

private val PanelShape = RoundedCornerShape(36.dp)
private val ToolShape = RoundedCornerShape(22.dp)

@Composable
fun BoxScope.SocialCreatePanel(
    open: Boolean,
    creations: List<CreationItem>,
    activeSpace: SpaceKind,
    shareManager: ShareContentManager,
    onDismiss: () -> Unit,
    onOpenTool: (SocialCreateTool) -> Unit
) {
    val accent = if (activeSpace == SpaceKind.Personal) {
        Color(0xFFB794F4)
    } else {
        Lumen.Accent.copy(alpha = 0.85f)
    }
    var dragX by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current

    AnimatedVisibility(
        visible = open,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss)
        )
    }
    AnimatedVisibility(
        visible = open,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(132.dp)
            .zIndex(3f)
            .pointerInput(open) {
                if (!open) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX < -72f) onDismiss()
                        dragX = 0f
                    },
                    onHorizontalDrag = { _, delta -> dragX += delta }
                )
            },
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { -it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 10.dp, bottom = 16.dp)
                .shadow(20.dp, PanelShape, spotColor = Color(0x66000000))
                .clip(PanelShape)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xE6251838),
                        0.5f to Color(0xD0181028),
                        1f to Color(0xCC12081C)
                    )
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.45f),
                        1f to Color.White.copy(alpha = 0.12f)
                    ),
                    PanelShape
                )
                .padding(horizontal = 14.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Create",
                color = Color.White.copy(alpha = 0.88f),
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(18.dp))
            CreateToolButton("Scribble", Icons.Outlined.Brush, accent) { onOpenTool(SocialCreateTool.Scribble) }
            Spacer(Modifier.height(10.dp))
            CreateToolButton("Quote", Icons.Outlined.FormatQuote, accent) { onOpenTool(SocialCreateTool.Quote) }
            Spacer(Modifier.height(10.dp))
            CreateToolButton("Photo", Icons.Outlined.Image, accent) { onOpenTool(SocialCreateTool.Photo) }
            Spacer(Modifier.height(22.dp))
            Text(
                "Recent",
                color = Color.White.copy(alpha = 0.45f),
                fontFamily = Outfit,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                creations.take(4).forEach { item ->
                    RecentCreationThumb(
                        item = item,
                        onShare = {
                            val file = File(item.filePath)
                            shareManager.shareImage(file)
                        },
                        onLongPress = {
                            val file = File(item.filePath)
                            shareManager.startDrag(view, file, item.kind.name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ToolShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.6.dp, accent.copy(alpha = 0.35f), ToolShape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(22.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.78f),
            fontFamily = Outfit,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCreationThumb(
    item: CreationItem,
    onShare: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(File(item.filePath))
            .build()
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .combinedClickable(
                onClick = onShare,
                onLongClick = onLongPress
            )
    ) {
        Image(
            painter = painter,
            contentDescription = item.kind.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

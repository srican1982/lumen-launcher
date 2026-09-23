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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.social.CreationItem
import com.lumen.launcher.social.ShareContentManager
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.social.stickers.StickerLibrary
import com.lumen.launcher.ui.theme.Outfit
import java.io.File

private val PanelShape = RoundedCornerShape(40.dp)
private val CardShape = RoundedCornerShape(22.dp)
private val TileShape = RoundedCornerShape(18.dp)

/** Lilac used for the header sparkle, "See all" and the panel edge. */
private val Lilac = Color(0xFFC4B5FD)

@Composable
fun SocialCreatePanel(
    open: Boolean,
    creations: List<CreationItem>,
    @Suppress("UNUSED_PARAMETER") activeSpace: SpaceKind,
    shareManager: ShareContentManager,
    onDismiss: () -> Unit,
    onOpenTool: (SocialCreateTool) -> Unit
) {
    if (!open) return
    var dragX by remember { mutableFloatStateOf(0f) }
    var showAll by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    val stickerPacks by StickerLibrary.packs.collectAsState()
    val stickerCount = stickerPacks.sumOf { it.stickers.size }
    val view = LocalView.current
    val blockTouches = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(180f)
    ) {
        // Dim the home screen; tap outside the panel to close.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        AnimatedVisibility(
            visible = true,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(200.dp)
                .pointerInput(Unit) {
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
                    .padding(start = 10.dp, top = 8.dp, bottom = 10.dp)
                    .shadow(30.dp, PanelShape, spotColor = Color(0xFF8B7CF6), ambientColor = Color(0xFF8B7CF6))
                    .clip(PanelShape)
                    // Lavender frosted glass
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xEB4A4586),
                            0.45f to Color(0xEB33306A),
                            1f to Color(0xF0221F4A)
                        )
                    )
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x40FFFFFF), Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = 520f
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(listOf(Color(0xCCD8CCFF), Color(0x66A78BFA), Color(0xAAC4B5FD))),
                        PanelShape
                    )
                    .clickable(interactionSource = blockTouches, indication = null, onClick = {})
                    .padding(horizontal = 12.dp)
            ) {
                // Drag handle
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.40f))
                )
                // Header: sparkle · Create · Create & share.
                Row(
                    Modifier.padding(top = 18.dp, bottom = 16.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Lilac, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Create", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 28.sp)
                        Text("Create & share.", color = Color.White.copy(alpha = 0.78f), fontFamily = Outfit, fontSize = 14.sp, maxLines = 1)
                    }
                }

                if (!showAll) {
                    CreateToolCard(
                        title = "Scribble",
                        subtitle = "Turn your ideas into something.",
                        icon = CreateIcons.Squiggle,
                        gradient = listOf(Color(0xFF7C4DDF), Color(0xFF9A7FE3), Color(0xFF6F6FD8))
                    ) { onOpenTool(SocialCreateTool.Scribble) }
                    Spacer(Modifier.height(12.dp))
                    CreateToolCard(
                        title = "Quote",
                        subtitle = "Capture thoughts that matter.",
                        icon = Icons.Filled.FormatQuote,
                        gradient = listOf(Color(0xFF2563EB), Color(0xFF3B82D6), Color(0xFF56B5E0))
                    ) { onOpenTool(SocialCreateTool.Quote) }
                    Spacer(Modifier.height(12.dp))
                    CreateToolCard(
                        title = "Photo",
                        subtitle = "Add a personal touch.",
                        icon = Icons.Outlined.Image,
                        gradient = listOf(Color(0xFFC98B3A), Color(0xFFA77356), Color(0xFF7E6A8A))
                    ) { onOpenTool(SocialCreateTool.Photo) }
                    Spacer(Modifier.height(12.dp))
                    CreateToolCard(
                        title = "Lumen Stickers",
                        subtitle = if (stickerCount == 1) "1 sticker" else "$stickerCount stickers",
                        icon = CreateIcons.Sticker,
                        gradient = listOf(Color(0x33FFFFFF), Color(0x1FFFFFFF), Color(0x26FFFFFF))
                    ) { showStickers = true }

                    Box(
                        Modifier
                            .padding(top = 18.dp, bottom = 14.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                }

                // Recent + See all / Back
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showAll) "All creations" else "Recent",
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (creations.size > 4 || showAll) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showAll = !showAll }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(if (showAll) "Back" else "See all", color = Lilac, fontFamily = Outfit, fontSize = 14.sp)
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Lilac, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 2-column grid of recent creations
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp)
                ) {
                    if (creations.isEmpty()) {
                        Text(
                            "Your scribbles, quotes and photos will show up here.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontFamily = Outfit,
                            fontSize = 12.sp
                        )
                    }
                    val shown = if (showAll) creations else creations.take(4)
                    shown.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { item ->
                                RecentCreationTile(
                                    item = item,
                                    modifier = Modifier.weight(1f),
                                    onShare = { shareManager.shareImage(File(item.filePath)) },
                                    onLongPress = { shareManager.startDrag(view, File(item.filePath), item.kind.name) }
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (showStickers) {
            StickerPackScreen(onClose = { showStickers = false })
        }
    }
}

/** Glossy gradient card: icon on the left, title + two-line description, chevron. */
@Composable
private fun CreateToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(86.dp)
            .shadow(12.dp, CardShape, spotColor = gradient[1], ambientColor = gradient[1])
            .clip(CardShape)
            .background(Brush.linearGradient(gradient))
            // Glass sheen across the top
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Transparent))
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.14f))),
                CardShape
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, lineHeight = 19.sp)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = Outfit,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Square photo-style tile; tap to share, long-press to drag. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCreationTile(
    item: CreationItem,
    modifier: Modifier,
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
        modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .background(Color(0x33FFFFFF))
            .border(1.dp, Color.White.copy(alpha = 0.22f), TileShape)
            .combinedClickable(onClick = onShare, onLongClick = onLongPress)
    ) {
        Image(
            painter = painter,
            contentDescription = item.kind.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

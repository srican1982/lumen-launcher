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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Gesture
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
import com.lumen.launcher.social.CreationKind
import com.lumen.launcher.social.ShareContentManager
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.social.stickers.StickerLibrary
import com.lumen.launcher.ui.theme.Outfit
import java.io.File

private val PanelShape = RoundedCornerShape(36.dp)
private val CardShape = RoundedCornerShape(24.dp)

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
        // Dim the home screen behind the panel; tap outside to close.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
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
                .width(196.dp)
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
                    .padding(start = 10.dp, top = 6.dp, bottom = 12.dp)
                    .shadow(28.dp, PanelShape, spotColor = Color(0xFF6D28D9))
                    .clip(PanelShape)
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xF52B1650),
                            0.5f to Color(0xF51A0F30),
                            1f to Color(0xF5110A1F)
                        )
                    )
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x557C3AED), Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = 700f
                        )
                    )
                    .border(
                        1.2.dp,
                        Brush.verticalGradient(listOf(Color(0xCC8B5CF6), Color(0x338B5CF6), Color(0x66A78BFA))),
                        PanelShape
                    )
                    .clickable(interactionSource = blockTouches, indication = null, onClick = {})
                    .padding(horizontal = 14.dp)
            ) {
                // Drag handle
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )
                // Header
                Row(
                    Modifier.padding(top = 18.dp, bottom = 16.dp, start = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        null,
                        tint = Color(0xFF9F67F5),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Create",
                            color = Color.White,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Medium,
                            fontSize = 22.sp
                        )
                        Text(
                            "Make something. Share it.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontFamily = Outfit,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                if (!showAll) {
                    CreateToolCard(
                        title = "Scribble",
                        subtitle = "Draw & share",
                        icon = Icons.Outlined.Gesture,
                        gradient = listOf(Color(0xFF8E3CF7), Color(0xFFB45CF0), Color(0xFF6B55C9))
                    ) { onOpenTool(SocialCreateTool.Scribble) }
                    Spacer(Modifier.height(12.dp))
                    CreateToolCard(
                        title = "Quote",
                        subtitle = "Text to sticker",
                        icon = Icons.Outlined.FormatQuote,
                        gradient = listOf(Color(0xFF3B2FD9), Color(0xFF1F6FE0), Color(0xFF2BA3E8))
                    ) { onOpenTool(SocialCreateTool.Quote) }
                    Spacer(Modifier.height(12.dp))
                    CreateToolCard(
                        title = "Photo",
                        subtitle = "Add text & marks",
                        icon = Icons.Outlined.Image,
                        gradient = listOf(Color(0xFFE59A2F), Color(0xFF9A5A3A), Color(0xFF4B3560))
                    ) { onOpenTool(SocialCreateTool.Photo) }
                    Spacer(Modifier.height(12.dp))
                    StickerPackEntry(count = stickerCount) { showStickers = true }

                    Box(
                        Modifier
                            .padding(vertical = 16.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }

                // Recent header + See all / Back
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showAll) "All creations" else "Recent",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = Outfit,
                        fontSize = 15.sp,
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
                            Text(
                                if (showAll) "Back" else "See all",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = Outfit,
                                fontSize = 13.sp
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 14.dp)
                ) {
                    if (creations.isEmpty()) {
                        Text(
                            "Your scribbles, quotes and photos will show up here.",
                            color = Color.White.copy(alpha = 0.45f),
                            fontFamily = Outfit,
                            fontSize = 12.sp
                        )
                    }
                    val shown = if (showAll) creations else creations.take(4)
                    shown.forEach { item ->
                        RecentCreationThumb(
                            item = item,
                            onShare = { shareManager.shareImage(File(item.filePath)) },
                            onLongPress = { shareManager.startDrag(view, File(item.filePath), item.kind.name) }
                        )
                    }
                }
            }
        }
        if (showStickers) {
            StickerPackScreen(onClose = { showStickers = false })
        }
    }
}

/** Compact row that opens the Lumen Stickers screen. */
@Composable
private fun StickerPackEntry(count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color(0x559D63EE), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(CreateIcons.Sticker, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Lumen Stickers", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                if (count == 0) "For WhatsApp" else "$count stickers · WhatsApp",
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = Outfit,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CreateToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .shadow(14.dp, CardShape, spotColor = gradient[1])
            .clip(CardShape)
            .background(Brush.linearGradient(gradient))
            .background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(40f, 20f),
                    radius = 320f
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.08f))),
                CardShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.82f), fontFamily = Outfit, fontSize = 12.sp, maxLines = 1)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(26.dp)
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
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(shape)
            .background(Color(0xFF1C1230))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .combinedClickable(onClick = onShare, onLongClick = onLongPress)
    ) {
        Image(
            painter = painter,
            contentDescription = item.kind.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Icon(
            when (item.kind) {
                CreationKind.Scribble -> Icons.Outlined.Gesture
                CreationKind.Quote -> Icons.Outlined.FormatQuote
                CreationKind.Photo -> Icons.Outlined.Image
            },
            null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(18.dp)
        )
    }
}

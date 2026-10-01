package com.lumen.launcher.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.FormatQuote
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
import com.lumen.launcher.ui.theme.LumenPalette
import com.lumen.launcher.ui.theme.Outfit
import java.io.File

private val PanelGold = Color(0xFFE4CB91)
private val PanelShape = RoundedCornerShape(28.dp)
private val CardShape = RoundedCornerShape(22.dp)
private val TileShape = RoundedCornerShape(18.dp)

/** Lilac (or white in the White Glass theme) for the header sparkle and "See all". */
private val Lilac: Color get() = PanelGold

@Composable
fun SocialCreatePanel(
    open: Boolean,
    creations: List<CreationItem>,
    @Suppress("UNUSED_PARAMETER") activeSpace: SpaceKind,
    shareManager: ShareContentManager,
    onDismiss: () -> Unit,
    onOpenTool: (SocialCreateTool) -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    val dim by animateFloatAsState(if (open) 0.48f else 0f, tween(280), label = "panel-dim")
    if (!visibility.currentState && visibility.isIdle && !open) return
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
                .background(Color.Black.copy(alpha = dim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        AnimatedVisibility(
            visibleState = visibility,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.546f)
                .widthIn(max = 238.dp)
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
            exit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 6.dp, top = 6.dp, bottom = 8.dp)
                    .shadow(
                        elevation = 28.dp,
                        shape = PanelShape,
                        spotColor = Color(0x66000000),
                        ambientColor = Color(0x33000000)
                    )
                    .clip(PanelShape)
                    // Frost stack: dark veil for contrast, then bright white glass on top.
                    .background(Color(0xC9232926))
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFFB6AD95).copy(alpha = 0.10f),
                            0.45f to Color(0xFF51623F).copy(alpha = 0.05f),
                            1f to Color(0xFF9B87AC).copy(alpha = 0.12f)
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            0f to PanelGold.copy(alpha = 0.30f),
                            1f to Color.White.copy(alpha = 0.18f)
                        ),
                        PanelShape
                    )
                    .clickable(interactionSource = blockTouches, indication = null, onClick = {})
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                    in 5..11 -> "Good Morning,"
                    in 12..16 -> "Good Afternoon,"
                    else -> "Good Evening,"
                }
                Text(greeting, color = PanelGold, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
                    fontSize = 23.sp, modifier = Modifier.padding(top = 22.dp))
                Text("Create something today.", color = PanelGold.copy(alpha = .72f), fontFamily = Outfit, fontSize = 12.sp)
                Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(48.dp)) {
                        val gold = Color(0xFFE7D294)
                        drawCircle(gold.copy(alpha = .5f), radius = size.minDimension * .45f, style = Stroke(1.2.dp.toPx()))
                        drawCircle(gold, radius = size.minDimension * .36f, style = Stroke(1.8.dp.toPx()))
                        drawCircle(gold, radius = 3.dp.toPx(), center = Offset(size.width * .79f, size.height * .23f))
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("Lumen Panel", color = Color.White, fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold, fontSize = 21.sp, textAlign = TextAlign.Center)
                    Text("Create & share", color = Color.White.copy(.72f), fontFamily = Outfit, fontSize = 13.sp)
                }

                if (!showAll) {
                    CreateToolCard(
                        title = "Scribble",
                        subtitle = "Turn your ideas into something.",
                        icon = CreateIcons.Squiggle,
                        gradient = listOf(Color(0xFF7C4DDF), Color(0xFF9A7FE3), Color(0xFF6F6FD8))
                    ) { onOpenTool(SocialCreateTool.Scribble) }
                    Spacer(Modifier.height(9.dp))
                    CreateToolCard(
                        title = "Quote",
                        subtitle = "Capture thoughts that matter.",
                        icon = Icons.Filled.FormatQuote,
                        gradient = listOf(Color(0xFF2563EB), Color(0xFF3B82D6), Color(0xFF56B5E0))
                    ) { onOpenTool(SocialCreateTool.Quote) }
                    Spacer(Modifier.height(9.dp))
                    CreateToolCard(
                        title = "Photo",
                        subtitle = "Add a personal touch.",
                        icon = Icons.Outlined.Image,
                        gradient = listOf(Color(0xFFC98B3A), Color(0xFFA77356), Color(0xFF7E6A8A))
                    ) { onOpenTool(SocialCreateTool.Photo) }
                    Spacer(Modifier.height(9.dp))
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
                        .fillMaxWidth()
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

/** Frosted glass row: icon, title + subtitle, chevron. */
@Composable
private fun CreateToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    @Suppress("UNUSED_PARAMETER") gradient: List<Color>,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(CardShape)
            .background(Color.Black.copy(alpha = 0.18f))
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.055f),
                    1f to Color.White.copy(alpha = 0.018f)
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    0f to PanelGold.copy(alpha = 0.30f),
                    1f to Color.White.copy(alpha = 0.16f)
                ),
                CardShape
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PanelGold, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PanelGold, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, lineHeight = 19.sp)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.78f),
                fontFamily = Outfit,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = PanelGold.copy(alpha = 0.88f),
            modifier = Modifier.size(24.dp)
        )
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

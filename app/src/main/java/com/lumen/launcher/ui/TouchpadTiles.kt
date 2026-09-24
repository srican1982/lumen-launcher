package com.lumen.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.badge.NotificationBadgeRepository
import com.lumen.launcher.media.NowPlayingRepository
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.util.StatusBarController
import kotlinx.coroutines.delay

private val TileShape = RoundedCornerShape(22.dp)

/** Frosted white glass, as in the mockup. */
private fun Modifier.whiteGlassTile(): Modifier = this
    .clip(TileShape)
    .background(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.16f)))
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.18f))),
        TileShape
    )

/** Mini player for whatever is playing: artwork, progress, previous / play-pause / next. */
@Composable
fun NowPlayingTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val np by NowPlayingRepository.state.collectAsState()
    LaunchedEffect(Unit) { NowPlayingRepository.start(context) }

    // Advance the progress bar while playing.
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(np) {
        progress = np?.progress ?: 0f
        while (np?.playing == true) {
            delay(500)
            progress = np?.progress ?: 0f
        }
    }

    BoxWithConstraints(
        modifier
            .whiteGlassTile()
            .clickable { NowPlayingRepository.openPlayer(context) }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
    // Short tiles (e.g. labels hidden) drop the title and use a smaller artwork.
    val compact = maxHeight < 100.dp
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Artwork (or a music-note tile when nothing is playing)
        Box(
            Modifier
                .size(if (compact) 30.dp else 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            val art = np?.art
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                Image(image, contentDescription = "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        if (!compact) Text(
            text = np?.title?.takeIf { it.isNotBlank() } ?: "Not Playing",
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        // Progress line with a small knob
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val y = size.height / 2f
            drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            val x = size.width * progress
            drawLine(Color.White, Offset(0f, y), Offset(x, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(Icons.Filled.SkipPrevious, "Previous", 20.dp) { NowPlayingRepository.previous(context) }
            ControlButton(
                if (np?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (np?.playing == true) "Pause" else "Play",
                26.dp
            ) { NowPlayingRepository.playPause(context) }
            ControlButton(Icons.Filled.SkipNext, "Next", 20.dp) { NowPlayingRepository.next(context) }
        }
    }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size))
    }
}

/** Bell with the number of notifications; opens the notification shade. */
@Composable
fun NotificationsPreviewTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val badge by NotificationBadgeRepository.state.collectAsState()
    val count = badge.countsByPackage.values.sum()

    BoxWithConstraints(
        modifier
            .whiteGlassTile()
            .clickable(onClickLabel = "Open notifications") { StatusBarController.expandNotifications(context) },
        contentAlignment = Alignment.Center
    ) {
        // Just a bell with the red count — sized to the tile.
        val bell = (minOf(maxWidth, maxHeight) * 0.56f).coerceIn(22.dp, 42.dp)
        Box(contentAlignment = Alignment.Center) {
            // Soft golden glow behind the bell
            Box(
                Modifier
                    .size(bell * 1.5f)
                    .background(
                        Brush.radialGradient(listOf(Color(0x55FFC857), Color.Transparent)),
                        CircleShape
                    )
            )
            // Bell and badge share one box the size of the bell, so the badge sits on its corner.
            Box(Modifier.size(bell)) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = if (count > 0) "$count notifications" else "Notifications",
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Gold gradient bell
                            drawRect(
                                Brush.verticalGradient(listOf(Color(0xFFFFE3A1), Color(0xFFF7B548), Color(0xFFE8912B))),
                                blendMode = BlendMode.SrcIn
                            )
                        }
                )
                CountBadge(
                    count = count,
                    height = (bell * 0.5f).coerceIn(16.dp, 20.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = bell * 0.22f, y = -(bell * 0.14f))
                )
            }
        }
    }
}

/** Faint dot grid across the TouchPad card (as in the design). */
fun Modifier.touchpadDots(): Modifier = this.drawBehind {
    val step = 14.dp.toPx()
    val r = 0.9.dp.toPx()
    val dot = Color.White.copy(alpha = 0.12f)
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            drawCircle(dot, radius = r, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

/** "Tap Home · Hold Private · Swipe Switch · Swipe Up Apps" along the bottom of the card. */
@Composable
fun TouchpadHintsRow(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        TouchpadHint(Icons.Outlined.TouchApp, "Tap", "Home")
        TouchpadHint(Icons.Outlined.PanTool, "Hold", "Private")
        TouchpadHint(Icons.Outlined.SwapHoriz, "Swipe", "Switch")
        TouchpadHint(Icons.Outlined.KeyboardArrowUp, "Swipe Up", "Apps")
    }
}

@Composable
private fun TouchpadHint(icon: ImageVector, gesture: String, action: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(16.dp))
        Text(gesture, color = Color.White.copy(alpha = 0.92f), fontFamily = Outfit, fontSize = 9.sp, maxLines = 1, lineHeight = 10.sp)
        Text(action, color = Color.White.copy(alpha = 0.72f), fontFamily = Outfit, fontSize = 9.sp, maxLines = 1, lineHeight = 10.sp)
    }
}

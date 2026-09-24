package com.lumen.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.lumen.launcher.data.IconTreatment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.ui.theme.Lumen
import java.io.File

/** Rounded square: straight sides, round corners (not an ellipse). */
val Squircle: Shape = RoundedCornerShape(percent = 30)

@Composable
fun SpaceBackdrop(path: String?) {
    val context = LocalContext.current
    Crossfade(targetState = path, modifier = Modifier.fillMaxSize(), label = "space-wallpaper") { current ->
        if (!current.isNullOrBlank()) {
            val file = File(current)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("${file.absolutePath}-${file.lastModified()}")
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AmbientBackdrop() {
    val glass = LocalGlass.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.18f),
                    0.55f to Color.Transparent,
                    1f to Color.Transparent
                )
            )
            .background(glass.veil)
    )
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lumen.PillRadius),
    glow: Boolean = false,
    airy: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalGlass.current
    Box(modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = if (airy) 8.dp else 14.dp,
                    shape = shape,
                    spotColor = Color(0x44000000),
                    ambientColor = Color(0x22000000)
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to if (airy) glass.airyTop else glass.cardTop,
                        1f to if (airy) glass.airyBottom else glass.cardBottom
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        0f to glass.strokeTop,
                        1f to glass.strokeBottom
                    ),
                    shape = shape
                ),
            content = content
        )
    }
}

fun Modifier.glass(
    shape: Shape = RoundedCornerShape(Lumen.PillRadius),
    colors: GlassColors = GlassColors.OnDark
): Modifier = this
    .shadow(14.dp, shape, spotColor = Color(0x44000000), ambientColor = Color(0x22000000))
    .clip(shape)
    .background(Brush.verticalGradient(0f to colors.filmTop, 1f to colors.filmBottom))
    .border(
        0.8.dp,
        Brush.verticalGradient(0f to colors.strokeTop, 1f to colors.strokeBottom),
        shape
    )

/** Small controls need an edge of their own once the wallpaper turns bright. */
fun Modifier.glassPill(
    shape: Shape = RoundedCornerShape(Lumen.PillRadius),
    colors: GlassColors = GlassColors.OnDark
): Modifier = this
    .clip(shape)
    .background(colors.pill)
    .border(
        0.8.dp,
        Brush.verticalGradient(0f to colors.strokeTop, 1f to colors.strokeBottom),
        shape
    )

fun Modifier.denseGlass(shape: Shape = RoundedCornerShape(Lumen.SheetRadius)): Modifier = this
    .shadow(24.dp, shape, spotColor = Color(0x66000000), ambientColor = Color(0x33000000))
    .clip(shape)
    .background(
        Brush.verticalGradient(
            0f to Color(0xCC2A2A32),
            1f to Color(0xE014141A)
        )
    )
    .border(0.8.dp, Brush.verticalGradient(0f to Color(0x55FFFFFF), 1f to Color(0x12FFFFFF)), shape)

/** Launcher app icon with optional notification badge (see [showNotificationBadge]). */
@Composable
fun AppIconWithBadge(
    packageName: String,
    activityName: String,
    size: Dp,
    icons: IconCache,
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    phase: IconPhase = IconPhase.Rest,
    showNotificationBadge: Boolean = true
) = AppIcon(
    packageName,
    activityName,
    size,
    icons,
    modifier,
    corner,
    phase,
    showNotificationBadge
)

@Composable
fun AppIcon(
    packageName: String,
    activityName: String,
    size: Dp,
    icons: IconCache,
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    phase: IconPhase = IconPhase.Rest,
    showNotificationBadge: Boolean = true
) {
    val treatment = LocalIconTreatment.current
    val whiteGlass = treatment == IconTreatment.WhiteGlass
    var bitmap by remember(packageName, activityName, whiteGlass) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName, activityName, whiteGlass) {
        bitmap = if (whiteGlass) icons.getFrosted(packageName, activityName) else icons.get(packageName, activityName)
    }
    val iconShape = if (corner > 0.dp) RoundedCornerShape(corner) else Squircle
    val badgeMode = LocalNotificationBadgeMode.current
    val badgeCount = if (showNotificationBadge) {
        LocalNotificationBadgeCounts.current[packageName] ?: 0
    } else {
        0
    }
    if (phase == IconPhase.Rest) {
        RestAppIcon(bitmap, size, iconShape, treatment, modifier, badgeCount, badgeMode)
    } else {
        MotionAppIcon(bitmap, size, iconShape, phase, treatment, modifier, badgeCount, badgeMode)
    }
}

@Composable
private fun RestAppIcon(
    bitmap: ImageBitmap?,
    size: Dp,
    iconShape: Shape,
    treatment: IconTreatment,
    modifier: Modifier,
    badgeCount: Int,
    badgeMode: com.lumen.launcher.badge.NotificationBadgeMode
) {
    Box(modifier = modifier.size(size)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = iconColorFilter(treatment),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shadowElevation = iconElevation(treatment, 8f)
                        shape = iconShape
                        clip = true
                    }
                    .then(
                        if (iconHasGlass(treatment)) {
                            Modifier.border(
                                width = 0.8.dp,
                                brush = Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = if (treatment == IconTreatment.Contrast) 0.62f else 0.38f),
                                    1f to Color.White.copy(alpha = 0.10f)
                                ),
                                shape = iconShape
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        } else {
            Box(Modifier.fillMaxSize().clip(iconShape).background(Color.White.copy(alpha = 0.16f)))
        }
        AppNotificationBadge(badgeCount, badgeMode, size)
    }
}

@Composable
private fun MotionAppIcon(
    bitmap: ImageBitmap?,
    size: Dp,
    iconShape: Shape,
    phase: IconPhase,
    treatment: IconTreatment,
    modifier: Modifier,
    badgeCount: Int,
    badgeMode: com.lumen.launcher.badge.NotificationBadgeMode
) {
    val motion = when (phase) {
        IconPhase.Pressed -> spring<Float>(stiffness = 1400f, dampingRatio = 0.90f)
        IconPhase.Launching -> spring(stiffness = 680f, dampingRatio = 0.52f)
        IconPhase.Lifted, IconPhase.Dragging -> spring(stiffness = 520f, dampingRatio = 0.78f)
        IconPhase.Rest -> spring(stiffness = 820f, dampingRatio = 0.68f)
    }
    val scale = when (phase) {
        IconPhase.Pressed -> 0.96f
        IconPhase.Launching -> 1.03f
        IconPhase.Lifted -> 1.05f
        IconPhase.Dragging -> 1.08f
        IconPhase.Rest -> 1f
    }
    val drop = when (phase) {
        IconPhase.Pressed -> 2f
        IconPhase.Launching -> -1.5f
        IconPhase.Lifted -> -8f
        IconPhase.Dragging -> -14f
        IconPhase.Rest -> 0f
    }
    val elevation = when (phase) {
        IconPhase.Pressed -> 7f
        IconPhase.Launching -> 22f
        IconPhase.Lifted -> 28f
        IconPhase.Dragging -> 44f
        IconPhase.Rest -> 16f
    }
    val highlight = when (phase) {
        IconPhase.Pressed -> 0.05f
        IconPhase.Launching -> 0.14f
        IconPhase.Lifted -> 0.13f
        IconPhase.Dragging -> 0.16f
        IconPhase.Rest -> 0.10f
    }
    val floor = when (phase) {
        IconPhase.Pressed -> 0.10f
        IconPhase.Launching -> 0.16f
        IconPhase.Lifted -> 0.22f
        IconPhase.Dragging -> 0.32f
        IconPhase.Rest -> 0.18f
    }
    val floorY = when (phase) {
        IconPhase.Pressed -> 1.5.dp
        IconPhase.Launching -> 2.dp
        IconPhase.Lifted -> 6.dp
        IconPhase.Dragging -> 10.dp
        IconPhase.Rest -> 2.5.dp
    }
    val animScale by animateFloatAsState(scale, motion, label = "iconScaleX")
    val dropY by animateFloatAsState(drop, motion, label = "iconDrop")
    val elev by animateFloatAsState(elevation, motion, label = "iconElevation")
    val gloss by animateFloatAsState(highlight, motion, label = "iconHighlight")
    val floorA by animateFloatAsState(floor, motion, label = "iconFloor")
    val floorOffset by animateDpAsState(
        targetValue = floorY,
        animationSpec = spring(stiffness = 820f, dampingRatio = 0.72f),
        label = "iconFloorY"
    )
    Box(modifier = modifier.size(size)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = floorOffset)
                .graphicsLayer {
                    alpha = floorA
                    scaleX = animScale * 0.92f
                    scaleY = 0.55f
                }
                .clip(iconShape)
                .background(Color.Black)
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animScale
                    scaleY = animScale
                    translationY = dropY
                    shadowElevation = iconElevation(treatment, elev)
                    this.shape = iconShape
                    clip = false
                }
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = iconColorFilter(treatment),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(iconShape)
                )
            } else {
                Box(Modifier.fillMaxSize().clip(iconShape).background(Color.White.copy(alpha = 0.16f)))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(iconShape)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = gloss),
                            0.26f to Color.Transparent,
                            0.82f to Color.Transparent,
                            1f to Color.Black.copy(alpha = gloss * 0.28f)
                        )
                    )
            )
        }
        AppNotificationBadge(badgeCount, badgeMode, size)
    }
}

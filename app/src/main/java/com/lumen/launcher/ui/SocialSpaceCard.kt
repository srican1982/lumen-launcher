package com.lumen.launcher.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.data.AppCategory
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.inbox.InboxItem
import com.lumen.launcher.inbox.InboxSource
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.travel.TripGalleryScreen
import com.lumen.launcher.ui.social.StickerPackScreen
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * Playful Social Space canvas — no TouchPad.
 * Social Today · Create · Recent conversations · Social apps.
 */
@Composable
fun SocialSpaceCard(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    var todayMs by remember { mutableStateOf<Long?>(null) }
    var yesterdayMs by remember { mutableStateOf<Long?>(null) }
    var packageUsage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var details by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf(false) }
    var tripAlbums by remember { mutableStateOf(false) }
    val socialApps = remember(state.homeApps, state.visibleApps) {
        val preferred = state.homeApps.filter { it.category == AppCategory.Social }
        preferred.ifEmpty {
            state.visibleApps.filter { it.category == AppCategory.Social }
        }.distinctBy { it.packageName }.take(24)
    }
    val socialPackages = remember(state.visibleApps) { state.visibleApps.filter { it.category == AppCategory.Social }.map { it.packageName }.toSet() }

    LaunchedEffect(socialPackages) {
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                loadSocialUsage(context, socialPackages)
            }
            todayMs = snapshot?.today
            yesterdayMs = snapshot?.yesterday
            packageUsage = snapshot?.byPackage.orEmpty()
            delay(15_000)
        }
    }

    val chats = remember(state.recentConversations, state.hidden, state.privateApps) {
        com.lumen.launcher.inbox.ConversationRecents.latest(
            state.recentConversations.filter { chat -> chat.packageName !in state.hidden &&
                state.privateApps.none { it.substringBefore('/') == chat.packageName } },
            System.currentTimeMillis(), 5
        )
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SocialTodayCard(
            todayMs = todayMs,
            yesterdayMs = yesterdayMs,
            onDetails = {
                if (todayMs == null && packageUsage.isEmpty()) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else {
                    details = true
                }
            },
            needsAccess = todayMs == null && packageUsage.isEmpty()
        )
        CreateSomethingRow(
            onPhoto = { vm.openSocialTool(SocialCreateTool.Photo) },
            onScribble = { vm.openSocialTool(SocialCreateTool.Scribble) },
            onQuote = { vm.openSocialTool(SocialCreateTool.Quote) },
            onTripAlbums = { tripAlbums = true },
            onSeeAll = { vm.setRecentsOpen(true) }
        )
        RecentConversationsRow(
            chats = chats,
            icons = vm.icons,
            apps = state.visibleApps,
            onOpen = { vm.openConversation(it) },
            onNewChat = {
                val preferred = socialApps.firstOrNull {
                    it.packageName in InboxSource.WhatsApp.packages ||
                        it.packageName in InboxSource.Messages.packages
                }
                if (preferred != null) vm.launch(preferred) else vm.openSearch()
            },
            onSeeAll = {
                if (!state.inboxAccess) vm.requestInboxAccess()
                else vm.openSearch()
            },
            needsAccess = !state.inboxAccess
        )
        YourSocialAppsCard(
            apps = socialApps,
            icons = vm.icons,
            onLaunch = { vm.launch(it) },
            onEdit = { vm.openDrawer() }
        )
    }

    if (stickers) StickerPackScreen(onClose = { stickers = false })
    if (tripAlbums) TripGalleryScreen(onClose = { tripAlbums = false })
    if (details) {
        SocialTodayDetailsSheet(
            todayMs = todayMs,
            yesterdayMs = yesterdayMs,
            packageUsage = packageUsage,
            apps = socialApps,
            icons = vm.icons,
            onDismiss = { details = false },
            onLaunch = { vm.launch(it) }
        )
    }
}

@Composable
private fun SocialTodayCard(
    todayMs: Long?,
    yesterdayMs: Long?,
    onDetails: () -> Unit,
    needsAccess: Boolean
) {
    val shape = RoundedCornerShape(26.dp)
    val borderBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFF8AD8),
            Color(0xFFB794F6),
            Color(0xFF7DD3FC),
            Color(0xFF86EFAC),
            Color(0xFFFBBF24)
        )
    )
    val fill = Brush.linearGradient(
        listOf(Color(0xD94A2A68), Color(0xD93D4578), Color(0xD92A4A62))
    )
    val delta = if (todayMs != null && yesterdayMs != null) yesterdayMs - todayMs else null
    val balance = balanceProgress(todayMs)

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .clip(shape)
            .background(borderBrush)
            .padding(1.5.dp)
            .clip(shape)
            .background(fill)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1.05f)) {
                Text(
                    "SOCIAL TODAY",
                    color = Color(0xFFE9D5FF),
                    fontFamily = Outfit,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    todayMs?.let { formatDuration(it) } ?: "—",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 30.sp
                )
                when {
                    needsAccess -> Text(
                        "Enable usage access",
                        color = Color.White.copy(0.7f),
                        fontFamily = Outfit,
                        fontSize = 12.sp
                    )
                    delta == null -> Text(
                        "Time in social apps",
                        color = Color.White.copy(0.7f),
                        fontFamily = Outfit,
                        fontSize = 12.sp
                    )
                    delta >= 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TrendingDown, null, tint = Color(0xFF86EFAC), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (delta < 60_000) "Same as yesterday\nby this time" else "${formatDuration(delta)} less vs yesterday\nby this time",
                            color = Color(0xFF86EFAC),
                            fontFamily = Outfit,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TrendingUp, null, tint = Color(0xFFF9A8D4), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${formatDuration(-delta)} more vs yesterday\nby this time",
                            color = Color(0xFFF9A8D4),
                            fontFamily = Outfit,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (needsAccess) "Turn on access ›" else "View details ›",
                    color = Color(0xFFE9D5FF),
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onDetails)
                )
            }

            BalanceRing(progress = balance, modifier = Modifier.size(66.dp))

            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(0.9f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "Good conversations,\nbrighter days",
                    color = Color.White.copy(0.92f),
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.End
                )
                Spacer(Modifier.height(4.dp))
                Text("☺", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun BalanceRing(progress: Float, modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFFFF6B9D),
        Color(0xFFC084FC),
        Color(0xFF60A5FA),
        Color(0xFF34D399),
        Color(0xFFFBBF24)
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.5.dp.toPx()
            val arcSize = Size(this.size.minDimension - stroke, this.size.minDimension - stroke)
            val topLeft = Offset(
                (this.size.width - arcSize.width) / 2f,
                (this.size.height - arcSize.height) / 2f
            )
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val sweep = (progress.coerceIn(0.12f, 1f) * 360f)
            val segment = sweep / colors.size
            colors.forEachIndexed { i, c ->
                drawArc(
                    color = c,
                    startAngle = -90f + i * segment,
                    sweepAngle = segment - 2f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Favorite, null, tint = Color(0xFFF9A8D4), modifier = Modifier.size(13.dp))
            Text(
                "Good\nBalance!",
                color = Color.White,
                fontFamily = Outfit,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CreateSomethingRow(
    onPhoto: () -> Unit,
    onScribble: () -> Unit,
    onQuote: () -> Unit,
    onTripAlbums: () -> Unit,
    onSeeAll: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    data class Tool(
        val title: String,
        val caption: String,
        val icon: ImageVector,
        val colors: List<Color>,
        val onClick: () -> Unit
    )
    val tools = listOf(
        Tool("Photo", "Capture", Icons.Outlined.PhotoCamera, listOf(Color(0xFFFF7EB3), Color(0xFFFF5C8A)), onPhoto),
        Tool("Scribble", "Draw & share", Icons.Outlined.Edit, listOf(Color(0xFF8B9CF7), Color(0xFF6B7FF0)), onScribble),
        Tool("Quote", "Inspire", Icons.Outlined.FormatQuote, listOf(Color(0xFF5EEAD4), Color(0xFF2DD4BF)), onQuote)
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(end = 20.dp)
            .background(Color.White.copy(alpha = 0.09f), shape)
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Create something",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp, lineHeight = 20.sp
                )
                Text(
                    "Turn moments into memories",
                    color = Color.White.copy(0.65f),
                    fontFamily = Outfit,
                    fontSize = 12.sp, lineHeight = 15.sp
                )
            }
            Text(
                "See all ›",
                color = Color(0xFFE9D5FF),
                fontFamily = Outfit,
                fontSize = 13.sp, lineHeight = 16.sp,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            tools.forEach { tool ->
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = tool.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(tool.colors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(tool.icon, tool.title, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tool.title,
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp, lineHeight = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        tool.caption,
                        color = Color.White.copy(0.55f),
                        fontFamily = Outfit,
                        fontSize = 9.sp, lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
            TripAlbumPolaroidStack(
                onClick = onTripAlbums,
                modifier = Modifier.offset(x = 22.dp, y = (-16).dp).graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
            )
        }
    }
}

@Composable
private fun TripAlbumPolaroidStack(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(72.dp)
            .height(86.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Back polaroid
        Box(
            Modifier
                .size(48.dp, 58.dp)
                .rotate(-14f)
                .offset(x = (-10).dp, y = 4.dp)
                .shadow(6.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(3.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF7DD3FC), Color(0xFFA78BFA), Color(0xFFF9A8D4))))
            )
        }
        // Middle polaroid
        Box(
            Modifier
                .size(50.dp, 60.dp)
                .rotate(6f)
                .offset(x = 8.dp, y = (-2).dp)
                .shadow(8.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(3.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFFF7EB3), Color(0xFFC084FC))))
            )
        }
        // Front polaroid — tropical vibe
        Box(
            Modifier
                .size(54.dp, 66.dp)
                .rotate(-4f)
                .shadow(10.dp, RoundedCornerShape(5.dp))
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White)
                .padding(4.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFB86C),
                                Color(0xFFFF6B9D),
                                Color(0xFF7C3AED),
                                Color(0xFF0EA5E9)
                            )
                        )
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // Simple palm silhouette
                    val trunk = Path().apply {
                        moveTo(size.width * 0.42f, size.height * 0.92f)
                        quadraticBezierTo(
                            size.width * 0.48f, size.height * 0.55f,
                            size.width * 0.55f, size.height * 0.32f
                        )
                    }
                    drawPath(trunk, color = Color(0xFF1E293B).copy(0.55f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                    val leafColor = Color(0xFF14532D).copy(0.7f)
                    listOf(-40f, -10f, 25f, 55f).forEach { angle ->
                        rotate(angle, pivot = Offset(size.width * 0.55f, size.height * 0.32f)) {
                            drawOval(
                                color = leafColor,
                                topLeft = Offset(size.width * 0.55f, size.height * 0.18f),
                                size = Size(size.width * 0.34f, size.height * 0.12f)
                            )
                        }
                    }
                    drawCircle(Color.White.copy(0.85f), radius = 5.dp.toPx(), center = Offset(size.width * 0.72f, size.height * 0.18f))
                }
                Text(
                    "Good Vibes\nOnly ♡",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentConversationsRow(
    chats: List<InboxItem>,
    icons: IconCache,
    apps: List<AppInfo>,
    onOpen: (InboxItem) -> Unit,
    onNewChat: () -> Unit,
    onSeeAll: () -> Unit,
    needsAccess: Boolean
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                null,
                tint = Color(0xFFE9D5FF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Recent Conversations",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (needsAccess) "Connect" else "See all ›",
                color = Color(0xFFE9D5FF),
                fontFamily = Outfit,
                fontSize = 13.sp, lineHeight = 16.sp,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .nestedScroll(remember { SocialRowScrollBoundary() })
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (chats.isEmpty()) {
                Text(
                    if (needsAccess) "Allow notification access for chats"
                    else "No recent chats yet",
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                chats.forEach { chat ->
                    ConversationChip(
                        chat = chat,
                        icons = icons,
                        activityName = apps.firstOrNull { it.packageName == chat.packageName }?.activityName.orEmpty(),
                        onClick = { onOpen(chat) }
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clickable(onClick = onNewChat)
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.10f))
                        .border(1.2.dp, Color.White.copy(0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Add, "New Chat", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("New Chat", color = Color.White.copy(0.85f), fontFamily = Outfit, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ConversationChip(
    chat: InboxItem,
    icons: IconCache,
    activityName: String,
    onClick: () -> Unit
) {
    val initial = chat.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val hue = remember(chat.title) {
        val colors = listOf(
            Color(0xFFFB7185), Color(0xFFA78BFA), Color(0xFF38BDF8),
            Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFF472B6)
        )
        colors[Math.floorMod((chat.packageName + chat.title).hashCode(), colors.size)]
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(hue, hue.copy(alpha = 0.55f)))),
                contentAlignment = Alignment.Center
            ) {
                ConversationAvatar(chat)

            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1224))
                    .border(1.5.dp, Color(0xFF1A1224), CircleShape)
                    .padding(1.5.dp),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    packageName = chat.packageName,
                    activityName = activityName,
                    size = 17.dp,
                    icons = icons,
                    corner = 5.dp,
                    showNotificationBadge = false
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            chat.title,
            color = Color.White,
            fontFamily = Outfit,
            fontSize = 11.sp, lineHeight = 14.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            relativeTime(chat.postedAt),
            color = Color.White.copy(0.55f),
            fontFamily = Outfit,
            fontSize = 10.sp, lineHeight = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun YourSocialAppsCard(
    apps: List<AppInfo>,
    icons: IconCache,
    onLaunch: (AppInfo) -> Unit,
    onEdit: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GridView, null, tint = Color(0xFFE9D5FF), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Your Social Apps",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Edit ›",
                color = Color(0xFFE9D5FF),
                fontFamily = Outfit,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onEdit)
            )
        }
        Spacer(Modifier.height(12.dp))
        if (apps.isEmpty()) {
            Text(
                "Add social apps to this Space",
                color = Lumen.Muted,
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .nestedScroll(remember { SocialRowScrollBoundary() })
                .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                apps.forEach { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(68.dp)
                            .clickable { onLaunch(app) }
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            activityName = app.activityName,
                            size = 52.dp,
                            icons = icons
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            fontFamily = Outfit,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialTodayDetailsSheet(
    todayMs: Long?,
    yesterdayMs: Long?,
    packageUsage: Map<String, Long>,
    apps: List<AppInfo>,
    icons: IconCache,
    onDismiss: () -> Unit,
    onLaunch: (AppInfo) -> Unit
) {
    val ranked = remember(packageUsage, apps) {
        val byPkg = packageUsage.filterKeys { pkg -> apps.any { it.packageName == pkg } }
        apps
            .map { it to (byPkg[it.packageName] ?: 0L) }
            .sortedByDescending { it.second }
    }
    val maxMs = ranked.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val delta = if (todayMs != null && yesterdayMs != null) yesterdayMs - todayMs else null
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF22A1F3D), Color(0xF21A2238), Color(0xF2141C2E))
                    )
                )
                .border(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(Color(0xFFFF8AD8), Color(0xFFB794F6), Color(0xFF7DD3FC), Color(0xFFFBBF24))
                    ),
                    shape
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "SOCIAL TODAY",
                        color = Color(0xFFE9D5FF),
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        todayMs?.let { formatDuration(it) } ?: "—",
                        color = Color.White,
                        fontFamily = Outfit,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            delta == null -> "Time across your social apps"
                            delta >= 0 -> "↓ ${formatDuration(delta)} less than yesterday by now"
                            else -> "↑ ${formatDuration(-delta)} more than yesterday by now"
                        },
                        color = if (delta != null && delta >= 0) Color(0xFF86EFAC) else Color(0xFFF9A8D4),
                        fontFamily = Outfit,
                        fontSize = 12.sp
                    )
                }
                BalanceRing(progress = balanceProgress(todayMs), modifier = Modifier.size(68.dp))
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.10f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Where your time went",
                color = Color.White.copy(0.7f),
                fontFamily = Outfit,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (ranked.isEmpty()) {
                    Text(
                        "No social app usage recorded today.",
                        color = Lumen.Muted,
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
                ranked.take(12).forEach { (app, ms) ->
                    val fraction = (ms.toFloat() / maxMs.toFloat()).coerceIn(0.04f, 1f)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(0.07f))
                            .clickable { onLaunch(app) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            activityName = app.activityName,
                            size = 42.dp,
                            icons = icons
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    app.label,
                                    color = Color.White,
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (ms > 0) formatDuration(ms) else "—",
                                    color = Color.White.copy(0.85f),
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(0.10f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFFF8AD8), Color(0xFFA78BFA), Color(0xFF60A5FA))
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Done",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/** ~0 empty → 1 light use. Caps “good balance” around 2h social time. */
private fun balanceProgress(todayMs: Long?): Float {
    if (todayMs == null) return 0.55f
    val hours = todayMs / 3_600_000f
    return min(1f, 0.25f + (1f - (hours / 3f).coerceIn(0f, 1f)) * 0.75f)
}

private fun relativeTime(at: Long): String {
    val mins = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        else -> "${mins / (24 * 60)}d ago"
    }
}

private data class SocialUsageSnapshot(
    val today: Long,
    val yesterday: Long,
    val byPackage: Map<String, Long>
)

private fun loadSocialUsage(context: Context, packages: Set<String>): SocialUsageSnapshot? {
    if (packages.isEmpty()) return SocialUsageSnapshot(0, 0, emptyMap())
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    if (ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) != AppOpsManager.MODE_ALLOWED
    ) {
        return null
    }
    return runCatching {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(yesterdayStart - 86_400_000L, now)
        val samples = mutableListOf<com.lumen.launcher.data.UsageTimeline.Event>()
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> samples.add(com.lumen.launcher.data.UsageTimeline.Event(event.timeStamp, event.packageName))
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> samples.add(com.lumen.launcher.data.UsageTimeline.Event(event.timeStamp, event.packageName, false))
                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                android.app.usage.UsageEvents.Event.DEVICE_SHUTDOWN -> samples.add(com.lumen.launcher.data.UsageTimeline.Event(event.timeStamp, null, false))
            }
        }
        val yesterdayEnd = java.time.Instant.ofEpochMilli(now).atZone(zone).minusDays(1).toInstant().toEpochMilli()
        val todayMap = com.lumen.launcher.data.UsageTimeline.totals(samples, todayStart, now, packages)
        val yesterdayMap = com.lumen.launcher.data.UsageTimeline.totals(samples, yesterdayStart, yesterdayEnd, packages)
        SocialUsageSnapshot(
            today = todayMap.values.sum(),
            yesterday = yesterdayMap.values.sum(),
            byPackage = todayMap
        )
    }.getOrNull()
}

private class SocialRowScrollBoundary : NestedScrollConnection {
    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource) = androidx.compose.ui.geometry.Offset(available.x, 0f)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}

@Composable
private fun ConversationAvatar(chat: InboxItem) {
    val atlas = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.lumen.launcher.R.drawable.social_avatar_atlas)
    val index = Math.floorMod((chat.packageName + (chat.conversationId ?: chat.title)).hashCode(), 6)
    val cellWidth = atlas.width / 3
    val cellHeight = atlas.height / 2
    // A square crop inside each portrait cell avoids stretching faces.
    val painter = remember(atlas, index) {
        androidx.compose.ui.graphics.painter.BitmapPainter(atlas,
            androidx.compose.ui.unit.IntOffset((index % 3) * cellWidth, (index / 3) * cellHeight + (cellHeight - cellWidth) / 3),
            androidx.compose.ui.unit.IntSize(cellWidth, cellWidth))
    }
    coil.compose.AsyncImage(
        model = chat.avatarPath?.let { java.io.File(it) },
        contentDescription = if (chat.avatarPath == null) "Placeholder profile photo" else "Profile photo",
        placeholder = painter, error = painter, fallback = painter,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

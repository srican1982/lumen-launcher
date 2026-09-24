package com.lumen.launcher.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import com.lumen.launcher.ui.theme.LumenPalette
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.DockApp
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.data.SpaceCopy
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.data.WeatherSnapshot
import com.lumen.launcher.util.CompetingLauncher
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onRequestDefaultHome: () -> Unit,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val recentsOpen = state.recentsOpen
    var editKey by remember { mutableStateOf<String?>(null) }
    var editPhase by remember { mutableStateOf(IconPhase.Rest) }
    var editDrag by remember { mutableStateOf(Offset.Zero) }
    var liftOrigin by remember { mutableStateOf(Offset.Zero) }
    var dropArmed by remember { mutableStateOf(false) }
    var showFloatPad by remember { mutableStateOf(false) }
    var floatIsland by remember { mutableStateOf(Rect.Zero) }
    val gridRef = remember { CoordBox() }
    val rootRef = remember { CoordBox() }
    val padBox = remember { RectBox() }
    val slotOrigins = remember { mutableMapOf<String, Offset>() }
    val dockSlotWindows = remember { mutableStateMapOf<Int, Rect>() }
    val editing = editPhase == IconPhase.Lifted || editPhase == IconPhase.Dragging
    val hour = rememberLiveHour()
    val copy = remember(state.activeSpace, state.spaceAutomatic, hour) {
        SpaceCopy.context(state.activeSpace, hour, state.spaceAutomatic)
    }
    val restBlur = if (editing) Modifier.blur(12.dp) else Modifier
    val dragged = state.homeApps.find { it.key == editKey }
    val density = LocalDensity.current
    val iconPx = with(density) { state.iconSizeDp.dp.toPx() }
    val iconSlotPad = 4.dp
    val leadLabel = if (state.showLabels) 6.dp + with(density) { 11.sp.toDp() } + 3.dp else 0.dp
    val leadRowHeight = iconSlotPad * 2 + state.iconSizeDp.dp + leadLabel
    val leadBlockHeight = leadRowHeight * 2 + 16.dp
    val gridState = rememberLazyGridState()
    LaunchedEffect(isActive) {
        if (isActive) gridState.scrollToItem(0)
    }
    LaunchedEffect(state.homePulse) {
        if (state.homePulse == 0) return@LaunchedEffect
        viewModel.setRecentsOpen(false)
        gridState.scrollToItem(0)
    }

    fun padVisibleNow(): Boolean {
        val pad = padBox.value
        if (pad.width <= 8f || pad.height <= 8f) return false
        val view = gridRef.value?.takeIf { it.isAttached }?.boundsInWindow() ?: return true
        val visW = (minOf(pad.right, view.right) - maxOf(pad.left, view.left)).coerceAtLeast(0f)
        val visH = (minOf(pad.bottom, view.bottom) - maxOf(pad.top, view.top)).coerceAtLeast(0f)
        return visW > 48f && visH > pad.height * 0.45f
    }

    fun windowOnPad(window: Offset): Boolean {
        if (window == Offset.Unspecified) return false
        val pad = 48f
        val box = padBox.value
        if (box.width > 8f && box.height > 8f &&
            window.x >= box.left - pad && window.x <= box.right + pad &&
            window.y >= box.top - pad && window.y <= box.bottom + pad
        ) {
            return true
        }
        return showFloatPad && floatIsland.width > 8f && floatIsland.inflate(56f).contains(window)
    }

    fun dropWindow(layout: LayoutCoordinates?, origin: Offset, drag: Offset): Offset {
        if (layout == null || !layout.isAttached) return Offset.Unspecified
        return layout.localToWindow(origin + drag + Offset(iconPx / 2f, iconPx / 2f))
    }

    fun dockSlotAt(window: Offset): Int? {
        if (window == Offset.Unspecified) return null
        val pad = 28f
        return dockSlotWindows.entries
            .mapNotNull { (index, box) ->
                if (box.width <= 4f || box.height <= 4f) return@mapNotNull null
                val hit = box.inflate(pad)
                if (!hit.contains(window)) return@mapNotNull null
                index to (box.center - window).getDistance()
            }
            .minByOrNull { it.second }
            ?.first
    }

    val dropWin = dropWindow(gridRef.value, liftOrigin, editDrag)
    val hoverDock = if (editing) dockSlotAt(dropWin) else null
    val overPad = editing && hoverDock == null && windowOnPad(dropWin)
    LaunchedEffect(overPad, editing) {
        if (editing && overPad) dropArmed = true
        if (!editing) dropArmed = false
    }
    LaunchedEffect(showFloatPad) {
        if (!showFloatPad) floatIsland = Rect.Zero
    }

    fun finishDrag(from: String?) {
        val app = state.homeApps.find { it.key == from }
            ?: state.visibleApps.find { it.key == from }
        val window = dropWindow(gridRef.value, liftOrigin, editDrag)
        val dockSlot = dockSlotAt(window)
        if (app != null && dockSlot != null) {
            viewModel.placeOnDock(app, dockSlot)
            dropArmed = false
            editKey = null
            editPhase = IconPhase.Rest
            editDrag = Offset.Zero
            showFloatPad = false
            return
        }
        if (app != null && (dropArmed || windowOnPad(window))) {
            viewModel.moveToPrivate(app)
            dropArmed = false
            editKey = null
            editPhase = IconPhase.Rest
            editDrag = Offset.Zero
            showFloatPad = false
            return
        }
        dropArmed = false
        val point = liftOrigin + editDrag
        val target = slotOrigins.minByOrNull { (_, pos) ->
            (pos - point).getDistance()
        }?.key
        if (from != null && target != null) viewModel.reorderHome(from, target)
        editKey = null
        editPhase = IconPhase.Rest
        editDrag = Offset.Zero
        showFloatPad = false
    }

    @Composable
    fun HomeGridIcon(app: AppInfo?, modifier: Modifier = Modifier) {
        if (app == null) {
            Spacer(modifier)
            return
        }
        val active = editKey == app.key
        IconSlot(
            label = app.label,
            packageName = app.packageName,
            activityName = app.activityName,
            iconSize = state.iconSizeDp.dp,
            icons = viewModel.icons,
            onClick = { viewModel.launch(app) },
            onLongClick = { viewModel.showAppActions(app) },
            showLabel = state.showLabels,
            allowDrag = !recentsOpen,
            dragOffset = Offset.Zero,
            modifier = modifier
                .onGloballyPositioned { coords ->
                    val grid = gridRef.value
                    if (grid != null && grid.isAttached && coords.isAttached) {
                        slotOrigins[app.key] = grid.localPositionOf(coords, Offset.Zero)
                    }
                }
                .then(
                    if (editing && !active) Modifier.blur(12.dp).graphicsLayer { alpha = 0.82f }
                    else Modifier
                ),
            onContact = { phase, drag ->
                if (phase != IconPhase.Pressed && phase != IconPhase.Launching) {
                    if (phase == IconPhase.Lifted) {
                        if (editPhase != IconPhase.Lifted && editPhase != IconPhase.Dragging) {
                            liftOrigin = slotOrigins[app.key] ?: Offset.Zero
                            showFloatPad = !padVisibleNow()
                        }
                    }
                    editKey = if (phase == IconPhase.Rest) null else app.key
                    editPhase = phase
                    editDrag = if (phase == IconPhase.Rest) Offset.Zero else drag
                    if (phase == IconPhase.Rest) showFloatPad = false
                    if (phase == IconPhase.Dragging) {
                        dropArmed = windowOnPad(dropWindow(gridRef.value, liftOrigin, drag))
                        viewModel.dismissPopups()
                    }
                }
            },
            onDragEnd = { finishDrag(app.key) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootRef.value = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .emptySpaceLongPress(
                    enabled = !recentsOpen && !editing,
                    onLongPress = viewModel::openMenu
                )
        ) {
        Spacer(Modifier.height(6.dp))
        HomeGreeting(
            greeting = copy.greeting,
            recentsOpen = recentsOpen,
            weather = state.weather,
            onRecents = { viewModel.setRecentsOpen(!recentsOpen) },
            modifier = restBlur
        )
        if (!recentsOpen) {
            SpaceRow(
                selected = state.activeSpace,
                automatic = state.spaceAutomatic,
                onSelect = { space ->
                    if (space == state.activeSpace && !state.spaceAutomatic) viewModel.selectSpace(null)
                    else viewModel.selectSpace(space)
                }
            )
        }
        if (!state.isDefaultHome) {
            Spacer(Modifier.height(10.dp))
            HomeSetupCard(onSetDefault = onRequestDefaultHome)
        }
        if (state.competingLaunchers.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            OverlayWarningCard(
                launchers = state.competingLaunchers,
                onOpen = viewModel::openPackageInfo
            )
        }
        Spacer(Modifier.height(12.dp))
        ActionBar(
            hint = copy.prompt,
            onClick = viewModel::openSearch,
            onLongClick = { viewModel.openCapture() },
            modifier = restBlur
        )
        if (state.focusing) {
            Spacer(Modifier.height(10.dp))
            FocusBanner(state, viewModel)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onGloballyPositioned { gridRef.value = it }
        ) {
            @Suppress("DEPRECATION")
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.gridColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp, bottom = 12.dp),
                userScrollEnabled = !editing,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                if (state.smartCluster && !editing && state.clusterApps.isNotEmpty()) {
                    item(key = "smart-cluster", span = { GridItemSpan(state.gridColumns) }) {
                        SmartClusterCard(state, viewModel)
                    }
                }
                item(key = "touchpad-row", span = { GridItemSpan(state.gridColumns) }) {
                    val lead = state.homeApps.filter { it.key !in state.folderAppKeys }.take(4)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // A little extra room for the large TouchPad ring.
                            .height(leadBlockHeight + 20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                HomeGridIcon(lead.getOrNull(0), Modifier.weight(1f))
                                HomeGridIcon(lead.getOrNull(1), Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth()) {
                                HomeGridIcon(lead.getOrNull(2), Modifier.weight(1f))
                                HomeGridIcon(lead.getOrNull(3), Modifier.weight(1f))
                            }
                        }
                        // One glass card: large TouchPad ring, mini player and notifications.
                        val cardShape = RoundedCornerShape(30.dp)
                        Column(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .padding(start = 6.dp, top = iconSlotPad, bottom = iconSlotPad)
                                .clip(cardShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.14f))
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.16f))),
                                    cardShape
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 2.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                            ) {
                                TouchpadIsland(
                                    enabled = !recentsOpen && !editing,
                                    state = state,
                                    icons = viewModel.icons,
                                    dropReady = overPad,
                                    onGesture = viewModel::runBlankGesture,
                                    onPrivateArmed = viewModel::armPrivateSpace,
                                    onBoundsInWindow = { l, t, r, b ->
                                        padBox.value = Rect(l, t, r, b)
                                        viewModel.setTouchpadWindow(l, t, r, b)
                                    },
                                    framed = false,
                                    markSize = 104.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                Column(
                                    modifier = Modifier
                                        .width(88.dp)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    NowPlayingTile(
                                        Modifier
                                            .weight(1.25f)
                                            .fillMaxWidth()
                                    )
                                    NotificationsPreviewTile(
                                        Modifier
                                            .weight(0.75f)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                if (!state.focusing) items(state.folders, key = { "folder-${it.id}" }) { folder ->
                    val apps = folder.appKeys.mapNotNull { key -> state.visibleApps.find { it.key == key } }
                    HomeFolderTile(
                        name = folder.name,
                        apps = apps,
                        iconSize = state.iconSizeDp.dp,
                        icons = viewModel.icons,
                        showLabel = state.showLabels,
                        onClick = { viewModel.openFolder(folder) },
                        onLongClick = { viewModel.editFolder(folder) }
                    )
                }
                if (!state.focusing) items(state.homeApps.filter { it.key !in state.folderAppKeys }.drop(4), key = { it.key }) { app ->
                    HomeGridIcon(app)
                }
            }
            }
        }
        Spacer(Modifier.height(8.dp))
        DockBar(
            apps = state.dock,
            slotCount = state.dockCapacity,
            iconSize = state.iconSizeDp.dp,
            icons = viewModel.icons,
            hoverSlot = hoverDock,
            onClick = viewModel::launchDock,
            onLongClick = viewModel::showDockActions,
            onReorder = viewModel::reorderDock,
            onDrawer = viewModel::openDrawer,
            onMove = viewModel::dismissPopups,
            onSlotBounds = { index, box -> dockSlotWindows[index] = box },
            onDropWindow = { window -> windowOnPad(window) },
            onDropPrivate = { app ->
                val info = state.apps.find { it.key == app.key } ?: return@DockBar
                viewModel.moveToPrivate(info)
            },
            modifier = restBlur
                .navigationBarsPadding()
                .padding(bottom = 6.dp)
        )
        }
        FloatingPrivateIsland(
            visible = showFloatPad,
            state = state,
            icons = viewModel.icons,
            dropReady = overPad || dropArmed,
            onBoundsInWindow = { l, t, r, b -> floatIsland = Rect(l, t, r, b) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .zIndex(8f)
                .onGloballyPositioned { coords ->
                    val box = coords.boundsInWindow()
                    if (box.width > 8f && box.height > 8f) {
                        floatIsland = Rect(box.left, box.top, box.right, box.bottom)
                    }
                }
        )
        if (editing && dragged != null) {
            val grid = gridRef.value
            val root = rootRef.value
            val local = if (grid != null && grid.isAttached && root != null && root.isAttached) {
                root.windowToLocal(grid.localToWindow(liftOrigin + editDrag))
            } else {
                liftOrigin + editDrag
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset(local.x.roundToInt(), local.y.roundToInt()) }
                    .zIndex(12f)
            ) {
                AppIcon(
                    dragged.packageName,
                    dragged.activityName,
                    state.iconSizeDp.dp,
                    viewModel.icons,
                    phase = editPhase
                )
                Text(
                    dragged.label,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = Outfit,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun rememberLiveHour(): Int {
    var hour by remember { mutableStateOf(LocalDateTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            hour = now.hour
            val ms = ((60 - now.minute) * 60 - now.second) * 1000L - now.nano / 1_000_000L
            delay(ms.coerceIn(1_000L, 60_000L))
        }
    }
    return hour
}

@Composable
private fun HomeGreeting(
    greeting: String,
    recentsOpen: Boolean,
    weather: WeatherSnapshot?,
    onRecents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date = remember(LocalDate.now()) {
        DateTimeFormatter.ofPattern("EEEE, d MMMM").format(LocalDate.now())
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecentsArrow(open = recentsOpen, onClick = onRecents)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp)
        ) {
            Text(
                greeting,
                color = Lumen.Text,
                fontSize = 28.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                date,
                color = Lumen.Faint,
                fontSize = 13.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        if (weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    weatherIcon(weather.summary),
                    contentDescription = weather.summary,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.Start) {
                    Text(
                        weather.temperatureLabel(),
                        color = Lumen.Text,
                        fontSize = 22.sp,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        weather.summary,
                        color = Lumen.Faint,
                        fontSize = 11.sp,
                        fontFamily = Outfit,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun weatherIcon(summary: String) = when {
    summary.contains("Storm", true) -> Icons.Outlined.Thunderstorm
    summary.contains("Snow", true) -> Icons.Outlined.AcUnit
    summary.contains("Rain", true) || summary.contains("Drizzle", true) -> Icons.Outlined.Grain
    summary.contains("Overcast", true) || summary.contains("Fog", true) -> Icons.Outlined.Cloud
    summary.contains("Mostly", true) -> Icons.Outlined.WbCloudy
    else -> Icons.Outlined.WbSunny
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionBar(
    hint: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Glass(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Lumen.PillRadius)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Lumen.AccentFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Lumen.OnAccent, modifier = Modifier.size(16.dp))
            }
            Text(
                hint,
                color = Lumen.Muted,
                fontSize = 16.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Icon(Icons.Outlined.MicNone, null, tint = Lumen.Accent, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmartClusterCard(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val apps = state.clusterApps
    val kicker = if (state.focusing) "FOCUS" else state.activeSpace.title.uppercase()
    Glass(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), airy = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                kicker,
                color = Lumen.Accent,
                fontFamily = Outfit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Apps that matter now",
                color = Lumen.Faint,
                fontFamily = Outfit,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )
            Crossfade(targetState = "${state.activeSpace.name}:${state.focusing}", label = "cluster") {
                ClusterRing(apps = state.clusterApps, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClusterRing(apps: List<AppInfo>, viewModel: LauncherViewModel) {
    val count = apps.size.coerceAtLeast(1)
    Box(
        modifier = Modifier.fillMaxWidth().height(168.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(148.dp)) {
            val radius = size.minDimension / 2f - 6f
            drawCircle(Color.White.copy(alpha = 0.05f), radius = radius)
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = radius,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
        apps.forEachIndexed { index, app ->
            val angle = Math.toRadians((-90.0 + 360.0 * index / count))
            Box(
                modifier = Modifier
                    .offset(
                        x = (cos(angle) * 58.0).toFloat().dp,
                        y = (sin(angle) * 58.0).toFloat().dp
                    )
                    .size(40.dp)
                    .iconContact(
                        key = app.key,
                        allowDrag = false,
                        onPhase = {},
                        onLaunch = { viewModel.launch(app) },
                        onLongPress = { viewModel.showAppActions(app) }
                    )
            ) {
                AppIcon(app.packageName, app.activityName, 40.dp, viewModel.icons)
            }
        }
    }
}

@Composable
private fun FocusBanner(state: LauncherUiState, viewModel: LauncherViewModel) {
    val left = ((state.focusUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt()
    val task = state.focusTask?.text ?: "This space"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("FOCUS", color = Lumen.Accent, fontFamily = Outfit, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(task, color = Lumen.Text, fontFamily = Outfit, fontSize = 15.sp, maxLines = 1)
            Text("$left min left", color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp)
        }
        Text(
            "End",
            color = Lumen.OnAccent,
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Lumen.Accent)
                .clickable(onClick = viewModel::endFocus)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun HomeSetupCard(onSetDefault: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .glass(RoundedCornerShape(22.dp), LocalGlass.current)
            .clickable(onClick = onSetDefault)
            .padding(16.dp)
    ) {
        Text(
            "STILL USING YOUR OLD LAUNCHER",
            color = Lumen.Accent,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp
        )
        Text(
            "Home swipes still belong to the previous launcher. Set Lumen as the Home app, then press Home.",
            color = Lumen.Text,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Set Lumen as Home",
            color = Lumen.OnAccent,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Lumen.AccentFill)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun OverlayWarningCard(
    launchers: List<CompetingLauncher>,
    onOpen: (String) -> Unit
) {
    val names = launchers.joinToString(", ") { it.label }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .glass(RoundedCornerShape(22.dp), LocalGlass.current)
            .padding(16.dp)
    ) {
        Text(
            "ANOTHER LAUNCHER IS STILL ON TOP",
            color = Lumen.Accent,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp
        )
        Text(
            "$names can still steal top swipes. Turn off Appear on top and Accessibility for that app, or uninstall it.",
            color = Lumen.Text,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        launchers.forEach { launcher ->
            Text(
                "Open ${launcher.label} settings",
                color = Lumen.OnAccent,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Lumen.AccentFill)
                    .clickable { onOpen(launcher.packageName) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun PrivateLockCard(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .glass(RoundedCornerShape(24.dp), LocalGlass.current)
            .clickable(onClick = onUnlock)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Lumen.AccentFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Lock, null, tint = Lumen.OnAccent, modifier = Modifier.size(22.dp))
        }
        Text(
            "Locked",
            color = Lumen.Text,
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            "Hides and locks access through Lumen. Apps can still appear in Settings, Play Store, another launcher, and notifications. Long-press the TouchPad, then confirm it’s you.",
            color = Lumen.Muted,
            fontFamily = Outfit,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Unlock",
            color = Lumen.OnAccent,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Lumen.AccentFill)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** Chip icons: house, briefcase, person, leaf, plane. */
private fun spaceIcon(space: SpaceKind): ImageVector = when (space) {
    SpaceKind.Home -> Icons.Filled.Home
    SpaceKind.Work -> Icons.Outlined.Work
    SpaceKind.Personal -> Icons.Outlined.Person
    SpaceKind.Focus -> Icons.Outlined.Eco
    SpaceKind.Travel -> Icons.Outlined.Flight
    SpaceKind.Private -> Icons.Outlined.Lock
}

@Composable
fun SpaceRow(selected: SpaceKind, automatic: Boolean, onSelect: (SpaceKind) -> Unit) {
    val spaces = SpaceKind.entries.filter { it != SpaceKind.Private }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        spaces.forEach { space ->
            val active = space == selected
            val white = LumenPalette.whiteGlass
            val chipShape = RoundedCornerShape(16.dp)
            val content = when {
                active && white -> Color(0xFF5B21B6)
                active -> Lumen.OnAccent
                else -> Color.White.copy(alpha = 0.88f)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(chipShape)
                    .then(
                        when {
                            active && white -> Modifier.background(
                                Brush.verticalGradient(listOf(Color(0xFFF6F1FF), Color(0xFFE4D8FB)))
                            )
                            active -> Modifier.background(Lumen.AccentFill)
                            else -> Modifier
                                .background(Color.White.copy(alpha = if (white) 0.14f else 0.08f))
                                .border(0.8.dp, Color.White.copy(alpha = if (white) 0.35f else 0.12f), chipShape)
                        }
                    )
                    .clickable { onSelect(space) }
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(spaceIcon(space), contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = space.title,
                    color = content,
                    fontSize = 11.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentsArrow(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "recents-arrow")
    Box(
        modifier = modifier
            .size(36.dp)
            .shadow(10.dp, CircleShape, spotColor = Color(0x55000000), ambientColor = Color(0x22000000))
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x28FFFFFF))))
            .border(0.8.dp, Brush.verticalGradient(listOf(Color(0x88FFFFFF), Color(0x22FFFFFF))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Create and share",
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeFolderTile(
    name: String,
    apps: List<AppInfo>,
    iconSize: Dp,
    icons: IconCache,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(5.dp)
        ) {
            val cells = listOf(
                Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd
            )
            cells.forEachIndexed { index, align ->
                val app = apps.getOrNull(index)
                Box(Modifier.align(align).size(iconSize * 0.38f)) {
                    if (app != null) {
                        AppIcon(app.packageName, app.activityName, iconSize * 0.38f, icons)
                    }
                }
            }
        }
        if (showLabel) {
            Text(
                name,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = Outfit,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DockBar(
    apps: List<DockApp>,
    slotCount: Int = 4,
    iconSize: Dp,
    icons: IconCache,
    hoverSlot: Int? = null,
    onClick: (DockApp) -> Unit,
    onLongClick: (DockApp) -> Unit,
    onReorder: (String, String) -> Unit,
    onDrawer: () -> Unit,
    onMove: () -> Unit,
    onSlotBounds: (Int, Rect) -> Unit = { _, _ -> },
    onDropWindow: (Offset) -> Boolean = { false },
    onDropPrivate: (DockApp) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val slots = List(slotCount.coerceIn(3, 6)) { index -> apps.getOrNull(index) }
    var dockKey by remember { mutableStateOf<String?>(null) }
    var dockPhase by remember { mutableStateOf(IconPhase.Rest) }
    var dockDrag by remember { mutableStateOf(Offset.Zero) }
    var liftOrigin by remember { mutableStateOf(Offset.Zero) }
    var dockLayout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val slotOrigins = remember { mutableStateMapOf<String, Offset>() }
    val moving = dockPhase == IconPhase.Lifted || dockPhase == IconPhase.Dragging
    val density = LocalDensity.current
    val half = with(density) { iconSize.toPx() / 2f }
    Glass(
        modifier = modifier.fillMaxWidth().height(iconSize + 22.dp),
        shape = RoundedCornerShape(Lumen.DockRadius),
        airy = true
    ) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { dockLayout = it }) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                slots.forEachIndexed { index, slot ->
                    DockSlot(
                        app = slot,
                        iconSize = iconSize,
                        icons = icons,
                        hidden = moving && slot?.key == dockKey,
                        highlighted = hoverSlot == index,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onAdd = onDrawer,
                        onBounds = { onSlotBounds(index, it) },
                        onPositioned = { key, coords ->
                            val dock = dockLayout
                            if (dock != null && dock.isAttached && coords.isAttached) {
                                slotOrigins[key] = dock.localPositionOf(coords, Offset.Zero)
                            }
                        },
                        onContact = { app, phase, drag ->
                            if (phase != IconPhase.Pressed && phase != IconPhase.Launching) {
                                if (phase == IconPhase.Lifted &&
                                    dockPhase != IconPhase.Lifted &&
                                    dockPhase != IconPhase.Dragging
                                ) {
                                    liftOrigin = slotOrigins[app.key] ?: Offset.Zero
                                }
                                dockKey = if (phase == IconPhase.Rest) null else app.key
                                dockPhase = phase
                                dockDrag = if (phase == IconPhase.Rest) Offset.Zero else drag
                                if (phase == IconPhase.Dragging) onMove()
                            }
                        },
                        onDragEnd = { app ->
                            val layout = dockLayout
                            val window = if (layout != null && layout.isAttached) {
                                layout.localToWindow(liftOrigin + dockDrag + Offset(half, half))
                            } else Offset.Unspecified
                            if (window != Offset.Unspecified && onDropWindow(window)) {
                                onDropPrivate(app)
                            } else {
                                val point = liftOrigin + dockDrag
                                val target = slotOrigins.minByOrNull { (_, pos) ->
                                    (pos - point).getDistance()
                                }?.key
                                if (target != null) onReorder(app.key, target)
                            }
                            dockKey = null
                            dockPhase = IconPhase.Rest
                            dockDrag = Offset.Zero
                        }
                    )
                }
            }
            val dragged = apps.find { it.key == dockKey }
            if (moving && dragged != null) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (liftOrigin.x + dockDrag.x).roundToInt(),
                                (liftOrigin.y + dockDrag.y).roundToInt()
                            )
                        }
                        .zIndex(12f)
                ) {
                    AppIcon(
                        dragged.packageName,
                        dragged.activityName,
                        iconSize,
                        icons,
                        phase = dockPhase
                    )
                }
            }
        }
    }
}

@Composable
private fun DockSlot(
    app: DockApp?,
    iconSize: Dp,
    icons: IconCache,
    hidden: Boolean,
    highlighted: Boolean = false,
    onClick: (DockApp) -> Unit,
    onLongClick: (DockApp) -> Unit,
    onAdd: () -> Unit,
    onBounds: (Rect) -> Unit = {},
    onPositioned: (String, LayoutCoordinates) -> Unit,
    onContact: (DockApp, IconPhase, Offset) -> Unit,
    onDragEnd: (DockApp) -> Unit
) {
    val glow = if (highlighted) {
        Modifier.border(1.5.dp, Color(0xFFE7C27A).copy(alpha = 0.92f), Squircle)
    } else {
        Modifier
    }
    if (app != null) {
        Box(
            modifier = glow.onGloballyPositioned { onBounds(it.boundsInWindow()) }
        ) {
            DockIcon(
                app = app,
                iconSize = iconSize,
                icons = icons,
                hidden = hidden,
                onClick = onClick,
                onLongClick = onLongClick,
                onPositioned = { onPositioned(app.key, it) },
                onContact = { phase, drag -> onContact(app, phase, drag) },
                onDragEnd = { onDragEnd(app) }
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(iconSize)
                .then(glow)
                .clip(Squircle)
                .background(Color.White.copy(if (highlighted) 0.20f else 0.10f))
                .onGloballyPositioned { onBounds(it.boundsInWindow()) }
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Add, null, tint = Color.White.copy(0.45f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DockIcon(
    app: DockApp,
    iconSize: Dp,
    icons: IconCache,
    hidden: Boolean,
    onClick: (DockApp) -> Unit,
    onLongClick: (DockApp) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    onContact: (IconPhase, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var phase by remember(app.id) { mutableStateOf(IconPhase.Rest) }
    var drag by remember(app.id) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    Box(
        Modifier
            .onGloballyPositioned(onPositioned)
            .iconContact(
                key = app.id,
                allowDrag = true,
                onPhase = {
                    phase = it
                    if (it == IconPhase.Rest) drag = Offset.Zero
                    onContact(it, drag)
                },
                onLaunch = {
                    scope.launch {
                        onClick(app)
                        delay(180)
                        if (phase == IconPhase.Launching) phase = IconPhase.Rest
                    }
                },
                onLongPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongClick(app)
                },
                onLift = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) },
                onDrag = {
                    drag = it
                    onContact(IconPhase.Dragging, it)
                },
                onDragEnd = onDragEnd
            )
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
    ) {
        AppIcon(app.packageName, app.activityName, iconSize, icons, phase = phase)
    }
}

@Composable
fun IconSlot(
    label: String,
    packageName: String,
    activityName: String,
    iconSize: Dp,
    icons: IconCache,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showLabel: Boolean = true,
    labelSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    allowDrag: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
    onContact: ((IconPhase, Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    var phase by remember(packageName, activityName) { mutableStateOf(IconPhase.Rest) }
    var drag by remember(packageName, activityName) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lifted = phase == IconPhase.Lifted || phase == IconPhase.Dragging
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .iconContact(
                key = "$packageName/$activityName",
                allowDrag = allowDrag,
                onPhase = {
                    phase = it
                    if (it == IconPhase.Rest) drag = Offset.Zero
                    onContact?.invoke(it, drag)
                },
                onLaunch = {
                    scope.launch {
                        onClick()
                        delay(180)
                        if (phase == IconPhase.Launching) {
                            phase = IconPhase.Rest
                            onContact?.invoke(IconPhase.Rest, Offset.Zero)
                        }
                    }
                },
                onLongPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                },
                onLift = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) },
                onDrag = {
                    drag = it
                    onContact?.invoke(IconPhase.Dragging, it)
                },
                onDragEnd = { onDragEnd?.invoke() }
            )
            .graphicsLayer { alpha = if (lifted) 0f else 1f }
            .padding(vertical = 4.dp)
    ) {
        AppIcon(packageName, activityName, iconSize, icons, phase = phase)
        if (showLabel) {
            Text(
                label,
                style = TextStyle(
                    color = Color.White.copy(alpha = if (lifted) 0.4f else 1f),
                    fontSize = labelSize,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Normal,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.55f),
                        offset = Offset(0f, 1f),
                        blurRadius = 6f
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private class CoordBox {
    var value: LayoutCoordinates? = null
}

private class RectBox {
    var value: Rect = Rect.Zero
}

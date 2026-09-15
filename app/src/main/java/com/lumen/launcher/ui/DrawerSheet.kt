package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.DrawerFilter
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlin.math.roundToInt

@Composable
fun DrawerSheet(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    var dragApp by remember { mutableStateOf<AppInfo?>(null) }
    var dragPhase by remember { mutableStateOf(IconPhase.Rest) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var liftWindow by remember { mutableStateOf(Offset.Zero) }
    var island by remember { mutableStateOf(Rect.Zero) }
    var dropArmed by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val slotWindows = remember { mutableStateMapOf<String, Offset>() }
    val holding = dragPhase == IconPhase.Lifted || dragPhase == IconPhase.Dragging
    val moving = dragPhase == IconPhase.Dragging
    val density = LocalDensity.current
    val iconPx = with(density) { state.iconSizeDp.dp.toPx() }
    var drawerQuery by remember { mutableStateOf("") }
    val query = drawerQuery.trim()
    val sections = remember(state.drawerSections, query) {
        if (query.isBlank()) state.drawerSections
        else state.drawerSections.map { (title, apps) ->
            title to apps.filter { app ->
                app.label.contains(query, ignoreCase = true)
            }
        }.filter { it.second.isNotEmpty() }
    }
    val showRecents = query.isBlank() && state.recentApps.isNotEmpty() && state.drawerFilter == DrawerFilter.Az

    fun dropPoint(delta: Offset = dragDelta): Offset {
        return liftWindow + delta + Offset(iconPx / 2f, iconPx / 2f)
    }

    fun overPad(delta: Offset = dragDelta): Boolean {
        val box = island
        if (box.width <= 8f || box.height <= 8f) return false
        val point = dropPoint(delta)
        if (box.inflate(72f).contains(point)) return true
        val ghost = Rect(
            liftWindow.x + delta.x,
            liftWindow.y + delta.y,
            liftWindow.x + delta.x + iconPx,
            liftWindow.y + delta.y + iconPx
        )
        return ghost.overlaps(box.inflate(48f))
    }

    val overIsland = moving && overPad()
    LaunchedEffect(overIsland, moving) {
        if (moving) dropArmed = overIsland
    }

    fun finishDrag(app: AppInfo) {
        if (dropArmed || overPad()) {
            viewModel.moveToPrivate(app)
        }
        dropArmed = false
        dragApp = null
        dragPhase = IconPhase.Rest
        dragDelta = Offset.Zero
    }

    fun onContact(app: AppInfo, phase: IconPhase, drag: Offset) {
        if (phase == IconPhase.Pressed || phase == IconPhase.Launching) return
        if (phase == IconPhase.Lifted) {
            if (dragPhase != IconPhase.Lifted && dragPhase != IconPhase.Dragging) {
                liftWindow = slotWindows[app.key] ?: Offset.Zero
            }
        }
        if (phase == IconPhase.Rest) {
            if (dragApp?.key == app.key) {
                dragApp = null
                dragPhase = IconPhase.Rest
                dragDelta = Offset.Zero
            }
        } else {
            dragApp = app
            dragPhase = phase
            dragDelta = drag
        }
        if (phase == IconPhase.Lifted) viewModel.showAppActions(app)
        if (phase == IconPhase.Dragging) {
            dropArmed = overPad(drag)
            viewModel.dismissPopups()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0x994A1D86),
                    0.42f to Color(0x88301858),
                    1f to Color(0xCC14061F)
                )
            )
            .onGloballyPositioned { root = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (moving) Modifier.blur(10.dp) else Modifier)
                .emptySpaceLongPress(enabled = !holding, onLongPress = viewModel::openMenu)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                        .padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Lumen.AccentFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.GridView, null, tint = Lumen.OnAccent, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "Lumen",
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Lumen.Accent.copy(alpha = 0.22f))
                        .blur(18.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(
                            width = 1.2.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Lumen.Accent.copy(alpha = 0.85f),
                                    Color.White.copy(alpha = 0.28f),
                                    Lumen.Pink.copy(alpha = 0.55f)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(22.dp))
                    BasicTextField(
                        value = drawerQuery,
                        onValueChange = { drawerQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Light,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(Lumen.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (drawerQuery.isEmpty()) {
                                    Text(
                                        "Search all apps...",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontFamily = Outfit,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 16.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    Icon(
                        Icons.Outlined.MicNone,
                        contentDescription = "Voice",
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { viewModel.openVoice() }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DrawerFilter.entries.toList()) { filter ->
                    val active = state.drawerFilter == filter
                    val label = when (filter) {
                        DrawerFilter.Az -> "A-Z"
                        DrawerFilter.MostUsed -> "Most used"
                        DrawerFilter.Categories -> "Categories"
                    }
                    Text(
                        text = label,
                        color = if (active) Lumen.OnAccent else Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .then(
                                if (active) Modifier.background(Color(0xFFE6D4FA))
                                else Modifier
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50.dp))
                            )
                            .clickable { viewModel.selectDrawerFilter(filter) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp, top = 8.dp),
                userScrollEnabled = !holding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showRecents) {
                    item(span = { GridItemSpan(4) }, key = "recently-used-h") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, bottom = 10.dp)
                                .clickable { viewModel.selectDrawerFilter(DrawerFilter.MostUsed) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "RECENTLY USED",
                                color = Color.White.copy(alpha = 0.42f),
                                fontSize = 11.sp,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.8.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    item(span = { GridItemSpan(4) }, key = "recently-used-row") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            state.recentApps.take(5).forEach { app ->
                                DrawerAppIcon(
                                    app = app,
                                    iconSize = 58.dp,
                                    icons = viewModel.icons,
                                    dimmed = moving && dragApp?.key != app.key,
                                    onLaunch = { viewModel.launch(app) },
                                    onMenu = { viewModel.showAppActions(app) },
                                    onLocated = { slotWindows[app.key] = it },
                                    onContact = { phase, drag -> onContact(app, phase, drag) },
                                    onDragEnd = { finishDrag(app) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                sections.forEach { (title, apps) ->
                    if (apps.isEmpty()) return@forEach
                    item(span = { GridItemSpan(4) }, key = "h-$title") {
                        Text(
                            title,
                            color = Color.White.copy(alpha = 0.42f),
                            fontSize = 13.sp,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(apps, key = { it.key }) { app ->
                        DrawerAppIcon(
                            app = app,
                            iconSize = 58.dp,
                            icons = viewModel.icons,
                            dimmed = moving && dragApp?.key != app.key,
                            onLaunch = { viewModel.launch(app) },
                            onMenu = { viewModel.showAppActions(app) },
                            onLocated = { slotWindows[app.key] = it },
                            onContact = { phase, drag -> onContact(app, phase, drag) },
                            onDragEnd = { finishDrag(app) }
                        )
                    }
                }
            }
        }
        val active = state.activeApp
        val menuOpen = active != null
        if (active != null) {
            Box(Modifier.zIndex(6f)) {
                AppActionsSheet(active, state, viewModel)
            }
        }
        FloatingPrivateIsland(
            visible = holding || menuOpen,
            state = state,
            icons = viewModel.icons,
            dropReady = overIsland || dropArmed,
            onBoundsInWindow = { l, t, r, b -> island = Rect(l, t, r, b) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
                .zIndex(8f)
                .onGloballyPositioned { coords ->
                    val box = coords.boundsInWindow()
                    if (box.width > 8f && box.height > 8f) {
                        island = Rect(box.left, box.top, box.right, box.bottom)
                    }
                }
        )
        val ghost = dragApp
        if (holding && ghost != null) {
            val local = root?.windowToLocal(liftWindow + dragDelta) ?: Offset.Zero
            Box(
                modifier = Modifier
                    .offset { IntOffset(local.x.roundToInt(), local.y.roundToInt()) }
                    .zIndex(12f)
            ) {
                AppIcon(
                    ghost.packageName,
                    ghost.activityName,
                    state.iconSizeDp.dp,
                    viewModel.icons,
                    phase = dragPhase
                )
            }
        }
    }
}

@Composable
private fun DrawerAppIcon(
    app: AppInfo,
    iconSize: Dp,
    icons: IconCache,
    dimmed: Boolean,
    onLaunch: () -> Unit,
    onMenu: () -> Unit,
    onLocated: (Offset) -> Unit,
    onContact: (IconPhase, Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconSlot(
        label = app.label,
        packageName = app.packageName,
        activityName = app.activityName,
        iconSize = iconSize,
        icons = icons,
        labelSize = 12.sp,
        allowDrag = true,
        onClick = onLaunch,
        onLongClick = onMenu,
        onContact = onContact,
        onDragEnd = onDragEnd,
        modifier = modifier
            .then(if (dimmed) Modifier.blur(10.dp) else Modifier)
            .onGloballyPositioned { coords ->
                onLocated(coords.boundsInWindow().topLeft)
            }
    )
}

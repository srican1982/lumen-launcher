package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lumen.launcher.data.AlphabetIndex
import com.lumen.launcher.data.AppCategory
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.LumenPalette
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.HapticFeedbackConstants

private enum class DrawerTab(val label: String) {
    AZ("A-Z"),
    MOST_USED("Most used"),
    CATEGORIES("Categories"),
    RECENT("Recent")
}

@Composable
fun DrawerSheet(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    var selectedDrawerTab by remember { mutableStateOf(DrawerTab.AZ) }
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
    val azBase = remember(state.visibleApps, state.activeSpace, state.recents) {
        AlphabetIndex.buildAz(
            apps = state.visibleApps,
            space = state.activeSpace,
            recents = state.recents
        )
    }
    val azSections = remember(azBase, query, state.aliases) {
        filterSections(azBase.first, query, state.aliases)
    }
    val recentSections = remember(state.recentApps, query, state.aliases) {
        filterSections(listOf("Recent" to state.recentApps), query, state.aliases)
    }
    val categorySectionsBase = remember(state.visibleApps) {
        categorySections(state.visibleApps)
    }
    val categorySectionsFiltered = remember(categorySectionsBase, query, state.aliases) {
        filterSections(categorySectionsBase, query, state.aliases)
    }
    val displaySections = when (selectedDrawerTab) {
        DrawerTab.AZ -> azSections
        DrawerTab.RECENT -> recentSections
        DrawerTab.CATEGORIES -> categorySectionsFiltered
        DrawerTab.MOST_USED -> emptyList()
    }
    val showAlphabet = selectedDrawerTab == DrawerTab.AZ && query.isBlank()
    val alphabetIndex = remember(azSections, showAlphabet, azBase) {
        if (!showAlphabet) AlphabetIndex.build(emptyList())
        else AlphabetIndex.build(azSections, leadingItems = 0)
            .copy(spaceBoosted = azBase.second.spaceBoosted)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    var scrubbing by remember { mutableStateOf(false) }

    fun jumpToLetter(letter: Char) {
        scrubLetter = letter
        val target = alphabetIndex.firstVisibleIndex(letter) ?: return
        scope.launch { listState.scrollToItem(target) }
    }

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
        if (phase == IconPhase.Dragging) {
            dropArmed = overPad(drag)
            viewModel.dismissPopups()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (LumenPalette.whiteGlass) {
                    // White Glass theme: neutral frosted grey instead of pink
                    Brush.verticalGradient(
                        0f to Color(0xFF59616C),
                        0.35f to Color(0xFF3C434C),
                        0.72f to Color(0xFF262A30),
                        1f to Color(0xFF15171B)
                    )
                } else {
                    Brush.verticalGradient(
                        0f to Color(0xFFA22E77),
                        0.35f to Color(0xFF6E1E5C),
                        0.72f to Color(0xFF3A1236),
                        1f to Color(0xFF190A18)
                    )
                }
            )
            .background(if (LumenPalette.whiteGlass) Color(0x26000000) else Color(0x40200A1C))
            .onGloballyPositioned { root = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (moving) Modifier.blur(10.dp) else Modifier)
                .emptySpaceLongPress(enabled = !holding, onLongPress = viewModel::openMenu)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.GridView, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
                Text(
                    "Lumen",
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                        .clickable { viewModel.openMenu() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        state.activeSpace.title,
                        color = Color.White.copy(alpha = 0.92f),
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = "Space",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(12.dp, RoundedCornerShape(26.dp), spotColor = Color(0x44000000))
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0x40201828))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
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
            Spacer(Modifier.height(12.dp))
            DrawerTabRow(
                selected = selectedDrawerTab,
                onSelect = { selectedDrawerTab = it }
            )
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedDrawerTab == DrawerTab.MOST_USED) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Most used is coming soon",
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = Outfit,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    val listEndPad = if (showAlphabet && alphabetIndex.available.isNotEmpty()) 36.dp else 4.dp
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = listEndPad),
                        contentPadding = PaddingValues(bottom = 28.dp, top = 4.dp),
                        userScrollEnabled = !holding && !scrubbing
                    ) {
                        displaySections.forEach { (title, apps) ->
                            if (apps.isEmpty()) return@forEach
                            item(key = "h-${selectedDrawerTab.name}-$title") {
                                val letterHeader = selectedDrawerTab == DrawerTab.AZ && title.length == 1
                                Text(
                                    title,
                                    color = Color.White.copy(alpha = if (letterHeader) 0.88f else 0.72f),
                                    fontSize = if (letterHeader) 28.sp else 22.sp,
                                    fontFamily = Outfit,
                                    fontWeight = if (letterHeader) FontWeight.Light else FontWeight.Medium,
                                    letterSpacing = if (letterHeader) 0.sp else 0.3.sp,
                                    modifier = Modifier.padding(
                                        top = if (letterHeader) 20.dp else 18.dp,
                                        bottom = if (letterHeader) 12.dp else 10.dp
                                    )
                                )
                            }
                            if (selectedDrawerTab == DrawerTab.CATEGORIES) {
                                items(
                                    items = apps.chunked(4),
                                    key = { row -> row.map { it.key }.joinToString() }
                                ) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { app ->
                                            DrawerCategoryCell(
                                                app = app,
                                                icons = viewModel.icons,
                                                dimmed = moving && dragApp?.key != app.key,
                                                lifted = holding && dragApp?.key == app.key,
                                                onLaunch = { viewModel.launch(app) },
                                                onMenu = { viewModel.showAppActions(app) },
                                                onLocated = { slotWindows[app.key] = it },
                                                onContact = { phase, drag -> onContact(app, phase, drag) },
                                                onDragEnd = { finishDrag(app) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(4 - row.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(apps, key = { "${selectedDrawerTab.name}-${it.key}" }) { app ->
                                    DrawerAppRow(
                                        app = app,
                                        icons = viewModel.icons,
                                        dimmed = moving && dragApp?.key != app.key,
                                        lifted = holding && dragApp?.key == app.key,
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
                    if (showAlphabet && alphabetIndex.available.isNotEmpty()) {
                        AlphabetRail(
                            index = alphabetIndex,
                            activeLetter = scrubLetter,
                            scrubbing = scrubbing,
                            icons = viewModel.icons,
                            onLetter = ::jumpToLetter,
                            onScrubbingChange = { active ->
                                scrubbing = active
                                if (!active) scrubLetter = null
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .zIndex(4f)
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

/** Applies the drawer search query to any (title, apps) section list — shared by all tabs. */
private fun filterSections(
    base: List<Pair<String, List<AppInfo>>>,
    query: String,
    aliases: Map<String, String>
): List<Pair<String, List<AppInfo>>> {
    if (query.isBlank()) return base
    val normalized = FuzzySearch.normalize(query)
    val aliasKey = aliases[normalized]
    return base.map { (title, apps) ->
        title to apps.map { app ->
            val score = maxOf(
                FuzzySearch.score(query, app.label),
                if (app.key == aliasKey) 1200 else 0,
                if (normalized.length >= 4 &&
                    app.packageName.contains(normalized.replace(" ", ""), ignoreCase = true)
                ) 620 else 0
            )
            app to score
        }.filter { it.second >= 300 }
            .sortedByDescending { it.second }
            .map { it.first }
    }.filter { it.second.isNotEmpty() }
}

/** Groups apps by AppCategory for the Categories tab; empty categories are dropped. */
private fun categorySections(apps: List<AppInfo>): List<Pair<String, List<AppInfo>>> {
    val order = listOf(
        AppCategory.Work,
        AppCategory.Social,
        AppCategory.Entertainment,
        AppCategory.Finance,
        AppCategory.Food,
        AppCategory.Shopping,
        AppCategory.Travel,
        AppCategory.Utilities,
        AppCategory.Games
    )
    val grouped = apps.groupBy { it.category }
    return order.mapNotNull { category ->
        val bucket = grouped[category].orEmpty()
        if (bucket.isEmpty()) return@mapNotNull null
        category.label to bucket.sortedBy { it.label.lowercase() }
    }
}

/**
 * A-Z / Most used / Categories / Recent pill row under the search field.
 * A-Z, Recent, and Categories drive real content; Most used is a
 * placeholder until usage-frequency tracking exists.
 */
@Composable
private fun DrawerTabRow(
    selected: DrawerTab,
    onSelect: (DrawerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DrawerTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val shape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            Modifier.shadow(12.dp, shape, spotColor = Lumen.AccentDeep.copy(alpha = 0.65f))
                        } else {
                            Modifier
                        }
                    )
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            Brush.horizontalGradient(
                                listOf(Color(0xFF9B5DE5), Color(0xFF7C3AED))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.05f))
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFFB794F6).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                        shape = shape
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) Color(0xFFF8F4FF) else Color.White.copy(alpha = 0.68f),
                    fontFamily = Outfit,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun DrawerAppRow(
    app: AppInfo,
    icons: IconCache,
    dimmed: Boolean,
    lifted: Boolean,
    onLaunch: () -> Unit,
    onMenu: () -> Unit,
    onLocated: (Offset) -> Unit,
    onContact: (IconPhase, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var phase by remember(app.key) { mutableStateOf(IconPhase.Rest) }
    var drag by remember(app.key) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (dimmed) Modifier.blur(8.dp) else Modifier)
            .iconContact(
                key = app.key,
                allowDrag = true,
                onPhase = {
                    phase = it
                    if (it == IconPhase.Rest) drag = Offset.Zero
                    onContact(it, drag)
                },
                onLaunch = {
                    scope.launch {
                        onLaunch()
                        delay(180)
                        if (phase == IconPhase.Launching) {
                            phase = IconPhase.Rest
                            onContact(IconPhase.Rest, Offset.Zero)
                        }
                    }
                },
                onLongPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onMenu()
                },
                onLift = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                },
                onDrag = {
                    drag = it
                    onContact(IconPhase.Dragging, it)
                },
                onDragEnd = onDragEnd
            )
            .onGloballyPositioned { coords ->
                onLocated(coords.boundsInWindow().topLeft)
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 2.dp)
        ) {
        AppIcon(
            packageName = app.packageName,
            activityName = app.activityName,
            size = 46.dp,
            icons = icons,
            corner = 14.dp,
            phase = if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                IconPhase.Lifted
            } else {
                phase
            },
            modifier = Modifier.then(
                if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                    Modifier.graphicsLayer { alpha = 0f }
                } else {
                    Modifier
                }
            )
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            color = Color.White.copy(alpha = if (lifted) 0.35f else 0.96f),
            fontFamily = Outfit,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        }
    }
}

@Composable
private fun DrawerCategoryCell(
    app: AppInfo,
    icons: IconCache,
    dimmed: Boolean,
    lifted: Boolean,
    onLaunch: () -> Unit,
    onMenu: () -> Unit,
    onLocated: (Offset) -> Unit,
    onContact: (IconPhase, Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember(app.key) { mutableStateOf(IconPhase.Rest) }
    var drag by remember(app.key) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .defaultMinSize(minHeight = 88.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (dimmed) Modifier.blur(8.dp) else Modifier)
            .iconContact(
                key = app.key,
                allowDrag = true,
                onPhase = {
                    phase = it
                    if (it == IconPhase.Rest) drag = Offset.Zero
                    onContact(it, drag)
                },
                onLaunch = {
                    scope.launch {
                        onLaunch()
                        delay(180)
                        if (phase == IconPhase.Launching) {
                            phase = IconPhase.Rest
                            onContact(IconPhase.Rest, Offset.Zero)
                        }
                    }
                },
                onLongPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onMenu()
                },
                onLift = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                },
                onDrag = {
                    drag = it
                    onContact(IconPhase.Dragging, it)
                },
                onDragEnd = onDragEnd
            )
            .onGloballyPositioned { coords ->
                onLocated(coords.boundsInWindow().topLeft)
            }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        AppIcon(
            packageName = app.packageName,
            activityName = app.activityName,
            size = 52.dp,
            icons = icons,
            corner = 14.dp,
            phase = if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                IconPhase.Lifted
            } else {
                phase
            },
            modifier = Modifier.then(
                if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                    Modifier.graphicsLayer { alpha = 0f }
                } else {
                    Modifier
                }
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Color.White.copy(alpha = if (lifted) 0.35f else 0.9f),
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

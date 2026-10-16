package com.lumen.launcher.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    AZ("All"),
    MOST_USED("Top"),
    CATEGORIES("Groups"),
    RECENT("Recent")
}

@Composable
fun DrawerSheet(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    // The drawer owns Back while visible, ahead of the underlying Focus guide.
    androidx.activity.compose.BackHandler(enabled = state.sheet == com.lumen.launcher.vm.Sheet.Drawer) {
        if (state.activeApp != null) viewModel.dismissAppActions() else onDismiss()
    }
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
    val iconPx = with(density) { 44.dp.toPx() }
    var drawerQuery by remember { mutableStateOf("") }
    val query = drawerQuery.trim()
    val dismissKeyboardOnScroll = rememberSearchKeyboardDismissal()
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
    val mostUsedSections = remember(state.visibleApps, state.launchHours, query, state.aliases) {
        val apps = com.lumen.launcher.data.AppUsageRanking.rank(state.visibleApps, state.launchHours)
        filterSections(listOf("Most used" to apps), query, state.aliases)
    }
    val displaySections = when (selectedDrawerTab) {
        DrawerTab.AZ -> azSections
        DrawerTab.RECENT -> recentSections
        DrawerTab.CATEGORIES -> categorySectionsFiltered
        DrawerTab.MOST_USED -> mostUsedSections
    }
    val gridColumns = 4
    val allAzApps = remember(azSections) { azSections.flatMap { it.second } }
    val gridSections = displaySections
    val showAlphabet = selectedDrawerTab == DrawerTab.AZ && query.isBlank()
    val alphabetIndex = remember(azSections, showAlphabet, azBase) {
        if (!showAlphabet) AlphabetIndex.build(emptyList())
        else AlphabetIndex.buildGrid(azSections, gridColumns)
            .copy(spaceBoosted = azBase.second.spaceBoosted)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    var scrubbing by remember { mutableStateOf(false) }

    var alphabetTouch by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val searchAttention = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(scrubbing, alphabetTouch) {
        if (alphabetTouch == 0 && !scrubbing) return@LaunchedEffect
        searchAttention.animateTo(.65f, androidx.compose.animation.core.tween(130))
        searchAttention.animateTo(if (scrubbing) .22f else 0f, androidx.compose.animation.core.tween(450))
    }

    val gridDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(gridDragged) { if (gridDragged) scrubLetter = null }
    val highlightedLetter = if (showAlphabet && scrubbing) scrubLetter else null

    fun jumpToLetter(letter: Char) {
        scrubLetter = letter
        alphabetTouch++
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
            .background(DrawerTheme.gradient)
            .onGloballyPositioned { root = it }
    ) {
        SpaceBackdrop(state.spaceWallpaper, extraBlur = 8.dp)
        Box(Modifier.fillMaxSize().background(Color(0xEB080B0C)))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (moving) Modifier.blur(10.dp) else Modifier)
                .nestedScroll(dismissKeyboardOnScroll)
                .emptySpaceLongPress(enabled = !holding, onLongPress = viewModel::openMenu)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            SearchField(
                query = drawerQuery,
                onQueryChange = { drawerQuery = it },
                focusRequester = searchFocus,
                onSearch = { viewModel.openSearch(); viewModel.onQueryChange(drawerQuery) },
                hint = "Search apps...",
                onVoice = { viewModel.voice.openVoice() },
                autoFocus = false,
                showScope = false,
                attention = searchAttention.value
            )
            Spacer(Modifier.height(16.dp))
            DrawerTabRow(
                selected = selectedDrawerTab,
                onSelect = { selectedDrawerTab = it }
            )
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (selectedDrawerTab == DrawerTab.MOST_USED && mostUsedSections.all { it.second.isEmpty() }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isNotBlank()) "No matching apps" else "Open apps with Lumen to see your most used here.",
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
                        gridSections.forEach { (title, apps) ->
                            if (apps.isEmpty()) return@forEach
                            if (selectedDrawerTab == DrawerTab.CATEGORIES || selectedDrawerTab == DrawerTab.AZ) {
                                item(key = "h-$title") {
                                    Text(title, color = Color.White.copy(.9f), fontFamily = Outfit,
                                        fontSize = 22.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 9.dp, bottom = 6.dp))
                                }
                            }
                            items(apps.chunked(gridColumns), key = { row ->
                                "${selectedDrawerTab.name}-$title-${row.first().key}"
                            }) { row ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    row.forEach { app ->
                                        DrawerCategoryCell(
                                            app = app, icons = viewModel.icons,
                                            dimmed = moving && dragApp?.key != app.key,
                                            highlighted = highlightedLetter == null ||
                                                AlphabetIndex.letterKey(app.label).first() == highlightedLetter,
                                            lifted = holding && dragApp?.key == app.key,
                                            onLaunch = { viewModel.launch(app) },
                                            onMenu = { viewModel.showAppActions(app) },
                                            onLocated = { slotWindows[app.key] = it },
                                            onContact = { phase, drag -> onContact(app, phase, drag) },
                                            onDragEnd = { finishDrag(app) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(gridColumns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        if (gridSections.all { it.second.isEmpty() }) item {
                            Text("No matching apps", color = Color.White.copy(.6f), fontFamily = Outfit,
                                modifier = Modifier.padding(vertical = 24.dp))
                        }
                    }
                    if (showAlphabet && alphabetIndex.available.isNotEmpty()) {
                        AlphabetRail(
                            index = alphabetIndex,
                            activeLetter = highlightedLetter,
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
                    44.dp,
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
 * Most used ranks the locally saved launch history.
 */
@Composable
private fun DrawerTabRow(
    selected: DrawerTab,
    onSelect: (DrawerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DrawerTab.entries.forEach { tab ->
            val selectedTab = tab == selected
            Box(Modifier.weight(1f).height(48.dp).clickable { onSelect(tab) },
                contentAlignment = Alignment.Center) {
                Text(tab.label, color = if (selectedTab) Color(0xFF241B10) else Color.White.copy(.65f),
                    fontFamily = Outfit, fontSize = 14.sp,
                    fontWeight = if (selectedTab) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(if (selectedTab)
                            listOf(Color(0xFFF3D7A0), Color(0xFFF5CB7E))
                            else listOf(Color.Transparent, Color.Transparent)))
                        .padding(horizontal = 4.dp, vertical = 8.dp))
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
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember(app.key) { mutableStateOf(IconPhase.Rest) }
    var drag by remember(app.key) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val iconTouch = Modifier
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (dimmed) Modifier.blur(8.dp) else Modifier)

    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 2.dp)
        ) {
        Box(iconTouch.padding(2.dp)) {
        AppIcon(
            packageName = app.packageName,
            activityName = app.activityName,
            size = 44.dp,
            icons = icons,
            corner = 14.dp,
            phase = if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                IconPhase.Lifted
            } else {
                phase
            },
            modifier = Modifier.onGloballyPositioned { onLocated(it.boundsInWindow().topLeft) }.then(
                if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                    Modifier.graphicsLayer { alpha = 0f }
                } else {
                    Modifier
                }
            )
        )
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) {
        Text(
            text = app.label,
            color = Color.White.copy(alpha = if (lifted) 0.35f else 0.96f),
            fontFamily = Outfit,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onLaunch).padding(vertical = 12.dp, horizontal = 4.dp)
        )
        }
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
    modifier: Modifier = Modifier,
    highlighted: Boolean = true
) {
    var phase by remember(app.key) { mutableStateOf(IconPhase.Rest) }
    var drag by remember(app.key) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val iconTouch = Modifier
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { alpha = if (highlighted) 1f else .25f }
            .defaultMinSize(minHeight = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (dimmed) Modifier.blur(8.dp) else Modifier)

            .padding(vertical = 3.dp, horizontal = 0.dp)
    ) {
        Box(iconTouch.padding(2.dp)) {
        AppIcon(
            packageName = app.packageName,
            activityName = app.activityName,
            size = 44.dp,
            icons = icons,
            corner = 14.dp,
            phase = if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                IconPhase.Lifted
            } else {
                phase
            },
            modifier = Modifier.onGloballyPositioned { onLocated(it.boundsInWindow().topLeft) }.then(
                if (lifted || phase == IconPhase.Lifted || phase == IconPhase.Dragging) {
                    Modifier.graphicsLayer { alpha = 0f }
                } else {
                    Modifier
                }
            )
        )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = app.label,
            color = Color.White.copy(alpha = if (lifted) 0.35f else 0.9f),
            fontFamily = Outfit,
            fontWeight = FontWeight.Light,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(onClick = onLaunch).padding(horizontal = 1.dp, vertical = 2.dp)
        )
    }
}

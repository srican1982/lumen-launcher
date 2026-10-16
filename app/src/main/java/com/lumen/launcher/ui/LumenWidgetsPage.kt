package com.lumen.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WidthNormal
import androidx.compose.material.icons.outlined.WidthWide
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.widgets.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LumenWidgetsPage(isActive: Boolean, space: com.lumen.launcher.data.SpaceKind = com.lumen.launcher.data.SpaceKind.Home) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val host = remember { WidgetHostSession.host(context) }
    var ids by remember(space) { mutableStateOf(WidgetHostStore.idsForMode(context, space.name)) }
    var picker by remember { mutableStateOf(false) }
    var appWidgetTab by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var sizingRevision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val prefs = remember { WidgetHostStore.prefs(context) }
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var positionRevision by remember { mutableIntStateOf(0) }
    val positions = remember { mutableStateMapOf<Int, WidgetPosition>() }
    var dragOrigin by remember { mutableStateOf<Map<Int, WidgetPosition>>(emptyMap()) }
    var dragDesired by remember { mutableStateOf(WidgetPosition(0f, 0f)) }
    val moveDrag = remember { arrayOf<(Offset) -> Unit>({}) }
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableFloatStateOf(1f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var alignment by remember { mutableStateOf<WidgetAlignment?>(null) }

    DisposableEffect(prefs, space) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ids" || key?.startsWith("space_") == true) {
                ids = WidgetHostStore.idsForMode(context, space.name)
                positionRevision++
            } else if (key?.startsWith("x_") == true || key?.startsWith("y_") == true) {
                positionRevision++
            } else if (key?.startsWith("columns_") == true || key?.startsWith("height_") == true || key?.startsWith("transparent_") == true) {
                sizingRevision++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(host, isActive) {
        if (isActive) WidgetHostSession.start()
        onDispose { if (isActive) WidgetHostSession.stop() }
    }
    LaunchedEffect(isActive, space) {
        if (isActive) {
            val stale = ids.filter { manager.getAppWidgetInfo(it) == null }.toSet()
            if (stale.isNotEmpty()) WidgetHostStore.remove(context, stale)
            withContext(Dispatchers.IO) { runCatching { LumenWidgetProvider.refreshAll(context) } }
        }
    }
    LaunchedEffect(editing) {
        if (!editing) {
            WidgetHostStore.setPositions(context, positions.toMap())
            draggingId = null
            alignment = null
        }
    }
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            val edge = with(density) { 48.dp.toPx() }
            val movement = when {
                fingerY < edge -> -10f
                fingerY > viewportHeightPx - edge -> 10f
                else -> 0f
            }
            if (movement != 0f) {
                val moved = scroll.scrollBy(movement)
                moveDrag[0](Offset(0f, moved / density.density))
            }
            kotlinx.coroutines.delay(16)
        }
    }

    fun kindOf(id: Int): WidgetKind =
        WidgetKind.entries.firstOrNull {
            manager.getAppWidgetInfo(id)?.provider == LumenWidgetProvider.component(context, it)
        } ?: WidgetKind.Tasks

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${space.title} widgets", color = Lumen.Text, fontFamily = Outfit, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (editing) "Hold & drag to move · Pull corner to resize"
                    else "A little space for what matters",
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = { editing = !editing }, enabled = ids.isNotEmpty()) {
                Icon(
                    if (editing) Icons.Outlined.Close else Icons.Outlined.Tune,
                    if (editing) "Finish editing" else "Arrange widgets",
                    tint = Lumen.Accent
                )
            }
            TextButton(onClick = { picker = true }) {
                Icon(Icons.Outlined.Add, "Add widget", tint = Lumen.Accent)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { appWidgetTab = false; picker = true }) { Text("Lumen widgets", color = Lumen.BrandGold) }
            TextButton(onClick = { appWidgetTab = true; picker = true }) { Text("App widgets", color = Lumen.BrandGold) }
        }
        if (message.isNotEmpty()) {
            Text(message, color = Lumen.Accent, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (ids.isEmpty()) {
            Glass(modifier = Modifier.fillMaxWidth().height(220.dp), shape = RoundedCornerShape(26.dp), airy = true) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Make room for your day", color = Lumen.Text, fontFamily = Outfit, fontSize = 21.sp)
                    Text(
                        "Trip, tasks, people, photos and quiet moments. Add the widgets you want to keep close.",
                        color = Lumen.Muted,
                        fontFamily = Outfit,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                    TextButton(onClick = { picker = true }) {
                        Text("Choose widgets", color = Lumen.Accent)
                    }
                }
            }
        } else {
            val layoutSizes = remember(ids, sizingRevision) {
                ids.associateWith { id ->
                    val kind = kindOf(id)
                    WidgetHostStore.columns(context, id, kind) to WidgetHostStore.height(context, id, kind)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(bottom = 58.dp).onSizeChanged {
                viewportHeightPx = it.height.toFloat()
            }) {
                val boardWidth = maxWidth.value
                val defaults = remember(ids, layoutSizes, boardWidth) {
                    WidgetBoardLayout.defaults(ids.map { id ->
                        WidgetBoardSize(id, layoutSizes.getValue(id).first, layoutSizes.getValue(id).second)
                    }, boardWidth)
                }
                LaunchedEffect(ids, boardWidth, positionRevision, layoutSizes) {
                    if (draggingId == null) {
                        val saved = ids.associateWith { WidgetHostStore.position(context, it) }
                        val initial = ids.associateWith { id -> saved[id] ?: positions[id] ?: defaults.getValue(id) }
                        val settled = WidgetBoardLayout.settle(ids.map { WidgetBoardSize(it, layoutSizes.getValue(it).first, layoutSizes.getValue(it).second) }, boardWidth, initial)
                        positions.putAll(settled)
                        WidgetHostStore.setPositions(context, settled)
                    }
                    positions.keys.filter { it !in ids }.forEach { positions.remove(it) }
                }
                val shown = WidgetBoardLayout.settle(
                    ids.map { WidgetBoardSize(it, layoutSizes.getValue(it).first, layoutSizes.getValue(it).second) },
                    boardWidth, ids.associateWith { positions[it] ?: defaults.getValue(it) }
                )
                fun widthOf(id: Int) = if (layoutSizes.getValue(id).first == 2) boardWidth else (boardWidth - 10f) / 2f
                SideEffect {
                    moveDrag[0] = { amount ->
                        draggingId?.let { id ->
                            val available = boardWidth - widthOf(id)
                            dragDesired = WidgetPosition(if (available > 0f) (dragDesired.xFraction + amount.x / available).coerceIn(0f, 1f) else 0f,
                                (dragDesired.yDp + amount.y).coerceAtLeast(0f))
                            positions.putAll(WidgetBoardLayout.settle(ids.map { WidgetBoardSize(it, layoutSizes.getValue(it).first, layoutSizes.getValue(it).second) }, boardWidth, dragOrigin, id, dragDesired))
                            alignment = WidgetBoardLayout.align(positions.getValue(id), widthOf(id), layoutSizes.getValue(id).second.toFloat(), boardWidth, emptyList())
                        }
                    }
                }
                val contentHeight = maxOf(maxHeight.value, ids.maxOfOrNull {
                    shown.getValue(it).yDp + layoutSizes.getValue(it).second + 66f
                } ?: 0f)
                Box(Modifier.fillMaxSize().verticalScroll(scroll, enabled = draggingId == null)) {
                    Box(Modifier.fillMaxWidth().height(contentHeight.dp).pointerInput(editing, boardWidth, layoutSizes) {
                        if (!editing) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { point ->
                                val x = point.x / density.density
                                val y = point.y / density.density
                                draggingId = ids.asReversed().firstOrNull { id ->
                                    val pos = positions[id] ?: defaults.getValue(id)
                                    val width = widthOf(id)
                                    val left = pos.xFraction * (boardWidth - width)
                                    x in left..(left + width) && y in pos.yDp..(pos.yDp + layoutSizes.getValue(id).second)
                                }
                                dragOrigin = ids.associateWith { positions[it] ?: defaults.getValue(it) }
                                draggingId?.let { dragDesired = dragOrigin.getValue(it) }
                                alignment = null
                                fingerY = point.y - scroll.value
                                if (draggingId != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                draggingId?.let { id ->
                                    change.consume()
                                    moveDrag[0](amount / density.density)
                                    fingerY = change.position.y - scroll.value
                                }
                            },
                            onDragEnd = {
                                WidgetHostStore.setPositions(context, positions.toMap())
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draggingId = null
                                alignment = null
                            },
                            onDragCancel = {
                                if (draggingId != null && editing) positions.putAll(dragOrigin)
                                draggingId = null
                                alignment = null
                            }
                        )
                    }) {
                        if (draggingId != null) Canvas(Modifier.matchParentSize()) {
                            val guide = alignment
                            val color = Lumen.BrandGold.copy(alpha = .7f)
                            val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
                            guide?.guideX?.let { x -> drawLine(color, Offset(x.dp.toPx(), 0f),
                                Offset(x.dp.toPx(), size.height), strokeWidth = 1.dp.toPx(), pathEffect = dash) }
                            guide?.guideY?.let { y -> drawLine(color, Offset(0f, y.dp.toPx()),
                                Offset(size.width, y.dp.toPx()), strokeWidth = 1.dp.toPx(), pathEffect = dash) }
                        }
                        ids.forEach { id ->
                            key(id) {
                                val info = manager.getAppWidgetInfo(id)
                                if (info != null) {
                                    val pos = shown.getValue(id)
                                    val active = draggingId == id
                                    val motion = snap<Float>()
                                    val left by animateFloatAsState(pos.xFraction * (boardWidth - widthOf(id)), motion, label = "widget-left-$id")
                                    val top by animateFloatAsState(pos.yDp, motion, label = "widget-top-$id")
                                    WidgetBoardCard(
                                        id = id, info = info, host = host, manager = manager, kind = kindOf(id),
                                        columns = layoutSizes.getValue(id).first,
                                        heightDp = layoutSizes.getValue(id).second,
                                        editing = editing, dragging = draggingId == id,
                                        modifier = Modifier.offset {
                                            IntOffset((left * density.density).roundToInt(), (top * density.density).roundToInt())
                                        }.width(widthOf(id).dp),
                                        onRemove = {
                                            host.deleteAppWidgetId(id)
                                            WidgetHostStore.remove(context, setOf(id))
                                        },
                                        onResizeLive = { columns, height ->
                                            WidgetHostStore.resize(context, id, columns, height, kindOf(id))
                                            sizingRevision++
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (picker) {
        WidgetPickerDialog(
            space = space,
            onDismiss = { picker = false },
            onAdded = { /* prefs listener refreshes ids */ },
            initialAppWidgets = appWidgetTab
        )
    }
}

private val WidgetSelectGold = Color(0xFFFAC775)

private enum class WidgetResizeEdge { Left, Right, Top, Bottom, TopLeft, TopRight, BottomLeft, BottomRight }

@Composable
internal fun WidgetBoardCard(
    id: Int,
    info: AppWidgetProviderInfo,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    kind: WidgetKind,
    columns: Int,
    heightDp: Int,
    editing: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onResizeLive: (columns: Int, heightDp: Int) -> Unit,
    /** When false (Home), width stays full-row — no half-size beside icons. */
    allowHalfWidth: Boolean = true,
    onDone: (() -> Unit)? = null
) {
    val widgetEditSession = LocalWidgetEditSession.current
    val selectionOwner = remember(id) { Any() }
    var widgetBounds by remember(id) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val latestDone by rememberUpdatedState(onDone)
    DisposableEffect(widgetEditSession, selectionOwner, editing) {
        if (editing) {
            widgetEditSession.acquire(
                owner = selectionOwner,
                exclusive = onDone != null,
                onOutside = { latestDone?.invoke() }
            )
            widgetEditSession.updateBounds(selectionOwner, widgetBounds)
        }
        onDispose { widgetEditSession.release(selectionOwner) }
    }
    val context = LocalContext.current
    val external = WidgetHostStore.external(context, id)
    var nativeWidget by remember(id) { mutableStateOf<android.appwidget.AppWidgetHostView?>(null) }
    var transparent by remember(id, editing) { mutableStateOf(WidgetHostStore.transparent(context, id)) }
    var showSettings by remember(id, editing) { mutableStateOf(false) }
    var resizing by remember(id) { mutableStateOf(false) }
    var menuVisible by remember(id, editing) { mutableStateOf(true) }
    var nativeWidth by remember(id) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var liveColumns by remember(id, columns) { mutableIntStateOf(columns.coerceIn(1, 2)) }
    var liveHeight by remember(id, heightDp) { mutableFloatStateOf(heightDp.toFloat()) }
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    var lastPersistedHeight by remember(id) { mutableIntStateOf(heightDp) }
    var lastPersistedColumns by remember(id) { mutableIntStateOf(columns.coerceIn(1, 2)) }
    val shape = RoundedCornerShape(26.dp)
    val latestResize by rememberUpdatedState(onResizeLive)
    val displayHeight = liveHeight.roundToInt().coerceAtLeast(1)

    LaunchedEffect(columns, heightDp, dragging, resizing) {
        if (!dragging && !resizing) {
            liveColumns = columns.coerceIn(1, 2)
            liveHeight = heightDp.toFloat()
            lastPersistedHeight = heightDp
            lastPersistedColumns = columns.coerceIn(1, 2)
        }
    }
    LaunchedEffect(nativeWidth, displayHeight, resizing) {
        if (nativeWidth > 0) {
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, nativeWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, nativeWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (displayHeight - 16).coerceAtLeast(1))
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (displayHeight - 16).coerceAtLeast(1))
            })
        }
    }
    LaunchedEffect(editing) {
        if (!editing) showSettings = false
    }

    Box(
        modifier
            .height(displayHeight.dp)
            .zIndex(if (dragging || editing) 8f else 0f)
            .graphicsLayer {
                if (dragging) {
                    shadowElevation = 0f
                    alpha = 0.96f
                }
            }
            .onGloballyPositioned {
                cardWidthPx = it.size.width.toFloat().coerceAtLeast(1f)
                widgetBounds = it.boundsInRoot()
                widgetEditSession.updateBounds(selectionOwner, widgetBounds)
            }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(8.dp)
                .border(
                    width = if (editing) 2.dp else 0.6.dp,
                    color = if (editing) Color.Transparent else Lumen.BrandGold.copy(alpha = 0.24f),
                    shape = shape
                )
                .clip(shape)
        ) {
            AndroidView(
                factory = {
                    host.createView(it, id, info).apply {
                        setPadding(0, 0, 0, 0)
                        nativeWidget = this
                        if (!external) WidgetTextFit.install(this)
                        WidgetBackgroundStyle.install(this, context, id)
                    }
                },
                update = { WidgetBackgroundStyle.apply(it, context, id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .onSizeChanged { size ->
                        nativeWidth = with(density) { size.width.toDp().value.toInt() }
                    }
            )

            if (editing) {
                Box(Modifier.matchParentSize().clickable(onClickLabel = "Widget options") { menuVisible = true })
            }
        }

        if (editing) Canvas(Modifier.matchParentSize()) {
            val inset = 1.dp.toPx()
            drawRoundRect(
                color = WidgetSelectGold.copy(alpha = if (dragging) .7f else .38f),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
            )
            // Rounded corner grips sit inside measured bounds, with no circular dots.
            val radius = 12.dp.toPx()
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            val diameter = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            listOf(
                Triple(Offset(inset, inset), 180f, 90f),
                Triple(Offset(size.width - inset - radius * 2, inset), 270f, 90f),
                Triple(Offset(inset, size.height - inset - radius * 2), 90f, 90f),
                Triple(Offset(size.width - inset - radius * 2, size.height - inset - radius * 2), 0f, 90f)
            ).forEach { (origin, start, sweep) ->
                drawArc(WidgetSelectGold, start, sweep, false, origin, diameter, style = stroke)
            }

        }

        // Anchor outside the selected widget, leaving the resize border unobstructed.
        if (editing && menuVisible && !resizing && !dragging) {
            val gap = with(density) { 10.dp.roundToPx() }
            val popupPosition = remember(gap) {
                object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                            .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
                        val below = anchorBounds.bottom + gap
                        val y = if (below + popupContentSize.height <= windowSize.height - gap) below
                            else (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(gap)
                        return IntOffset(x, y)
                    }
                }
            }
            Popup(
                popupPositionProvider = popupPosition,
                // Outside taps on the gold border must resize — never end edit from the popup.
                onDismissRequest = { menuVisible = false },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
            ) {
                WidgetEditPopup(
                    title = if (external) info.loadLabel(context.packageManager).toString() else kind.title,
                    showSettings = showSettings,
                    transparent = transparent,
                    wide = liveColumns == 2,
                    canChangeWidth = allowHalfWidth && (external || !kind.wide),
                    onRemove = onRemove,
                    onResize = { showSettings = false; menuVisible = false },
                    onOpenSettings = { showSettings = true },
                    onBack = { showSettings = false },
                    onToggleBackground = {
                        transparent = !transparent
                        WidgetHostStore.setTransparent(context, id, transparent)
                        nativeWidget?.let { WidgetBackgroundStyle.apply(it, context, id) }
                    },
                    onToggleWidth = {
                        if (!allowHalfWidth || (kind.wide && !external)) return@WidgetEditPopup
                        liveColumns = if (liveColumns == 2) 1 else 2
                        lastPersistedColumns = liveColumns
                        lastPersistedHeight = displayHeight
                        latestResize(liveColumns, displayHeight)
                    },
                    onDone = onDone
                )
            }
        }

        if (editing) {
            WidgetResizeHandles(
                id = id,
                kind = kind,
                cardWidthPx = cardWidthPx,
                liveColumns = liveColumns,
                liveHeight = liveHeight,
                allowHalfWidth = allowHalfWidth && (external || !kind.wide),
                onResizing = { active ->
                    resizing = active
                    if (active) menuVisible = false
                },
                onLive = { cols, height ->
                    // Width snaps only between 1 and 2 columns — never continuous mm widths.
                    val nextCols = WidgetContentRules.columns(cols)
                        .coerceAtLeast(if (kind.wide && !external) 2 else 1)
                    liveColumns = nextCols
                    liveHeight = height
                    val rounded = height.roundToInt()
                    val widthChanged = nextCols != lastPersistedColumns
                    val heightChanged = kotlin.math.abs(rounded - lastPersistedHeight) >= 1
                    if (widthChanged || heightChanged) {
                        lastPersistedColumns = nextCols
                        lastPersistedHeight = rounded
                        latestResize(nextCols, rounded)
                    }
                },
                onCommit = { cols, height ->
                    val nextHeight = WidgetHostStore.constrainHeight(context, id, height.roundToInt(), kind)
                    val nextCols = WidgetContentRules.columns(cols)
                        .coerceAtLeast(if (kind.wide && !external) 2 else 1)
                    liveHeight = nextHeight.toFloat()
                    liveColumns = nextCols
                    lastPersistedHeight = nextHeight
                    lastPersistedColumns = nextCols
                    latestResize(nextCols, nextHeight)
                    resizing = false
                    // Stay selected with gold frame; tap empty space to exit (SL-style).
                    menuVisible = false
                }
            )
        }
    }
}

@Composable
private fun WidgetEditPopup(
    title: String,
    showSettings: Boolean,
    transparent: Boolean,
    wide: Boolean,
    canChangeWidth: Boolean,
    onRemove: () -> Unit,
    onResize: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onToggleBackground: () -> Unit,
    onToggleWidth: () -> Unit,
    onDone: (() -> Unit)?
) {
    val card = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(224.dp)
            .shadow(18.dp, card, clip = false)
            .clip(card)
            .background(Color(0xF21A121C))
            .border(1.dp, WidgetSelectGold.copy(0.45f), card)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (showSettings) "Settings" else title,
                color = Lumen.Text,
                fontFamily = Outfit,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onDone != null) {
                Text(
                    "Done",
                    color = WidgetSelectGold,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDone)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(0.08f))
        )
        if (showSettings) {
            WidgetPopupAction(
                Icons.Outlined.LayersClear,
                if (transparent) "Restore background" else "Remove background",
                onToggleBackground
            )
            if (canChangeWidth) {
                WidgetPopupAction(
                    if (wide) Icons.Outlined.WidthNormal else Icons.Outlined.WidthWide,
                    if (wide) "Make compact" else "Make wide",
                    onToggleWidth
                )
            }
            WidgetPopupAction(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        } else {
            WidgetPopupAction(Icons.Outlined.AspectRatio, "Resize", onResize)
            WidgetPopupAction(
                Icons.Outlined.LayersClear,
                if (transparent) "Restore background" else "Remove background",
                onToggleBackground
            )
            WidgetPopupAction(Icons.Outlined.Settings, "Settings", onOpenSettings)
            WidgetPopupAction(Icons.Outlined.Delete, "Remove", onRemove, danger = true)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun WidgetPopupAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) Color(0xFFFF8A80) else WidgetSelectGold,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (danger) Color(0xFFFF8A80) else Lumen.Text,
            fontFamily = Outfit,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun BoxScope.WidgetResizeHandles(
    id: Int,
    kind: WidgetKind,
    cardWidthPx: Float,
    liveColumns: Int,
    liveHeight: Float,
    allowHalfWidth: Boolean,
    onResizing: (Boolean) -> Unit,
    onLive: (columns: Int, heightDp: Float) -> Unit,
    onCommit: (columns: Int, heightDp: Float) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val minimum = WidgetHostStore.minimum(context, id, kind).toFloat()
    val maximum = WidgetHostStore.maximum(context, id).toFloat()
    val colsRef = rememberUpdatedState(liveColumns)
    val heightRef = rememberUpdatedState(liveHeight)
    val widthRef = rememberUpdatedState(cardWidthPx)
    val onLiveRef = rememberUpdatedState(onLive)
    val onCommitRef = rememberUpdatedState(onCommit)
    val onResizingRef = rememberUpdatedState(onResizing)
    val hit = 36.dp
    // All grips visible; horizontal drag only snaps between half/full columns.
    val edges = listOf(
        Alignment.CenterStart to WidgetResizeEdge.Left,
        Alignment.CenterEnd to WidgetResizeEdge.Right,
        Alignment.TopCenter to WidgetResizeEdge.Top,
        Alignment.BottomCenter to WidgetResizeEdge.Bottom,
        Alignment.TopStart to WidgetResizeEdge.TopLeft,
        Alignment.TopEnd to WidgetResizeEdge.TopRight,
        Alignment.BottomStart to WidgetResizeEdge.BottomLeft,
        Alignment.BottomEnd to WidgetResizeEdge.BottomRight
    )
    edges.forEach { (align, edge) ->
        Box(
            Modifier
                .align(align)
                .zIndex(12f)
                .then(when (edge) {
                    WidgetResizeEdge.Left, WidgetResizeEdge.Right -> Modifier.width(hit).fillMaxHeight()
                    WidgetResizeEdge.Top, WidgetResizeEdge.Bottom -> Modifier.height(hit).fillMaxWidth()
                    else -> Modifier.size(hit)
                })
                .pointerInput(id, kind, edge, allowHalfWidth) {
                    var accumX = 0f
                    var heightFloat = heightRef.value
                    var cols = colsRef.value
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                        )
                        down.consume()
                        onResizingRef.value(true)
                        accumX = 0f
                        heightFloat = heightRef.value
                        cols = colsRef.value
                        try {
                            while (true) {
                                val event = awaitPointerEvent(
                                    androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                )
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val amount = change.position - change.previousPosition
                                change.consume()
                                if (!change.pressed) break
                                val dy = with(density) { amount.y.toDp().value }
                                when (edge) {
                                    WidgetResizeEdge.Top, WidgetResizeEdge.TopLeft, WidgetResizeEdge.TopRight ->
                                        heightFloat = (heightFloat - dy).coerceIn(minimum, maximum)
                                    WidgetResizeEdge.Bottom, WidgetResizeEdge.BottomLeft, WidgetResizeEdge.BottomRight ->
                                        heightFloat = (heightFloat + dy).coerceIn(minimum, maximum)
                                    else -> Unit
                                }
                                when (edge) {
                                    WidgetResizeEdge.Left, WidgetResizeEdge.TopLeft, WidgetResizeEdge.BottomLeft ->
                                        accumX -= amount.x
                                    WidgetResizeEdge.Right, WidgetResizeEdge.TopRight, WidgetResizeEdge.BottomRight ->
                                        accumX += amount.x
                                    else -> Unit
                                }
                                // Discrete half ↔ full only (no continuous width).
                                if (allowHalfWidth &&
                                    edge != WidgetResizeEdge.Top && edge != WidgetResizeEdge.Bottom
                                ) {
                                    cols = when {
                                        accumX > widthRef.value * 0.22f -> 2
                                        accumX < -widthRef.value * 0.16f -> 1
                                        else -> colsRef.value
                                    }
                                } else if (!allowHalfWidth) {
                                    cols = colsRef.value.coerceAtLeast(if (kind.wide) 2 else 1)
                                }
                                onLiveRef.value(cols, heightFloat)
                            }
                        } finally {
                            onCommitRef.value(cols, heightFloat)
                            onResizingRef.value(false)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (edge == WidgetResizeEdge.Left || edge == WidgetResizeEdge.Right ||
                edge == WidgetResizeEdge.Top || edge == WidgetResizeEdge.Bottom) {
                val vertical = edge == WidgetResizeEdge.Left || edge == WidgetResizeEdge.Right
                Box(Modifier
                    .align(align)
                    .size(width = if (vertical) 3.dp else 24.dp,
                        height = if (vertical) 24.dp else 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WidgetSelectGold))
            }
        }
    }
}

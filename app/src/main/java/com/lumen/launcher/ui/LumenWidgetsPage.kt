package com.lumen.launcher.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.widgets.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LumenWidgetsPage(isActive: Boolean) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val host = remember { AppWidgetHost(context.applicationContext, WidgetHostStore.HOST_ID) }
    var ids by remember { mutableStateOf(WidgetHostStore.ids(context)) }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var sizingRevision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val prefs = remember { WidgetHostStore.prefs(context) }
    val pending = remember { context.getSharedPreferences("widget_binding", Context.MODE_PRIVATE) }
    var binding by remember { mutableStateOf(pending.getInt("id", -1) != -1) }
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

    fun finishBinding(id: Int, success: Boolean) {
        if (id != -1 && success && manager.getAppWidgetInfo(id)?.provider?.packageName == context.packageName) {
            if (ids.isNotEmpty()) {
                val bottom = ids.maxOf { existing ->
                    val kind = WidgetKind.entries.firstOrNull {
                        manager.getAppWidgetInfo(existing)?.provider == LumenWidgetProvider.component(context, it)
                    } ?: WidgetKind.Tasks
                    (positions[existing] ?: WidgetHostStore.position(context, existing) ?: WidgetPosition(0f, 0f)).yDp +
                        WidgetHostStore.height(context, existing, kind) + 10f
                }
                WidgetHostStore.setPosition(context, id, WidgetPosition(0f, bottom))
            }
            WidgetHostStore.add(context, id)
            picker = false
            scope.launch(Dispatchers.IO) { runCatching { LumenWidgetProvider.refreshAll(context) } }
        } else if (id != -1) {
            host.deleteAppWidgetId(id)
            message = "Widget wasn't added. You can try again."
        }
        pending.edit().remove("id").apply()
        binding = false
    }
    val bindPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pending.getInt("id", -1)
        finishBinding(id, result.resultCode == android.app.Activity.RESULT_OK)
    }
    fun add(kind: WidgetKind) {
        if (binding) return
        message = ""
        val id = host.allocateAppWidgetId()
        pending.edit().putInt("id", id).apply()
        binding = true
        val component = LumenWidgetProvider.component(context, kind)
        try {
            if (manager.bindAppWidgetIdIfAllowed(id, component)) finishBinding(id, true)
            else bindPermission.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
            )
        } catch (_: Exception) {
            finishBinding(id, false)
        }
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ids") {
                ids = WidgetHostStore.ids(context)
                positionRevision++
            } else if (key?.startsWith("x_") == true || key?.startsWith("y_") == true) {
                positionRevision++
            } else if (key?.startsWith("columns_") == true || key?.startsWith("height_") == true) {
                sizingRevision++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(host, isActive) {
        if (isActive) host.startListening()
        onDispose { host.stopListening() }
    }
    LaunchedEffect(isActive) {
        if (isActive) {
            val interrupted = pending.getInt("id", -1)
            if (interrupted != -1) finishBinding(interrupted, manager.getAppWidgetInfo(interrupted) != null)
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
                Text("Lumen widgets", color = Lumen.Text, fontFamily = Outfit, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
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
        var data by remember { mutableStateOf(WidgetSnapshot()) }
        var previews by remember { mutableStateOf<Map<WidgetKind, android.widget.RemoteViews>>(emptyMap()) }
        var quoteRevision by remember { mutableIntStateOf(0) }
        DisposableEffect(Unit) {
            val quotePrefs = context.getSharedPreferences("lumen_widget_quotes", Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> quoteRevision++ }
            quotePrefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { quotePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        LaunchedEffect(Unit) {
            com.lumen.launcher.social.CreationRepository(context).items.collect {
                data = withContext(Dispatchers.IO) {
                    runCatching { WidgetRepository.load(context) }.getOrDefault(WidgetSnapshot())
                }
            }
        }
        LaunchedEffect(data, quoteRevision) {
            previews = withContext(Dispatchers.IO) {
                WidgetKind.entries.associateWith { WidgetRenderer.views(context, 0, it, data) }
            }
        }
        Dialog(
            onDismissRequest = { if (!binding) picker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Glass(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(12.dp),
                shape = RoundedCornerShape(28.dp),
                airy = true
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Add a Lumen widget",
                            color = Lumen.Text,
                            fontFamily = Outfit,
                            fontSize = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { if (!binding) picker = false }) {
                            Icon(Icons.Outlined.Close, "Close widget picker", tint = Lumen.Accent)
                        }
                    }
                    Text(
                        "Tap a card to add it to this home page",
                        color = Lumen.Muted,
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            WidgetKind.entries,
                            key = { it.name },
                            span = { GridItemSpan(if (it.wide) 2 else 1) }
                        ) { kind ->
                            Column {
                                Box(Modifier.fillMaxWidth().height(kind.heightDp.dp)) {
                                    AndroidView(
                                        factory = {
                                            (previews[kind] ?: WidgetRenderer.views(it, 0, kind, WidgetSnapshot()))
                                                .apply(it, null)
                                        },
                                        update = { previews[kind]?.reapply(context, it) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clickable(enabled = !binding, onClickLabel = "Add ${kind.title} widget") {
                                                add(kind)
                                            }
                                    )
                                }
                                Text(
                                    kind.description,
                                    color = Lumen.Muted,
                                    fontFamily = Outfit,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetBoardCard(
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
    onResizeLive: (columns: Int, heightDp: Int) -> Unit
) {
    val density = LocalDensity.current
    var liveColumns by remember(id, columns) { mutableIntStateOf(columns) }
    var liveHeight by remember(id, heightDp) { mutableIntStateOf(heightDp) }
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    val shape = RoundedCornerShape(26.dp)
    val latestResize by rememberUpdatedState(onResizeLive)

    LaunchedEffect(columns, heightDp, dragging) {
        if (!dragging) {
            liveColumns = columns
            liveHeight = heightDp
        }
    }

    Box(
        modifier
            .height(heightDp.dp)
            .zIndex(if (dragging) 8f else 0f)
            .graphicsLayer {
                if (dragging) {
                    shadowElevation = 0f
                    alpha = 0.96f
                }
            }
            .onGloballyPositioned {
                cardWidthPx = it.size.width.toFloat().coerceAtLeast(1f)
            }
            // Keep the same clipping/layer chain when edit controls disappear.
            .border(
                width = if (dragging) 2.dp else 1.dp,
                color = if (editing) Lumen.Accent.copy(alpha = if (dragging) .7f else .3f) else Color.Transparent,
                shape = shape
            )
            .clip(shape)
    ) {
        AndroidView(
            factory = {
                host.createView(it, id, info).apply { setPadding(0, 0, 0, 0) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(liveHeight.dp)
                .onSizeChanged { size ->
                    val width = with(density) { size.width.toDp().value.toInt() }
                    manager.updateAppWidgetOptions(
                        id,
                        Bundle().apply {
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, liveHeight)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, liveHeight)
                        }
                    )
                }
        )

        if (editing) {
            // Suppress native widget actions in arrange mode. The canvas handles
            // dragging in stable coordinates, independently of the moving widget.
            Box(Modifier.matchParentSize().clickable(onClickLabel = "Hold to move widget") {})

            // Remove — small, out of the way of the resize handle.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClickLabel = "Remove widget", onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, "Remove widget", tint = Lumen.Text, modifier = Modifier.size(16.dp))
            }

            // Corner resize handle — drag to change height and width.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(40.dp)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Lumen.BrandGold.copy(alpha = 0.85f))
                    .pointerInput(id, kind) {
                        var accumX = 0f
                        var startHeight = heightDp
                        var startColumns = columns
                        detectDragGestures(
                            onDragStart = {
                                accumX = 0f
                                startHeight = liveHeight
                                startColumns = liveColumns
                            },
                            onDragEnd = {
                                liveHeight = WidgetContentRules.snapHeight(liveHeight, kind)
                                liveColumns = WidgetContentRules.columns(liveColumns)
                                latestResize(liveColumns, liveHeight)
                            },
                            onDragCancel = {
                                liveHeight = WidgetContentRules.snapHeight(liveHeight, kind)
                                latestResize(liveColumns, liveHeight)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumX += dragAmount.x
                                val heightDelta = with(density) { dragAmount.y.toDp().value }.roundToInt()
                                startHeight = WidgetContentRules.height(startHeight + heightDelta, kind)
                                val nextHeight = WidgetContentRules.snapHeight(startHeight, kind)
                                val nextColumns = when {
                                    accumX > cardWidthPx * 0.28f -> 2
                                    accumX < -cardWidthPx * 0.18f -> 1
                                    else -> startColumns
                                }
                                if (nextHeight != liveHeight || nextColumns != liveColumns) {
                                    liveHeight = nextHeight
                                    liveColumns = nextColumns
                                    latestResize(liveColumns, liveHeight)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("⌟", color = Lumen.OnAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

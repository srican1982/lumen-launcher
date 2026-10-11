package com.lumen.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.withTimeoutOrNull

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
    val view = LocalView.current
    val gridState = rememberLazyGridState()
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<Int?>(null) }
    var order by remember { mutableStateOf(ids) }
    var gridRoot by remember { mutableStateOf(Offset.Zero) }

    fun finishBinding(id: Int, success: Boolean) {
        if (id != -1 && success && manager.getAppWidgetInfo(id)?.provider?.packageName == context.packageName) {
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
                if (draggingId == null) order = ids
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
    LaunchedEffect(ids) {
        if (draggingId == null) order = ids
    }
    LaunchedEffect(editing) {
        if (!editing) {
            draggingId = null
            dragOffset = Offset.Zero
            dropTarget = null
        }
    }

    fun kindOf(id: Int): WidgetKind =
        WidgetKind.entries.firstOrNull {
            manager.getAppWidgetInfo(id)?.provider == LumenWidgetProvider.component(context, it)
        } ?: WidgetKind.Tasks

    fun commitOrder(next: List<Int>) {
        order = next
        WidgetHostStore.setOrder(context, next)
        ids = WidgetHostStore.ids(context)
    }

    fun targetIndexForDrag(rootPos: Offset): Int? {
        val info = gridState.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return null
        // LazyGrid item.offset is already in viewport coordinates.
        val local = rootPos - gridRoot
        val hit = items.minByOrNull { item ->
            val cx = item.offset.x + item.size.width / 2f
            val cy = item.offset.y + item.size.height / 2f
            val dx = local.x - cx
            val dy = local.y - cy
            dx * dx + dy * dy
        } ?: return null
        val key = hit.key as? Int ?: return null
        return order.indexOf(key).takeIf { it >= 0 }
    }

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
            Glass(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), airy = true) {
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
            val layoutSizes = remember(order, sizingRevision) {
                order.associateWith { id ->
                    val kind = kindOf(id)
                    WidgetHostStore.columns(context, id, kind) to WidgetHostStore.height(context, id, kind)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                userScrollEnabled = draggingId == null,
                contentPadding = PaddingValues(bottom = 66.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.onGloballyPositioned { gridRoot = it.positionInRoot() }
            ) {
                items(
                    items = order,
                    key = { it },
                    span = { id -> GridItemSpan(layoutSizes[id]?.first ?: 1) }
                ) { id ->
                    val info = manager.getAppWidgetInfo(id)
                    if (info != null) {
                        val kind = kindOf(id)
                        val columns = layoutSizes[id]?.first ?: 1
                        val height = layoutSizes[id]?.second ?: kind.heightDp
                        val active = draggingId == id
                        WidgetBoardCard(
                            id = id,
                            info = info,
                            host = host,
                            manager = manager,
                            kind = kind,
                            columns = columns,
                            heightDp = height,
                            editing = editing,
                            dragging = active,
                            dragOffset = if (active) dragOffset else Offset.Zero,
                            modifier = Modifier.animateItem(
                                placementSpec = if (active) null else androidx.compose.animation.core.spring(stiffness = 500f)
                            ),
                            onRemove = {
                                host.deleteAppWidgetId(id)
                                WidgetHostStore.remove(context, setOf(id))
                            },
                            onResizeLive = { nextColumns, nextHeight ->
                                WidgetHostStore.resize(context, id, nextColumns, nextHeight, kind)
                            },
                            dropHighlight = dropTarget != null && order.getOrNull(dropTarget!!) == id && draggingId != id,
                            onDragStart = {
                                draggingId = id
                                dragOffset = Offset.Zero
                                dropTarget = order.indexOf(id).takeIf { it >= 0 }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            },
                            onDrag = { delta, fingerRoot ->
                                dragOffset += delta
                                val target = targetIndexForDrag(fingerRoot)
                                if (target != null && target != dropTarget) {
                                    dropTarget = target
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                val to = dropTarget
                                if (to != null) {
                                    WidgetHostStore.moveTo(context, id, to)
                                    order = WidgetHostStore.ids(context)
                                    ids = order
                                }
                                draggingId = null
                                dragOffset = Offset.Zero
                                dropTarget = null
                            }
                        )
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
    dragOffset: Offset,
    dropHighlight: Boolean = false,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onResizeLive: (columns: Int, heightDp: Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (delta: Offset, rootPos: Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    var liveColumns by remember(id, columns) { mutableIntStateOf(columns) }
    var liveHeight by remember(id, heightDp) { mutableIntStateOf(heightDp) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    val shape = RoundedCornerShape(26.dp)
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val latestResize by rememberUpdatedState(onResizeLive)

    LaunchedEffect(columns, heightDp, dragging) {
        if (!dragging) {
            liveColumns = columns
            liveHeight = heightDp
        }
    }

    Box(
        modifier
            .zIndex(if (dragging) 8f else 0f)
            .graphicsLayer {
                if (dragging) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.03f
                    scaleY = 1.03f
                    shadowElevation = 18f
                    alpha = 0.96f
                }
            }
            .onGloballyPositioned {
                rootOrigin = it.positionInRoot()
                cardWidthPx = it.size.width.toFloat().coerceAtLeast(1f)
            }
            .then(
                if (editing) {
                    Modifier
                        .border(
                            width = if (dropHighlight) 2.dp else 1.2.dp,
                            color = Lumen.Accent.copy(alpha = if (dropHighlight) 0.95f else 0.55f),
                            shape = shape
                        )
                        .clip(shape)
                } else Modifier
            )
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
            // Blocks widget taps while arranging. Hold briefly, then drag to reorder.
            // (Pinch was removed from this layer — it stole the long-press.)
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .pointerInput(id) {
                        val slop = viewConfiguration.touchSlop
                        val holdMs = 280L
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            var finger = down.position
                            val held = withTimeoutOrNull(holdMs) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@withTimeoutOrNull false
                                    finger = change.position
                                    if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                                        return@withTimeoutOrNull false
                                    }
                                    // Early movement → let the grid scroll instead of picking up.
                                    if ((finger - down.position).getDistance() > slop) {
                                        return@withTimeoutOrNull false
                                    }
                                }
                            }
                            // null = hold completed (timeout); non-null = cancelled (up/move).
                            if (held != null) return@awaitEachGesture
                            // Long-press succeeded — consume so LazyGrid cannot steal the drag.
                            down.consume()
                            latestDragStart()
                            val pickup = finger
                            var last = finger
                            var moving = false
                            val liftOrigin = rootOrigin
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed || change.changedToUpIgnoreConsumed()) {
                                    latestDragEnd()
                                    break
                                }
                                change.consume()
                                finger = change.position
                                val total = finger - pickup
                                if (!moving && total.getDistance() > slop) moving = true
                                if (moving) {
                                    val delta = finger - last
                                    last = finger
                                    latestDrag(delta, liftOrigin + finger)
                                }
                            }
                        }
                    }
            )

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
                    .background(Lumen.Accent.copy(alpha = 0.88f))
                    .pointerInput(id, kind, heightDp, columns) {
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

package com.lumen.launcher.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.widgets.*
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun ModeWidgetBoard(space: SpaceKind, isActive: Boolean, apps: List<AppInfo>, columns: Int,
    iconSize: Dp, hasAddTile: Boolean, registerAppDrop: ((String, Offset) -> Boolean) -> Unit,
    appContent: @Composable (AppInfo, Modifier) -> Unit, addContent: @Composable (Modifier) -> Unit,
    onDraggingChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val host = remember { WidgetHostSession.host(context) }
    val prefs = remember { WidgetHostStore.prefs(context) }
    val placement = remember { context.getSharedPreferences("lumen_home_board", Context.MODE_PRIVATE) }
    val haptics = LocalHapticFeedback.current
    var revision by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var selected by remember(space) { mutableStateOf<Int?>(null) }
    var moving by remember(space) { mutableStateOf<String?>(null) }
    var desired by remember(space) { mutableStateOf<HomeBoardLayout.Cell?>(null) }
    var dragOrigin by remember { mutableStateOf(HomeBoardLayout.Cell(0, 0)) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(prefs, placement, isActive) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        placement.registerOnSharedPreferenceChangeListener(listener)
        if (isActive) WidgetHostSession.start()
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            placement.unregisterOnSharedPreferenceChangeListener(listener)
            if (isActive) WidgetHostSession.stop()
            onDraggingChanged(false)
        }
    }
    val ids = remember(space, revision) { WidgetHostStore.idsForMode(context, space.name).filter { manager.getAppWidgetInfo(it) != null } }
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInWindow() }) {
        val gap = 12f
        val cellWidth = (maxWidth.value - gap * (columns - 1)) / columns
        val legacyCellHeight = iconSize.value + 46f
        val cellHeight = 12f
        val appRows = ceil(legacyCellHeight / cellHeight).toInt()
        val kinds = ids.associateWith { id -> WidgetKind.entries.firstOrNull {
            manager.getAppWidgetInfo(id)?.provider == LumenWidgetProvider.component(context, it)
        } ?: WidgetKind.Tasks }
        // Discrete widths only: store cols 2 = full row, 1 = half row (never millimeter widths).
        val items = ids.map { id -> HomeBoardLayout.Item("widget:$id",
            if (WidgetHostStore.columns(context, id, kinds.getValue(id)) == 2) columns
            else (columns / 2).coerceAtLeast(1),
            ceil((WidgetHostStore.height(context, id, kinds.getValue(id)) + 8f) / cellHeight).toInt()) } +
            apps.map { HomeBoardLayout.Item(it.key, 1, appRows, rowStep = appRows) } +
            if (hasAddTile) listOf(HomeBoardLayout.Item("add", 1, appRows, rowStep = appRows)) else emptyList()
        val stored = remember(space, revision, items) { items.mapNotNull { item ->
            val key = "${space.name}:${item.key}"
            if (placement.contains("row:$key")) item.key to HomeBoardLayout.Cell(placement.getInt("col:$key", 0), placement.getInt("row:$key", 0)) else null
        }.toMap() }
        val migrating = placement.getInt("grid_version:${space.name}", 1) < 2
        val saved = if (migrating) {
            val legacyItems = items.map { item ->
                val rows = if (item.key.startsWith("widget:")) {
                    val id = item.key.removePrefix("widget:").toInt()
                    ceil((WidgetHostStore.height(context, id, kinds.getValue(id)) + 8f) / legacyCellHeight).toInt()
                } else 1
                item.copy(rows = rows, rowStep = 1)
            }
            val oldLayout = HomeBoardLayout.arrange(legacyItems, columns, stored)
            oldLayout.mapValues { (_, cell) ->
                val reclaimed = legacyItems.filter { it.columns == columns && it.key.startsWith("widget:") &&
                    oldLayout.getValue(it.key).row + it.rows <= cell.row }.sumOf { item ->
                    val fine = items.first { it.key == item.key }
                    (item.rows * legacyCellHeight - fine.rows * cellHeight).coerceAtLeast(0f).toDouble()
                }.toFloat()
                HomeBoardLayout.Cell(cell.column, ((cell.row * legacyCellHeight - reclaimed) / cellHeight).roundToInt().coerceAtLeast(0))
            }
        } else stored
        val compactionRevision = prefs.getInt("compaction_${space.name}", 0)
        val compacting = compactionRevision != placement.getInt("compacted:${space.name}", 0)
        val layout = if (compacting && moving == null) HomeBoardLayout.compact(items, columns, saved)
            else HomeBoardLayout.arrange(items, columns, saved, moving, desired)
        LaunchedEffect(space, migrating, compactionRevision, moving) {
            if (migrating || (compacting && moving == null)) {
                val edit = placement.edit().putInt("grid_version:${space.name}", 2)
                    .putInt("compacted:${space.name}", compactionRevision)
                layout.forEach { (key, cell) -> edit.putInt("row:${space.name}:$key", cell.row).putInt("col:${space.name}:$key", cell.column) }
                edit.apply()
            }
        }
        fun persist(positions: Map<String, HomeBoardLayout.Cell>) {
            val edit = placement.edit()
            positions.forEach { (key, cell) -> edit.putInt("row:${space.name}:$key", cell.row).putInt("col:${space.name}:$key", cell.column) }
            edit.apply()
        }
        fun target(point: Offset) = HomeBoardLayout.Cell((((point.x - origin.x) / density.density - cellWidth / 2f) / (cellWidth + gap)).roundToInt(),
            (((point.y - origin.y) / density.density - iconSize.value / 2f) / cellHeight).roundToInt())
        SideEffect {
            registerAppDrop { key, point ->
                if (apps.none { it.key == key }) false else {
                    persist(HomeBoardLayout.arrange(items, columns, layout, key, target(point)))
                    true
                }
            }
        }
        val height = items.maxOfOrNull { (layout.getValue(it.key).row + it.rows) * cellHeight } ?: 0f
        Box(Modifier.fillMaxWidth().height(height.dp)) {
            items.forEach { item ->
                key(item.key) {
                    val cell = layout.getValue(item.key)
                    val width = cellWidth * item.columns + gap * (item.columns - 1)
                    val modifier = Modifier.offset(x = (cell.column * (cellWidth + gap)).dp, y = (cell.row * cellHeight).dp).width(width.dp)
                    when {
                        item.key == "add" -> addContent(modifier)
                        item.key.startsWith("widget:") -> {
                            val id = item.key.removePrefix("widget:").toInt()
                            val info = manager.getAppWidgetInfo(id)
                            if (info != null && isActive) Box(modifier.widgetBoardDrag(
                                key = id,
                                immediate = selected == id,
                                onLongPress = {
                                    selected = id
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onStart = {
                                    selected = id
                                    dragOrigin = layout.getValue(item.key)
                                    delta = Offset.Zero
                                    moving = item.key
                                    desired = dragOrigin
                                    onDraggingChanged(true)
                                },
                                onDrag = { amount ->
                                    delta += amount / density.density
                                    desired = HomeBoardLayout.Cell(dragOrigin.column + (delta.x / (cellWidth + gap)).roundToInt(),
                                        dragOrigin.row + (delta.y / cellHeight).roundToInt())
                                },
                                onEnd = {
                                    persist(HomeBoardLayout.arrange(items, columns, saved, moving, desired))
                                    moving = null; desired = null; onDraggingChanged(false)
                                },
                                onCancel = { moving = null; desired = null; onDraggingChanged(false) }
                            )) {
                                WidgetBoardCard(id, info, host, manager, kinds.getValue(id),
                                    WidgetHostStore.columns(context, id, kinds.getValue(id)), WidgetHostStore.height(context, id, kinds.getValue(id)),
                                    editing = selected == id, dragging = moving == item.key,
                                    modifier = Modifier.fillMaxWidth(),
                                    onRemove = { host.deleteAppWidgetId(id); WidgetHostStore.remove(context, setOf(id)); selected = null },
                                    onResizeLive = { cols, height ->
                                        val kind = kinds.getValue(id)
                                        val prevCols = WidgetHostStore.columns(context, id, kind)
                                        WidgetHostStore.resize(context, id, cols, height, kind)
                                        // Keep the widget on a valid horizontal grid column after
                                        // snapping between half and full width.
                                        if (cols != prevCols) {
                                            val key = "widget:$id"
                                            val span = if (cols == 2) columns else (columns / 2).coerceAtLeast(1)
                                            val colKey = "col:${space.name}:$key"
                                            val rowKey = "row:${space.name}:$key"
                                            val col = placement.getInt(colKey, 0)
                                                .coerceIn(0, (columns - span).coerceAtLeast(0))
                                            placement.edit()
                                                .putInt(colKey, col)
                                                .putInt(rowKey, placement.getInt(rowKey, 0))
                                                .apply()
                                        }
                                    },
                                    allowHalfWidth = true,
                                    onDone = { selected = null })
                            }
                        }
                        else -> apps.find { it.key == item.key }?.let { appContent(it, modifier) }
                    }
                }
            }
        }
    }
}

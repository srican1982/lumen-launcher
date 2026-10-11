package com.lumen.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.widgets.*
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
    var resizeTarget by remember { mutableStateOf<Int?>(null) }
    var sizingRevision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val prefs = remember { WidgetHostStore.prefs(context) }
    val pending = remember { context.getSharedPreferences("widget_binding", Context.MODE_PRIVATE) }
    var binding by remember { mutableStateOf(pending.getInt("id", -1) != -1) }
    fun finishBinding(id: Int, success: Boolean) {
        if (id != -1 && success && manager.getAppWidgetInfo(id)?.provider?.packageName == context.packageName) {
            WidgetHostStore.add(context, id)
            picker = false
            scope.launch(Dispatchers.IO) { runCatching { LumenWidgetProvider.refreshAll(context) } }
        } else if (id != -1) { host.deleteAppWidgetId(id); message = "Widget wasn't added. You can try again." }
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
            else bindPermission.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component))
        } catch (_: Exception) { finishBinding(id, false) }
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ids") ids = WidgetHostStore.ids(context)
            else if (key?.startsWith("columns_") == true || key?.startsWith("height_") == true) sizingRevision++
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
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lumen widgets", color = Lumen.Text, fontFamily = Outfit, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("A little space for what matters", color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
            }
            TextButton(onClick = { editing = !editing }, enabled = ids.isNotEmpty()) {
                Icon(if (editing) Icons.Outlined.Close else Icons.Outlined.Tune, if (editing) "Finish editing" else "Arrange widgets", tint = Lumen.BrandGold)
            }
            TextButton(onClick = { picker = true }) { Icon(Icons.Outlined.Add, "Add widget", tint = Lumen.BrandGold) }
        }
        if (message.isNotEmpty()) Text(message, color = Lumen.BrandGold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        if (ids.isEmpty()) {
            Column(Modifier.fillMaxWidth().denseGlass(RoundedCornerShape(24.dp)).padding(24.dp)) {
                Text("Make room for your day", color = Lumen.Text, fontFamily = Outfit, fontSize = 21.sp)
                Text("Trip, tasks, people, photos and quiet moments. Add the widgets you want to keep close.", color = Lumen.Muted, fontFamily = Outfit, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp))
                TextButton(onClick = { picker = true }) { Text("Choose widgets", color = Lumen.BrandGold) }
            }
        } else {
            val layoutSizes = remember(ids, sizingRevision) { ids.associateWith { id ->
                val kind = WidgetKind.entries.firstOrNull { manager.getAppWidgetInfo(id)?.provider == LumenWidgetProvider.component(context, it) } ?: WidgetKind.Tasks
                WidgetHostStore.columns(context, id, kind) to WidgetHostStore.height(context, id, kind)
            } }
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 66.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ids, key = { it }, span = { id ->
                GridItemSpan(layoutSizes[id]?.first ?: 1)
            }) { id ->
                val info = manager.getAppWidgetInfo(id)
                if (info != null) Column {
                    val density = LocalDensity.current
                    val height = layoutSizes[id]?.second ?: 132
                    AndroidView(factory = { host.createView(it, id, info).apply { setPadding(0, 0, 0, 0) } },
                        modifier = Modifier.fillMaxWidth().height(height.dp).onSizeChanged { size ->
                            val width = with(density) { size.width.toDp().value.toInt() }
                            manager.updateAppWidgetOptions(id, Bundle().apply {
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                            })
                        })
                    if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { WidgetHostStore.move(context, id, -1) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("\u2191", color = Lumen.BrandGold) }
                        TextButton(onClick = { WidgetHostStore.move(context, id, 1) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("\u2193", color = Lumen.BrandGold) }
                        TextButton(onClick = { resizeTarget = id }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Size", color = Lumen.BrandGold, fontSize = 11.sp) }
                        TextButton(onClick = { host.deleteAppWidgetId(id); WidgetHostStore.remove(context, setOf(id)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Remove", color = Lumen.Muted, fontSize = 11.sp) }
                    }
                }
            }
        }
        }
    }
    resizeTarget?.let { id ->
        val kind = WidgetKind.entries.firstOrNull { manager.getAppWidgetInfo(id)?.provider == LumenWidgetProvider.component(context, it) }
        if (kind != null) {
            var columns by remember(id) { mutableIntStateOf(WidgetHostStore.columns(context, id, kind)) }
            var height by remember(id) { mutableIntStateOf(WidgetHostStore.height(context, id, kind)) }
            Dialog(onDismissRequest = { resizeTarget = null }) {
                Column(Modifier.fillMaxWidth().denseGlass(RoundedCornerShape(24.dp)).padding(20.dp)) {
                    Text("Resize ${kind.title}", color = Lumen.Text, fontFamily = Outfit, fontSize = 21.sp)
                    Text("Width", color = Lumen.Muted, modifier = Modifier.padding(top = 14.dp))
                    Row { listOf(1, 2).forEach { value ->
                        TextButton(onClick = { columns = value }) { Text((if (columns == value) "\u2713 " else "") + if (value == 1) "Half" else "Full", color = Lumen.BrandGold) }
                    } }
                    Text("Height", color = Lumen.Muted)
                    Column { listOf(96, 132, 180, 240, 300).filter { WidgetContentRules.height(it, kind) == it }.forEach { value ->
                        TextButton(onClick = { height = value }) { Text((if (height == value) "\u2713 " else "") + "$value dp", color = Lumen.BrandGold) }
                    } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { resizeTarget = null }) { Text("Cancel", color = Lumen.Muted) }
                        TextButton(onClick = { WidgetHostStore.resize(context, id, columns, height, kind); resizeTarget = null }) { Text("Apply", color = Lumen.BrandGold) }
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
                data = withContext(Dispatchers.IO) { runCatching { WidgetRepository.load(context) }.getOrDefault(WidgetSnapshot()) }
            }
        }
        LaunchedEffect(data, quoteRevision) {
            previews = withContext(Dispatchers.IO) { WidgetKind.entries.associateWith { WidgetRenderer.views(context, 0, it, data) } }
        }
        Dialog(onDismissRequest = { if (!binding) picker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(12.dp).denseGlass(RoundedCornerShape(28.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add a Lumen widget", color = Lumen.Text, fontFamily = Outfit, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (!binding) picker = false }) { Icon(Icons.Outlined.Close, "Close widget picker", tint = Lumen.BrandGold) }
                }
                Text("Tap a card to add it to this home page", color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(WidgetKind.entries, key = { it.name }, span = { GridItemSpan(if (it.wide) 2 else 1) }) { kind ->
                        Column {
                            Box(Modifier.fillMaxWidth().height(kind.heightDp.dp)) {
                                AndroidView(factory = { (previews[kind] ?: WidgetRenderer.views(it, 0, kind, WidgetSnapshot())).apply(it, null) },
                                    update = { previews[kind]?.reapply(context, it) }, modifier = Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().clickable(enabled = !binding, onClickLabel = "Add ${kind.title} widget") { add(kind) })
                            }
                            Text(kind.description, color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

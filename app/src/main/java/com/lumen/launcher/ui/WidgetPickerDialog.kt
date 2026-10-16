package com.lumen.launcher.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.widgets.LumenWidgetProvider
import com.lumen.launcher.widgets.WidgetCategory
import com.lumen.launcher.widgets.WidgetHostSession
import com.lumen.launcher.widgets.WidgetHostStore
import com.lumen.launcher.widgets.WidgetKind
import com.lumen.launcher.widgets.WidgetPosition
import com.lumen.launcher.widgets.WidgetRenderer
import com.lumen.launcher.widgets.WidgetRepository
import com.lumen.launcher.widgets.WidgetSnapshot
import com.lumen.launcher.widgets.WidgetTextFit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browse and add Lumen widgets without leaving Home. */
@Composable
fun WidgetPickerDialog(
    space: SpaceKind,
    onDismiss: () -> Unit,
    onAdded: (() -> Unit)? = null,
    initialAppWidgets: Boolean = false
) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val host = remember { WidgetHostSession.host(context) }
    val scope = rememberCoroutineScope()
    val pending = remember { context.getSharedPreferences("widget_binding", Context.MODE_PRIVATE) }
    var bindingMessage by remember { mutableStateOf("") }
    var binding by remember { mutableStateOf(pending.getInt("id", -1) != -1) }
    var appWidgets by remember { mutableStateOf(initialAppWidgets) }
    var categoryFilter by remember { mutableStateOf<WidgetCategory?>(null) }
    var data by remember { mutableStateOf(WidgetSnapshot()) }
    var previews by remember { mutableStateOf<Map<WidgetKind, android.widget.RemoteViews>>(emptyMap()) }
    var quoteRevision by remember { mutableIntStateOf(0) }

    fun finishBinding(id: Int, success: Boolean) {
        if (id != -1 && success && manager.getAppWidgetInfo(id) != null) {
            val existing = WidgetHostStore.idsForMode(context, space.name)
            if (existing.isNotEmpty()) {
                val bottom = existing.maxOf { other ->
                    val kind = WidgetKind.entries.firstOrNull {
                        manager.getAppWidgetInfo(other)?.provider == LumenWidgetProvider.component(context, it)
                    } ?: WidgetKind.Tasks
                    (WidgetHostStore.position(context, other)?.yDp ?: 0f) +
                        WidgetHostStore.height(context, other, kind) + 10f
                }
                WidgetHostStore.setPosition(context, id, WidgetPosition(0f, bottom))
            }
            manager.getAppWidgetInfo(id)?.let { WidgetHostStore.registerProvider(context, id, it) }
            WidgetHostStore.assignMode(context, id, pending.getString("space", space.name) ?: space.name)
            WidgetHostStore.add(context, id)
            onAdded?.invoke()
            onDismiss()
            scope.launch(Dispatchers.IO) { runCatching { LumenWidgetProvider.refreshAll(context) } }
        } else if (id != -1) {
            host.deleteAppWidgetId(id)
            bindingMessage = "Widget wasn't added. You can try again."
        }
        pending.edit().clear().apply()
        binding = false
    }

    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        finishBinding(pending.getInt("id", -1), result.resultCode == android.app.Activity.RESULT_OK)
    }
    fun bound(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure != null && info.provider.packageName != context.packageName) {
            pending.edit().putString("phase", "configure").apply()
            configure.launch(Intent(context, com.lumen.launcher.widgets.WidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        } else finishBinding(id, info != null)
    }
    val bindPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pending.getInt("id", -1)
        if (result.resultCode == android.app.Activity.RESULT_OK) bound(id) else finishBinding(id, false)
    }
    fun addProvider(component: android.content.ComponentName) {
        if (binding) return
        bindingMessage = ""
        val id = host.allocateAppWidgetId()
        pending.edit().putInt("id", id).putString("space", space.name).putString("phase", "bind").apply()
        binding = true
        try {
            if (manager.bindAppWidgetIdIfAllowed(id, component)) bound(id)
            else bindPermission.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component))
        } catch (_: Exception) { finishBinding(id, false) }
    }
    fun add(kind: WidgetKind) = addProvider(LumenWidgetProvider.component(context, kind))

    DisposableEffect(Unit) {
        val quotePrefs = context.getSharedPreferences("lumen_widget_quotes", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> quoteRevision++ }
        quotePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { quotePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) {
        com.lumen.launcher.media.NowPlayingRepository.start(context)
        kotlinx.coroutines.flow.combine(
            com.lumen.launcher.social.CreationRepository(context).items,
            com.lumen.launcher.media.NowPlayingRepository.state
        ) { _, _ -> Unit }.collect {
            data = withContext(Dispatchers.IO) {
                runCatching { WidgetRepository.load(context) }.getOrDefault(WidgetSnapshot())
            }
        }
    }
    LaunchedEffect(data, quoteRevision) {
        previews = withContext(Dispatchers.Default) {
            WidgetKind.entries.associateWith { WidgetRenderer.views(context, 0, it, data) }
        }
    }

    Dialog(
        onDismissRequest = { if (!binding) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Glass(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            airy = true
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Widgets",
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { if (!binding) onDismiss() }) {
                        Icon(Icons.Outlined.Close, "Close widget picker", tint = Lumen.Accent)
                    }
                }
                Text(
                    "Tap a card to add it to ${space.title}",
                    color = Lumen.Muted,
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { appWidgets = false }, enabled = !binding) {
                        Text("Lumen widgets", color = if (!appWidgets) Lumen.BrandGold else Lumen.Muted)
                    }
                    TextButton(onClick = { appWidgets = true }, enabled = !binding) {
                        Text("App widgets", color = if (appWidgets) Lumen.BrandGold else Lumen.Muted)
                    }
                }
                if (bindingMessage.isNotEmpty()) Text(bindingMessage, color = Lumen.BrandGold, fontSize = 12.sp)
                if (appWidgets) {
                    AppWidgetBrowser(binding, { addProvider(it.provider) }, Modifier.weight(1f))
                } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    item {
                        TextButton(onClick = { categoryFilter = null }) {
                            Text("All", color = if (categoryFilter == null) Lumen.Accent else Lumen.Muted)
                        }
                    }
                    WidgetCategory.entries.forEach { category ->
                        item(key = category.name) {
                            TextButton(onClick = { categoryFilter = category }) {
                                Text(category.title, color = if (categoryFilter == category) Lumen.Accent else Lumen.Muted)
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WidgetKind.pickerSections().filter { categoryFilter == null || it.first == categoryFilter }
                        .forEach { (category, kinds) ->
                            item(key = "cat_${category.name}", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    category.title,
                                    color = Lumen.Text,
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(kinds, key = { it.name }, span = { GridItemSpan(if (it.wide) 2 else 1) }) { kind ->
                                Column {
                                    Box(Modifier.fillMaxWidth().height(kind.heightDp.dp).clip(RoundedCornerShape(26.dp))) {
                                        key(kind.name) {
                                            AndroidView(
                                                factory = { ctx ->
                                                    runCatching {
                                                        (previews[kind] ?: WidgetRenderer.views(ctx, 0, kind, WidgetSnapshot()))
                                                            .apply(ctx, null)
                                                    }.getOrElse {
                                                        android.widget.TextView(ctx).apply {
                                                            text = kind.title
                                                            setTextColor(android.graphics.Color.WHITE)
                                                            setPadding(24, 24, 24, 24)
                                                        }
                                                    }.also { WidgetTextFit.install(it) }
                                                },
                                                update = { hostView ->
                                                    val remote = previews[kind] ?: return@AndroidView
                                                    runCatching { remote.reapply(context, hostView) }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clickable(enabled = !binding, onClickLabel = "Add ${kind.title} widget") {
                                                    add(kind)
                                                }
                                        )
                                    }
                                    Text(
                                        kind.title,
                                        color = Lumen.Text,
                                        fontFamily = Outfit,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
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
}

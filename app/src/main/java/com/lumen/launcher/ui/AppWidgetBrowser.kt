package com.lumen.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppWidgetBrowser(binding: Boolean, onAdd: (AppWidgetProviderInfo) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var providers by remember { mutableStateOf<List<AppWidgetProviderInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        providers = withContext(Dispatchers.IO) {
            AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName != context.packageName }
                .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
        }
        loaded = true
    }
    Column(modifier) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Find app widgets") })
        Spacer(Modifier.height(10.dp))
        val matches = providers.filter { it.loadLabel(context.packageManager).toString().contains(query, true) || it.provider.packageName.contains(query, true) }
        if (matches.isEmpty()) Text(if (loaded) "No matching app widgets on this phone." else "Loading app widgets…", color = Lumen.Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(matches, key = { it.provider.flattenToString() }) { info ->
                var preview by remember(info.provider) { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(info.provider) {
                    preview = withContext(Dispatchers.IO) { runCatching {
                        val drawable = info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
                            ?: info.loadIcon(context, context.resources.displayMetrics.densityDpi)
                        drawable?.let {
                            val w = it.intrinsicWidth.coerceAtLeast(1)
                            val h = it.intrinsicHeight.coerceAtLeast(1)
                            val factor = (700f / maxOf(w, h)).coerceAtMost(1f)
                            it.toBitmap((w * factor).toInt().coerceAtLeast(1), (h * factor).toInt().coerceAtLeast(1))
                        }
                    }.getOrNull() }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).glass(RoundedCornerShape(22.dp), LocalGlass.current)
                    .clickable(enabled = !binding, onClickLabel = "Add app widget") { onAdd(info) }.padding(14.dp)) {
                    Text(info.loadLabel(context.packageManager).toString(), color = Lumen.Text, fontFamily = Outfit, fontSize = 17.sp)
                    Text(runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString() }
                        .getOrDefault(info.provider.packageName), color = Lumen.Muted, fontSize = 12.sp)
                    preview?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(130.dp).padding(top = 10.dp)) }
                    Text(if (binding) "Adding…" else "+ Add widget", color = Lumen.BrandGold, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

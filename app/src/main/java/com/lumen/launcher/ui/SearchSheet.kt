package com.lumen.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.search.SearchHit
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchSheet(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        repeat(8) { index ->
            delay(if (index == 0) 16 else 40)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Lumen.Scrim)
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp)
                .denseGlass(RoundedCornerShape(Lumen.SheetRadius))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            )
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                focusRequester = focusRequester,
                onSearch = viewModel::submitSearch
            )
            Spacer(Modifier.height(14.dp))
            if (state.query.isBlank()) {
                Text(
                    "Try saying what you want",
                    color = Lumen.Faint,
                    fontSize = 11.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(10.dp))
                ExampleChips(
                    examples = listOf(
                        "WhatsApp",
                        "Call John",
                        "Turn on Bluetooth",
                        "Find photo editor"
                    ),
                    onSelect = viewModel::onQueryChange
                )
                if (state.recentApps.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "RECENT",
                        color = Lumen.Faint,
                        fontSize = 11.sp,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.recentApps, key = { it.key }) { app ->
                            IconSlot(
                                label = app.label,
                                packageName = app.packageName,
                                activityName = app.activityName,
                                iconSize = state.iconSizeDp.dp,
                                icons = viewModel.icons,
                                onClick = { viewModel.launch(app) },
                                onLongClick = { viewModel.showAppActions(app) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val apps = state.hits.filterIsInstance<SearchHit.App>()
                    val top = apps.firstOrNull()
                    if (top != null) {
                        item(key = "top") {
                            Text(
                                "TOP MATCH",
                                color = Lumen.Faint,
                                fontSize = 11.sp,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.4.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                            )
                            AppRow(top, viewModel, featured = true)
                        }
                    }
                    items(state.hits.size) { index ->
                        val hit = state.hits[index]
                        if (hit is SearchHit.App && hit.app.key == top?.app?.key) return@items
                        when (hit) {
                            is SearchHit.Math -> MathRow(hit)
                            is SearchHit.App -> AppRow(hit, viewModel)
                            is SearchHit.Action -> ActionRow(hit, viewModel)
                            is SearchHit.IntentGroup -> GroupRow(hit.title, hit.subtitle, hit.apps, viewModel)
                            is SearchHit.Discovery -> GroupRow(hit.title, hit.subtitle, hit.apps, viewModel)
                            is SearchHit.Web -> WebRow(hit, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Search, null, tint = Lumen.Text, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search or say what you want",
                    color = Lumen.Faint,
                    fontSize = 16.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Light
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Lumen.Text,
                    fontSize = 16.sp,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Lumen.Accent),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) keyboard?.show() }
            )
        }
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Lumen.Accent.copy(alpha = 0.85f),
            modifier = Modifier
                .padding(end = 8.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun ExampleChips(
    examples: List<String>,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(examples, key = { it }) { example ->
            Text(
                text = example,
                color = Lumen.Muted,
                fontSize = 13.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onSelect(example) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MathRow(hit: SearchHit.Math) {
    ResultRow(
        icon = Icons.Outlined.Calculate,
        title = hit.calculation.value,
        subtitle = hit.calculation.detail ?: hit.calculation.expression,
        onClick = {}
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(hit: SearchHit.App, viewModel: LauncherViewModel, featured: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (featured) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.07f))
            .combinedClickable(
                onClick = { viewModel.launch(hit.app) },
                onLongClick = { viewModel.showAppActions(hit.app) }
            )
            .padding(horizontal = 12.dp, vertical = if (featured) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(hit.app.packageName, hit.app.activityName, if (featured) 52.dp else 46.dp, viewModel.icons)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hit.app.label,
                color = Lumen.Text,
                fontSize = if (featured) 18.sp else 16.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (featured) "App" else hit.app.category.label,
                color = Lumen.Faint,
                fontSize = 12.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
private fun ActionRow(hit: SearchHit.Action, viewModel: LauncherViewModel) {
    val icon = when (hit.id) {
        "wifi" -> Icons.Outlined.Wifi
        "settings" -> Icons.Outlined.Settings
        "call" -> Icons.Outlined.Call
        "whatsapp", "whatsapp_call" -> Icons.AutoMirrored.Outlined.Chat
        else -> Icons.Outlined.Search
    }
    ResultRow(icon, hit.title, hit.subtitle) { viewModel.runHit(hit) }
}

@Composable
private fun WebRow(hit: SearchHit.Web, viewModel: LauncherViewModel) {
    ResultRow(Icons.Outlined.Language, "Search the web", hit.query) { viewModel.runHit(hit) }
}

@Composable
private fun GroupRow(
    title: String,
    subtitle: String,
    apps: List<AppInfo>,
    viewModel: LauncherViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(14.dp)
    ) {
        Text(title, color = Lumen.Text, fontSize = 16.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
        Text(subtitle, color = Lumen.Faint, fontSize = 12.sp, fontFamily = Outfit, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps.take(6), key = { it.key }) { app ->
                IconSlot(
                    label = app.label,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    iconSize = 42.dp,
                    icons = viewModel.icons,
                    onClick = { viewModel.launch(app) },
                    onLongClick = { viewModel.showAppActions(app) }
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Lumen.AccentDeep.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Lumen.Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Lumen.Text, fontSize = 16.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Lumen.Faint, fontSize = 12.sp, fontFamily = Outfit, fontWeight = FontWeight.Light)
        }
    }
}

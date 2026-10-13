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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.search.SearchHit
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun SearchSheet(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val interaction = remember { SearchAppInteraction() }
    var searchOrigin by remember { mutableStateOf(Offset.Zero) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(interaction.lifted, interaction.menu) {
        if (interaction.lifted != null || interaction.menu != null) keyboard?.hide()
    }
    val focusManager = LocalFocusManager.current
    val resultsState = rememberLazyListState()
    val dismissKeyboardOnScroll = remember(keyboard, focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }
    val appResults = if (state.query.isBlank()) {
        (state.recentApps + state.favoriteApps + state.homeApps).distinctBy { it.key }.take(4)
    } else state.hits.filterIsInstance<SearchHit.App>().map { it.app }.distinctBy { it.key }.take(8)
    val tileKeys = appResults.map { it.key }.toSet()
    val otherResults = state.hits.filter {
        it !is SearchHit.App || (state.query.isNotBlank() && it.app.key !in tileKeys)
    }


    CompositionLocalProvider(LocalSearchAppInteraction provides interaction) {
    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { searchOrigin = it.positionInRoot() }) {
        Box(Modifier.fillMaxSize().blur(if (interaction.lifted != null) 16.dp else 0.dp)) {
        SpaceBackdrop(state.spaceWallpaper, extraBlur = 16.dp)
        AmbientBackdrop()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Lumen.Scrim)
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.query.isBlank()) "Suggestions" else "Search", color = Lumen.Text,
                    fontFamily = Outfit, fontSize = 22.sp, modifier = Modifier.weight(1f))
                Text("Cancel", color = Lumen.BrandGold, fontFamily = Outfit, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = resultsState,
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(dismissKeyboardOnScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (appResults.isNotEmpty()) item(key = "app-grid") {
                    SearchAppTiles(appResults, viewModel, state.iconSizeDp.dp)
                }
                items(otherResults.size) { index ->
                    when (val hit = otherResults[index]) {
                        is SearchHit.Math -> MathRow(hit)
                        is SearchHit.App -> AppRow(hit, viewModel, state.iconSizeDp.dp, featured = false)
                        is SearchHit.Action -> ActionRow(hit, viewModel)
                        is SearchHit.IntentGroup -> GroupRow(hit.title, hit.subtitle, hit.apps, viewModel, state.iconSizeDp.dp)
                        is SearchHit.Discovery -> GroupRow(hit.title, hit.subtitle, hit.apps, viewModel, state.iconSizeDp.dp)
                        is SearchHit.Web -> WebRow(hit, viewModel)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SearchField(
                query = state.query, onQueryChange = viewModel::onQueryChange,
                focusRequester = focusRequester,
                onSearch = { keyboard?.hide(); viewModel.submitSearch() },
                hint = com.lumen.launcher.search.SpaceSearch.hint(state.activeSpace),
                onVoice = { keyboard?.hide(); viewModel.voice.openVoice() }
            )
        }
        }
        SearchAppOverlay(interaction, searchOrigin, state, viewModel)
    }
    }
}

@Composable
private fun SearchAppTiles(apps: List<AppInfo>, viewModel: LauncherViewModel, iconSize: Dp) {
    val rows = apps.chunked(4)
    val rowHeight = iconSize + 42.dp
    Glass(modifier = Modifier.fillMaxWidth().height(rowHeight * rows.size + 20.dp),
        shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    row.forEach { app ->
                        Column(Modifier.weight(1f).searchAppContact(app, viewModel),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app.packageName, app.activityName, iconSize, viewModel.icons)
                            Spacer(Modifier.height(6.dp))
                            FittingAppLabel(app.label, color = Lumen.Text, fontFamily = Outfit, fontSize = 11.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
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
    onSearch: () -> Unit,
    hint: String,
    onVoice: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .glass(RoundedCornerShape(26.dp), LocalGlass.current)
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
                    hint,
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
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onVoice),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.MicNone, "Speak to Lumen", tint = Lumen.BrandGold, modifier = Modifier.size(20.dp))
        }
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
private fun AppRow(hit: SearchHit.App, viewModel: LauncherViewModel, iconSize: Dp, featured: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (featured) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.07f))
            .searchAppContact(hit.app, viewModel)
            .padding(horizontal = 12.dp, vertical = if (featured) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(hit.app.packageName, hit.app.activityName, iconSize, viewModel.icons)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hit.app.label,
                color = Lumen.Text,
                fontSize = if (featured) 18.sp else 16.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                if (featured) "App" else hit.app.category.label,
                color = Lumen.Faint,
                fontSize = 12.sp,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionRow(hit: SearchHit.Action, viewModel: LauncherViewModel) {
    if (hit.id.startsWith("photo:")) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable { viewModel.runHit(hit) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = android.net.Uri.parse(hit.id.removePrefix("photo:")),
                contentDescription = "Photo thumbnail", contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(hit.title, color = Lumen.Text, fontFamily = Outfit, fontSize = 16.sp, maxLines = 1)
                Text(hit.subtitle, color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, maxLines = 1)
            }
        }
        return
    }
    val icon = when (hit.id) {
        "wifi" -> Icons.Outlined.Wifi
        "settings" -> Icons.Outlined.Settings
        "call" -> Icons.Outlined.Call
        "whatsapp", "whatsapp_call" -> Icons.AutoMirrored.Outlined.Chat
        "permission:contacts" -> Icons.Outlined.Person
        "permission:photos" -> Icons.Outlined.PhotoLibrary
        else -> if (hit.id.startsWith("contact:")) Icons.Outlined.Person else Icons.Outlined.Search
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
    viewModel: LauncherViewModel,
    iconSize: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(14.dp)
    ) {
        Text(title, color = Lumen.Text, fontSize = 16.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(subtitle, color = Lumen.Faint, fontSize = 12.sp, fontFamily = Outfit, fontWeight = FontWeight.Light, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps.take(6), key = { it.key }) { app ->
                Box(Modifier.width(iconSize + 20.dp).searchAppContact(app, viewModel)) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app.packageName, app.activityName, iconSize, viewModel.icons)
                    FittingAppLabel(app.label, color = Lumen.Text, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                } }

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
            Text(title, color = Lumen.Text, fontSize = 16.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, color = Lumen.Faint, fontSize = 12.sp, fontFamily = Outfit, fontWeight = FontWeight.Light, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

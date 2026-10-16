package com.lumen.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import com.lumen.launcher.data.CaptureKind
import com.lumen.launcher.data.ContactMatch
import com.lumen.launcher.search.SearchHit
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Both drawer browsing and mode results dismiss the IME on upward scrolling. */
@Composable
internal fun rememberSearchKeyboardDismissal(): NestedScrollConnection {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return remember(keyboard, focusManager) {
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
}

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
    val resultsState = rememberLazyListState()
    val dismissKeyboardOnScroll = rememberSearchKeyboardDismissal()
    val blank = state.query.isBlank()
    val suggested = remember(state.recentApps, state.favoriteApps, state.homeApps, blank) {
        (state.recentApps + state.favoriteApps + state.homeApps).distinctBy { it.key }.take(4)
    }
    val appResults = if (blank) suggested
    else state.hits.filterIsInstance<SearchHit.App>().map { it.app }.distinctBy { it.key }.take(8)
    val tileKeys = appResults.map { it.key }.toSet()
    val otherResults = state.hits.filter {
        it !is SearchHit.App || (state.query.isNotBlank() && it.app.key !in tileKeys)
    }
    val people by produceState(emptyList<ContactMatch>(), blank) {
        value = if (!blank) emptyList()
        else withContext(Dispatchers.IO) { viewModel.previewContacts(5) }
    }
    val recentQueries = remember(state.recentApps) {
        state.recentApps.map { it.label }.distinct().take(4)
    }
    val clipboard = LocalClipboardManager.current

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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp)
                // Absorb taps so they don't fall through to the dismiss scrim
                // (orb / empty pill areas have no clickable of their own).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            SearchField(
                query = state.query, onQueryChange = viewModel::onQueryChange,
                focusRequester = focusRequester,
                onSearch = { keyboard?.hide(); viewModel.submitSearch() },
                hint = "Search anything...",
                onVoice = { keyboard?.hide(); viewModel.voice.openVoice() }
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                state = resultsState,
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(dismissKeyboardOnScroll),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (blank) {
                    if (suggested.isNotEmpty()) item(key = "suggested") {
                        SearchBrowseSection("SUGGESTED") {
                            SearchSuggestedApps(suggested, viewModel, state.iconSizeDp.dp)
                        }
                    }
                    item(key = "people") {
                        SearchBrowseSection("PEOPLE") {
                            SearchPeopleRow(
                                people = people,
                                hasAccess = viewModel.hasContactsAccess(),
                                onAllow = {
                                    viewModel.runHit(
                                        SearchHit.Action(
                                            "permission:contacts",
                                            "Allow Contacts",
                                            "Find people in search",
                                            ""
                                        )
                                    )
                                },
                                onPerson = { name -> viewModel.onQueryChange(name) }
                            )
                        }
                    }
                    item(key = "quick") {
                        SearchBrowseSection("QUICK ACTIONS") {
                            SearchQuickActions(
                                onTask = { viewModel.openCapture(CaptureKind.Task) },
                                onNote = { viewModel.openCapture(CaptureKind.Note) },
                                onTimer = { viewModel.onQueryChange("timer 5 minutes") },
                                onPaste = {
                                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(viewModel::onQueryChange)
                                }
                            )
                        }
                    }
                    if (recentQueries.isNotEmpty()) item(key = "recent") {
                        SearchBrowseSection("RECENT") {
                            SearchRecentQueries(recentQueries) { viewModel.onQueryChange(it) }
                        }
                    }
                } else {
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
            }

        }
        }
        SearchAppOverlay(interaction, searchOrigin, state, viewModel)
    }
    }
}

@Composable
private fun SearchBrowseSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            color = Color.White.copy(0.45f),
            fontFamily = Outfit,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
        content()
    }
}

@Composable
private fun SearchSuggestedApps(apps: List<AppInfo>, viewModel: LauncherViewModel, iconSize: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(0.07f))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        apps.forEach { app ->
            Column(
                Modifier
                    .weight(1f)
                    .searchAppContact(app, viewModel),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIcon(app.packageName, app.activityName, iconSize.coerceAtMost(52.dp), viewModel.icons)
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        repeat((4 - apps.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

private val PeoplePastels = listOf(
    Color(0xFF7EB6FF), Color(0xFFE8A0BF), Color(0xFF8FCB9B), Color(0xFFF0B27A), Color(0xFFC5B0E8)
)

@Composable
private fun SearchPeopleRow(
    people: List<ContactMatch>,
    hasAccess: Boolean,
    onAllow: () -> Unit,
    onPerson: (String) -> Unit
) {
    if (!hasAccess) {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFFFAC775).copy(0.45f), RoundedCornerShape(22.dp))
                .clickable(onClick = onAllow)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Person, null, tint = Color(0xFFFAC775), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Allow Contacts", color = Lumen.Text, fontFamily = Outfit, fontSize = 14.sp)
        }
        return
    }
    if (people.isEmpty()) {
        Text("No contacts yet", color = Lumen.Faint, fontFamily = Outfit, fontSize = 13.sp)
        return
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        people.forEachIndexed { index, person ->
            val label = person.name.trim().split(Regex("\\s+")).firstOrNull { part ->
                part.any { it.isLetter() }
            } ?: person.name.trim().take(12)
            val initials = label.filter { it.isLetter() }
                .take(2)
                .uppercase()
                .ifBlank { "?" }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onPerson(person.name) }
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PeoplePastels[index % PeoplePastels.size].copy(0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    color = Lumen.Text,
                    fontFamily = Outfit,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchQuickActions(
    onTask: () -> Unit,
    onNote: () -> Unit,
    onTimer: () -> Unit,
    onPaste: () -> Unit
) {
    val actions = listOf(
        Triple("New task", Icons.Outlined.CheckBox, onTask),
        Triple("Note", Icons.AutoMirrored.Outlined.Notes, onNote),
        Triple("Timer", Icons.Outlined.Timer, onTimer),
        Triple("Paste link", Icons.Outlined.ContentPaste, onPaste)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, icon, action) ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, Color(0xFFFAC775).copy(0.35f), RoundedCornerShape(22.dp))
                            .background(Color.White.copy(0.05f))
                            .clickable(onClick = action)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = Color(0xFFFAC775), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = Lumen.Text, fontFamily = Outfit, fontSize = 13.sp, maxLines = 1)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchRecentQueries(queries: List<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        queries.forEach { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(q) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.History, null, tint = Color.White.copy(0.45f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(q, color = Lumen.Text, fontFamily = Outfit, fontSize = 15.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
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
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    hint: String,
    onVoice: () -> Unit,
    autoFocus: Boolean = true,
    attention: Float = 0f,
    showScope: Boolean = true
) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequester, autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        // Wait for the editable node to attach, including a drawer-to-search transition.
        androidx.compose.runtime.withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val arrival = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { arrival.animateTo(1f, androidx.compose.animation.core.tween(320)) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .graphicsLayer {
                alpha = arrival.value
                translationY = (1f - arrival.value) * 100.dp.toPx()
                scaleX = .88f + .12f * arrival.value
                scaleY = .88f + .12f * arrival.value
                shadowElevation = 18.dp.toPx()
                shape = RoundedCornerShape(48.dp)
                clip = false
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(48.dp))
        ) {
            SearchOrbSurface(Modifier.fillMaxSize())
            // A soft gold edge response, drawn without resizing or moving the input.
            if (attention > .001f) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val inset = 2.dp.toPx()
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(
                        Color(0xFFFFD88D).copy(alpha = attention * .25f),
                        Color(0xFFFFE7B0).copy(alpha = attention * .8f),
                        Color(0xFFFFD88D).copy(alpha = attention * .25f))),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                )
            }
            // Center the orb + field + mic in the pill; category line sits under as overlay.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Same live crystal orb as the dock ring — vertically centered.
                SearchOrbMark(Modifier.size(58.dp), prominent = true)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            hint.ifBlank { "Search anything..." },
                            color = Color.White.copy(0.88f),
                            fontSize = 17.sp,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 17.sp,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(Color(0xFFFFD88D)),
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
                // Raised glass mic well.
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White.copy(0.16f), Color.White.copy(0.04f), Color.Transparent)
                            )
                        )
                        .border(0.7.dp, Color.White.copy(0.22f), CircleShape)
                        .clickable(onClick = onVoice),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.MicNone,
                        "Speak to Lumen",
                        tint = Color.White.copy(0.92f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (showScope) Text(
                "APPS  •  FILES  •  WEB  •  AI",
                color = Color.White.copy(0.55f),
                fontFamily = Outfit,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
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

package com.lumen.launcher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lumen.launcher.data.*
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun MeCover(profile: MeProfile, progress: () -> Float, height: androidx.compose.ui.unit.Dp) {
    val hasPhoto = remember(profile.cover) { profile.cover.isNotBlank() && File(profile.cover).exists() }
    // Keep Coil's target dimensions fixed throughout the transition. Clip the reveal in drawing.
    Box(Modifier.fillMaxWidth().height(height).graphicsLayer { alpha = progress() }
        .drawWithContent {
            val reveal = size.height * progress().coerceIn(0f, 1f)
            if (reveal > 0f) clipRect(bottom = reveal) {
                this@drawWithContent.drawContent()
                drawRect(Brush.verticalGradient(
                    0f to Color.Black.copy(.24f), .4f to Color.Transparent,
                    .65f to Color.Transparent, 1f to Color(0xFF07152E), endY = reveal))
            }
        }) {
        if (hasPhoto) MePhoto(profile, Modifier.fillMaxSize().graphicsLayer {
            val scale = .96f + .04f * progress()
            scaleX = scale; scaleY = scale
        })
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFAF775D), Color(0xFF253B66), Color(0xFF06162E)))))
    }
}

@Composable
private fun MePhoto(profile: MeProfile, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(0.dp))) {
        AsyncImage(File(profile.cover), "Personal cover", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = profile.zoom; scaleY = profile.zoom
                translationX = profile.x * size.width * (profile.zoom - 1f) * .5f
                translationY = profile.y * size.height * (profile.zoom - 1f) * .5f
            })
    }
}

@Composable
internal fun MeHeader(profile: MeProfile, progress: () -> Float, expandedHeight: androidx.compose.ui.unit.Dp,
    selected: Boolean, interactive: Boolean, greeting: String, location: String?, onEdit: () -> Unit, onTravel: () -> Unit,
    compact: @Composable () -> Unit) {
    val context = LocalContext.current
    val tripPrefs = remember { context.getSharedPreferences("lumen_planned_trip", android.content.Context.MODE_PRIVATE) }
    var tripRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(tripPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> tripRevision++ }
        tripPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { tripPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val tripBadge = remember(tripRevision, LocalDate.now()) {
        runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(tripPrefs.getString("start", ""))) }
            .getOrNull()?.takeIf { it in 0..30 }?.let { if (it == 0L) "Today" else "${it}d" }
    }
    var sharing by remember { mutableStateOf(false) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { android.widget.Toast.makeText(context, "No app can open this file", android.widget.Toast.LENGTH_SHORT).show() }
    }
    MeCollapsingHeader(expandedHeight, progress, selected, compact = {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progress() }, contentAlignment = Alignment.BottomStart) { compact() }
    }, expanded = {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() }) {
            Column(Modifier.align(Alignment.BottomStart).padding(end = 80.dp, bottom = 5.dp)
                .clickable(enabled = interactive && selected, onClick = onEdit)) {
                Text(greeting, color = Color.White, fontFamily = Outfit, fontSize = 29.sp, lineHeight = 32.sp, maxLines = 2)
                if (profile.name.isNotBlank()) Text(profile.name, color = Color.White.copy(.85f), fontFamily = Outfit, fontSize = 29.sp, maxLines = 1)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Lumen.Muted, fontFamily = Outfit, fontSize = 13.sp)
                if (profile.location && !location.isNullOrBlank()) Text(location, color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
            }
            Column(Modifier.align(Alignment.CenterEnd), verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                MeCoverAction("Trip Mode", Icons.Outlined.FlightTakeoff, interactive && selected, badge = tripBadge, click = onTravel)
                MeCoverAction("My Files", Icons.Outlined.Folder, interactive && selected) { files.launch(arrayOf("*/*")) }
                MeCoverAction("Share", Icons.Outlined.Share, interactive && selected) { sharing = true }
            }
        }
    })
    if (sharing) MeShareDialog(profile) { sharing = false }
}

@Composable
private fun MeCoverAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, badge: String? = null, click: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF142333).copy(.5f))
            .border(.9.dp, Lumen.BrandGold.copy(.75f), CircleShape).clickable(enabled = enabled, onClick = click), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(label, fontFamily = Outfit, fontSize = 10.sp, color = Color.White)
        if (badge != null) Text(badge, color = Lumen.BrandGold, fontFamily = Outfit, fontSize = 9.sp)
    }
}

internal fun meFavorites(profile: MeProfile, state: LauncherUiState): List<AppInfo> {
    val available = state.visibleApps.filter { it.key !in state.privateApps }
    return if (profile.favoritesSet) profile.favorites.mapNotNull { key -> available.find { it.key == key } }.take(6)
    else available.filter { app -> state.dock.none { it.packageName == app.packageName } }
        .sortedBy { app -> state.homeApps.indexOfFirst { it.key == app.key }.let { if (it < 0) Int.MAX_VALUE else it } }.take(4)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MeContent(state: LauncherUiState, vm: LauncherViewModel, profile: MeProfile, active: Boolean = true, onEdit: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { if (active) while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    val event = EventPresentation.next(state.upcomingEvents, now)
    val favorites = meFavorites(profile, state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (profile.nextUp) {
            Column(Modifier.fillMaxWidth().meGlass().clickable {
                if (event != null) vm.openCalendarEvent(event) else vm.openCapture(CaptureKind.Task)
            }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (event == null) "Your day is clear" else "Next Up", color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                Text(event?.title ?: "Capture what\u2019s next", color = Lumen.Text, fontFamily = Outfit, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                if (event != null) {
                    Text(EventPresentation.whenText(event, now, true), color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                    if (event.location.isNotBlank()) Text(event.location, color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                }
            }
        }
        if (profile.tasks) {
            val open = state.todos.filter { !it.done && it.space != SpaceKind.Private }
            val today = open.count { it.dueAt?.let { due -> TodoTime.startOfDay(due) == TodoTime.startOfDay(now) } == true }
            Row(Modifier.fillMaxWidth().meGlass().clickable { vm.openTodoList() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF9ED8B0), modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("${open.size} Tasks", color = Lumen.Text, fontFamily = Outfit, fontSize = 18.sp)
                    Text("$today due today", color = Lumen.Muted, fontFamily = Outfit, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Lumen.Muted)
            }
        }
        // Flexible rows retain usable targets when six favorites are selected on a small phone.
        (favorites.map<AppInfo, AppInfo?> { it } + listOf(null)).chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { app ->
                    Column(Modifier.weight(1f).combinedClickable(onClick = { if (app == null) onEdit() else vm.launch(app) }, onLongClick = onEdit), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (app != null) AppIcon(app.packageName, app.activityName, 44.dp, vm.icons)
                        else Box(Modifier.size(44.dp).meGlass(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Lumen.BrandGold) }
                        Text(app?.label ?: "Add", color = Lumen.Text, fontFamily = Outfit, fontSize = 10.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Personalize Me", color = Lumen.BrandGold) }
    }
}

private fun Modifier.meGlass(): Modifier = clip(RoundedCornerShape(22.dp))
    .background(Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.White.copy(.06f))))
    .border(.8.dp, Color.White.copy(.28f), RoundedCornerShape(22.dp))

@Composable
internal fun MeSettingsEntry(state: LauncherUiState, vm: LauncherViewModel) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Me personal home", color = Lumen.BrandGold) }
    if (open) MeEditor(state, vm) { open = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MeEditor(state: LauncherUiState, vm: LauncherViewModel, dismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MePreferences(context.applicationContext) }
    val stored by store.profile.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<MeProfile?>(null) }
    LaunchedEffect(stored) { if (draft == null) draft = stored }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.noBackupFilesDir, "me_covers").apply { mkdirs() }
                    val file = File(dir, "cover-${java.util.UUID.randomUUID()}.img")
                    try {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input); file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file.absolutePath
                    } catch (e: Exception) { file.delete(); throw e }
                }
            }.onSuccess { path -> draft = draft?.copy(cover = path, zoom = 1f, x = 0f, y = 0f) }
                .onFailure { error = "Could not copy this photo. Choose another image." }
            busy = false
        }
    }
    var pickingApps by remember { mutableStateOf(false) }
    val p = draft ?: return
    val favorites = meFavorites(p, state)

    Dialog(
        onDismissRequest = { if (!busy) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1228), Color(0xFF0C1524), Color(0xFF071018))
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = !busy, onClick = dismiss) {
                        Text("Cancel", color = Lumen.Muted, fontFamily = Outfit, fontSize = 15.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Personalize Me",
                        color = Lumen.Text,
                        fontFamily = Outfit,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                runCatching { store.save(p) }.onSuccess { dismiss() }
                                    .onFailure { error = "Could not save your changes. Please try again." }
                                busy = false
                            }
                        }
                    ) {
                        Text(
                            if (busy) "Saving…" else "Save",
                            color = Lumen.BrandGold,
                            fontFamily = Outfit,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hero avatar
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(112.dp)
                                .shadow(18.dp, CircleShape, spotColor = Lumen.BrandGold.copy(.45f), ambientColor = Lumen.BrandGold.copy(.2f))
                                .border(2.5.dp, Lumen.BrandGold.copy(.9f), CircleShape)
                                .padding(3.dp)
                                .border(.7.dp, Color.White.copy(.28f), CircleShape)
                                .clip(CircleShape)
                                .background(Color.White.copy(.08f))
                                .clickable(enabled = !busy) {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (p.cover.isNotBlank()) MePhoto(p, Modifier.fillMaxSize())
                            else Icon(Icons.Outlined.Person, null, tint = Lumen.BrandGold, modifier = Modifier.size(48.dp))
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Lumen.BrandGold)
                                .clickable(enabled = !busy) {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, "Change cover", tint = Lumen.OnBrandGold, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        if (busy) "Copying photo…" else "Tap to choose your cover",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = p.name,
                        onValueChange = { draft = p.copy(name = it.take(50)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Preferred name", fontFamily = Outfit) },
                        placeholder = { Text("Optional", fontFamily = Outfit) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lumen.BrandGold.copy(.7f),
                            unfocusedBorderColor = Color.White.copy(.22f),
                            focusedLabelColor = Lumen.BrandGold,
                            unfocusedLabelColor = Lumen.Muted,
                            cursorColor = Lumen.BrandGold,
                            focusedTextColor = Lumen.Text,
                            unfocusedTextColor = Lumen.Text
                        )
                    )

                    MeSetupSection("Home") {
                        MeElegantToggle("Enable Me", "Show Me on the mode track", Icons.Outlined.Person, p.enabled) {
                            draft = p.copy(enabled = it)
                        }
                        MeElegantToggle("Open at startup", "Land on Me when Lumen opens", Icons.Outlined.Home, p.startup) {
                            draft = p.copy(startup = it)
                        }
                    }

                    if (p.cover.isNotBlank()) {
                        MeSetupSection("Cover crop") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(168.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(.8.dp, Color.White.copy(.22f), RoundedCornerShape(18.dp))
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val current = draft ?: return@detectTransformGestures
                                            draft = current.copy(
                                                zoom = (current.zoom * zoom).coerceIn(1f, 4f),
                                                x = (current.x + pan.x / size.width * 2).coerceIn(-1f, 1f),
                                                y = (current.y + pan.y / size.height * 2).coerceIn(-1f, 1f)
                                            )
                                        }
                                    }
                            ) { MePhoto(p, Modifier.fillMaxSize()) }
                            Text(
                                "Drag to position · pinch or use sliders",
                                color = Lumen.Faint,
                                fontFamily = Outfit,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            MeSetupSlider("Zoom", p.zoom, 1f..4f) { draft = p.copy(zoom = it) }
                            MeSetupSlider("Horizontal", p.x, -1f..1f) { draft = p.copy(x = it) }
                            MeSetupSlider("Vertical", p.y, -1f..1f) { draft = p.copy(y = it) }
                            TextButton(
                                onClick = { draft = p.copy(cover = "", zoom = 1f, x = 0f, y = 0f) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Remove cover", color = Color(0xFFFF8A8A), fontFamily = Outfit, fontSize = 13.sp)
                            }
                        }
                    }

                    MeSetupSection("On Me") {
                        MeElegantToggle("Next Up", "Show your next calendar moment", Icons.Outlined.Event, p.nextUp) {
                            draft = p.copy(nextUp = it)
                        }
                        MeElegantToggle("Tasks", "Show open tasks at a glance", Icons.Outlined.CheckCircle, p.tasks) {
                            draft = p.copy(tasks = it)
                        }
                        MeElegantToggle("Weather place", "Show the current weather location", Icons.Outlined.Place, p.location) {
                            draft = p.copy(location = it)
                        }
                    }

                    MeSetupSection("Favorites") {
                        Text(
                            "Up to 6 apps on your Me home",
                            color = Lumen.Faint,
                            fontFamily = Outfit,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        favorites.forEachIndexed { index, app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(app.packageName, app.activityName, 32.dp, vm.icons)
                                Text(
                                    app.label,
                                    color = Lumen.Text,
                                    fontFamily = Outfit,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                                )
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val keys = favorites.map { it.key }.toMutableList()
                                        java.util.Collections.swap(keys, index, index - 1)
                                        draft = p.copy(favorites = keys, favoritesSet = true)
                                    }
                                ) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up", tint = Lumen.Muted) }
                                IconButton(
                                    onClick = {
                                        draft = p.copy(
                                            favorites = favorites.filterNot { it.key == app.key }.map { it.key },
                                            favoritesSet = true
                                        )
                                    }
                                ) { Icon(Icons.Outlined.Close, "Remove", tint = Lumen.Muted) }
                            }
                        }
                        Button(
                            onClick = { pickingApps = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Lumen.BrandGold.copy(.18f),
                                contentColor = Lumen.BrandGold
                            )
                        ) {
                            Icon(Icons.Outlined.Apps, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose favorites", fontFamily = Outfit, fontWeight = FontWeight.Medium)
                        }
                    }

                    Text(
                        "Cover photos stay on this device. Steps stays hidden until an activity source is connected.",
                        color = Lumen.Faint,
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    error?.let {
                        Text(it, color = Color(0xFFFF8A8A), fontFamily = Outfit, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (pickingApps) {
        AlertDialog(
            onDismissRequest = { pickingApps = false },
            title = { Text("Favorites", fontFamily = Outfit) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(
                        state.visibleApps.filter { it.key !in state.privateApps }.sortedBy { it.label.lowercase() },
                        key = { it.key }
                    ) { app ->
                        val keys = meFavorites(p, state).map { it.key }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (app.key in keys || keys.size < 6) {
                                        draft = p.copy(
                                            favorites = if (app.key in keys) keys - app.key else keys + app.key,
                                            favoritesSet = true
                                        )
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(app.key in keys, null)
                            Text(app.label, fontSize = 13.sp, fontFamily = Outfit)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingApps = false }) {
                    Text("Done", color = Lumen.BrandGold, fontFamily = Outfit)
                }
            }
        )
    }
}

@Composable
private fun MeSetupSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(.12f), Color.White.copy(.05f))))
            .border(.8.dp, Color.White.copy(.22f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(
            title,
            color = Lumen.BrandGold,
            fontFamily = Outfit,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        content()
    }
}

@Composable
private fun MeElegantToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: Boolean,
    change: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Lumen.BrandGold.copy(.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Lumen.BrandGold, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Lumen.Text, fontFamily = Outfit, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Lumen.Faint, fontFamily = Outfit, fontSize = 12.sp, maxLines = 2)
        }
        Switch(
            checked = value,
            onCheckedChange = change,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Lumen.OnBrandGold,
                checkedTrackColor = Lumen.BrandGold,
                uncheckedThumbColor = Color.White.copy(.85f),
                uncheckedTrackColor = Color.White.copy(.18f)
            )
        )
    }
}

@Composable
private fun MeSetupSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label, color = Lumen.Muted, fontFamily = Outfit, fontSize = 11.sp)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Lumen.BrandGold,
            activeTrackColor = Lumen.BrandGold.copy(.75f),
            inactiveTrackColor = Color.White.copy(.16f)
        )
    )
}

@Composable
private fun MeToggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, fontFamily = Outfit)
        Switch(value, change)
    }
}

@Composable
private fun MeShareDialog(profile: MeProfile, dismiss: () -> Unit) {
    val context = LocalContext.current
    var includePhoto by remember { mutableStateOf(false) }
    var includeName by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<MeSharePreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(includePhoto, includeName) {
        preview = null
        preview = withContext(Dispatchers.Default) { MeSharePreview(includePhoto, includeName, meShareBitmap(profile, includePhoto, includeName)) }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Share My Lumen") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Events, locations, tasks and app names are excluded.", fontSize = 12.sp)
            preview?.let { result -> androidx.compose.foundation.Image(result.bitmap.asComposeImage(), "Share preview", Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit) }
            MeToggle("Include my cover photo", includePhoto) { includePhoto = it }
            MeToggle("Include my name", includeName) { includeName = it }
            error?.let { Text(it) }
        }
    }, confirmButton = { TextButton(enabled = preview?.let { it.photo == includePhoto && it.name == includeName } == true && !busy, onClick = {
        val result = preview ?: return@TextButton
        if (result.photo != includePhoto || result.name != includeName) return@TextButton
        val bitmap = result.bitmap
        busy = true
        scope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    File(dir, "my-lumen-${java.util.UUID.randomUUID()}.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share My Lumen"))
                dismiss()
            }.onFailure { error = "Could not share the preview. Please try again." }
            busy = false
        }
    }) { Text("Share this preview") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

private data class MeSharePreview(val photo: Boolean, val name: Boolean, val bitmap: Bitmap)

private fun Bitmap.asComposeImage() = this.asImageBitmap()

private fun meShareBitmap(profile: MeProfile, photo: Boolean, name: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = android.graphics.LinearGradient(0f, 0f, 540f, 960f, intArrayOf(0xFF34466B.toInt(), 0xFF0B1537.toInt(), 0xFF493477.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 540f, 960f, paint); paint.shader = null
    if (photo && profile.cover.isNotBlank()) {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(profile.cover, options)
        options.inJustDecodeBounds = false; options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 1200).coerceAtLeast(1)
        android.graphics.BitmapFactory.decodeFile(profile.cover, options)?.let { image ->
            val scale = maxOf(540f / image.width, 400f / image.height) * profile.zoom
            val w = image.width * scale; val h = image.height * scale
            canvas.save(); canvas.clipRect(0, 0, 540, 400)
            val x = (540 - w) / 2 + profile.x * 270; val y = (400 - h) / 2 + profile.y * 200
            canvas.drawBitmap(image, null, android.graphics.RectF(x, y, x + w, y + h), paint)
            canvas.restore(); image.recycle()
        }
    }
    paint.color = android.graphics.Color.WHITE; paint.textSize = 40f
    canvas.drawText("My Lumen", 30f, 315f, paint)
    if (name && profile.name.isNotBlank()) { paint.textSize = 30f; canvas.drawText(profile.name.take(24), 30f, 360f, paint) }
    paint.color = 0x25FFFFFF; canvas.drawRoundRect(25f, 425f, 515f, 490f, 30f, 30f, paint)
    paint.color = android.graphics.Color.WHITE; paint.textSize = 19f
    canvas.drawText("Me   Home   Work   Social   Travel   Focus", 43f, 465f, paint)
    paint.color = 0x20FFFFFF; canvas.drawRoundRect(25f, 515f, 515f, 660f, 25f, 25f, paint)
    paint.color = android.graphics.Color.WHITE; paint.textSize = 24f; canvas.drawText("Your space. Your day.", 48f, 585f, paint)
    paint.color = 0x35FFFFFF; canvas.drawRoundRect(25f, 830f, 515f, 920f, 45f, 45f, paint)
    paint.color = 0xFFFAC775.toInt(); canvas.drawCircle(270f, 875f, 48f, paint)
    paint.color = 0xFF493477.toInt(); canvas.drawCircle(270f, 875f, 42f, paint)
    return bitmap
}


@Composable
internal fun rememberMeMotionDuration(): Int {
    val context = LocalContext.current
    val power = remember { context.getSystemService(android.os.PowerManager::class.java) }
    var reduced by remember { mutableStateOf(power.isPowerSaveMode || !android.animation.ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                reduced = power.isPowerSaveMode || !android.animation.ValueAnimator.areAnimatorsEnabled()
            }
        }
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = power.isPowerSaveMode || !android.animation.ValueAnimator.areAnimatorsEnabled() }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.unregisterReceiver(receiver); context.contentResolver.unregisterContentObserver(observer) }
    }
    return if (reduced) 0 else 500
}

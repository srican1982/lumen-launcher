package com.lumen.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.lumen.launcher.data.*
import com.lumen.launcher.ui.theme.Outfit
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal val TravelCategory.tint: Color get() = when (this) {
    TravelCategory.Flights -> Color(0xFFE5ACF5)
    TravelCategory.Hotels -> Color(0xFFF1D88C)
    TravelCategory.Tickets -> Color(0xFF9CE6B7)
    TravelCategory.Itinerary -> Color(0xFF9DCFFA)
    TravelCategory.Other -> Color(0xFFCCB6FF)
}
internal val TravelCategory.icon: ImageVector get() = when (this) {
    TravelCategory.Flights -> Icons.Outlined.Flight
    TravelCategory.Hotels -> Icons.Outlined.Hotel
    TravelCategory.Tickets -> Icons.Outlined.ConfirmationNumber
    TravelCategory.Itinerary -> Icons.Outlined.Map
    TravelCategory.Other -> Icons.Outlined.Description
}

internal fun openTravelItem(context: android.content.Context, state: LauncherUiState, vm: LauncherViewModel, item: TravelAttachment): Boolean {
    if (item.mime == "application/x-lumen-app") {
        val app = state.visibleApps.firstOrNull { it.key == item.uri } ?: return false
        vm.launch(app)
        return true
    }
    return runCatching {
        val uri = Uri.parse(item.uri)
        val intent = if (item.mime == "text/uri-list") Intent(Intent.ACTION_VIEW, uri)
        else Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime.ifBlank { context.contentResolver.getType(uri) ?: "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

@Composable
fun TravelCollection(state: LauncherUiState, vm: LauncherViewModel, initialCategory: TravelCategory? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(initialCategory?.name) }
    val category = TravelCategory.entries.firstOrNull { it.name == selected }
    var adding by rememberSaveable { mutableStateOf(false) }
    var links by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraCategory by rememberSaveable { mutableStateOf(TravelCategory.Flights.name) }

    fun save(uri: String, title: String, mime: String, group: TravelCategory) {
        vm.addTravelAttachments(listOf(TravelAttachment(uri, title, mime, category = group)))
    }
    fun importFiles(uris: List<Uri>) {
        val group = category ?: return
        scope.launch {
            importing = true
            val results = withContext(Dispatchers.IO) {
                uris.distinct().map { uri -> runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "Travel file"
                    TravelAttachment(uri.toString(), name, context.contentResolver.getType(uri).orEmpty(), category = group)
                } }
            }
            vm.addTravelAttachments(results.mapNotNull { it.getOrNull() })
            if (results.any { it.isFailure }) error = "Some files could not be saved. Try a local copy from Files."
            importing = false
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importFiles(it) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importFiles(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { save(it, "Travel photo", "image/jpeg", TravelCategory.valueOf(cameraCategory)) }
        cameraUri = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraUri?.let { uri ->
            runCatching { camera.launch(Uri.parse(uri)) }.onFailure { error = "Camera is unavailable. Try Photos instead." }
        } else error = "Camera permission was not granted. You can still add photos or files."
    }
    val entries = state.travelAttachments.filter { category == null || it.travelCategory == category }
    val shape = RoundedCornerShape(30.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).imePadding().fillMaxHeight(.98f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF393347), Color(0xFF253339), Color(0xFF302D40))))
            .border(1.dp, Color.White.copy(.28f), shape).padding(16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).size(34.dp, 4.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color(0xFFAAA0CF).copy(alpha = .45f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR TRIP, TOGETHER", color = Color(0xFFDDC4F4), fontSize = 10.sp, letterSpacing = 1.6.sp)
                    Text(if (adding) "Add travel document" else category?.title ?: "Travel documents",
                        fontFamily = Outfit, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.08f))
                    .border(1.dp, Color.White.copy(.18f), RoundedCornerShape(50)), onClick = {
                    if (adding) adding = false else if (category != null) selected = null else onDismiss()
                }) { Icon(if (adding || category != null) Icons.Outlined.ArrowBack else Icons.Outlined.Close, "Back", tint = Color.White) }
            }
            Text(if (adding) "Choose what you want to save for this trip."
                else category?.description ?: "Files, links and ticket apps, all in one place.",
                color = Color(0xFFD5C8E1), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            if (adding) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(TravelCategory.entries.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { group ->
                                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(listOf(group.tint.copy(.12f), Color.White.copy(.07f)))).border(1.dp, Color.White.copy(.25f), RoundedCornerShape(18.dp))
                                    .clickable { selected = group.name; adding = false }.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        TravelBadge(group)
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFD5C8E1), modifier = Modifier.size(20.dp))
                                    }
                                    Text(group.title, color = Color.White, fontFamily = Outfit, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (group == TravelCategory.Other) "Any other travel documents" else group.description,
                                        color = Color(0xFFD5C8E1), fontSize = 11.sp, lineHeight = 15.sp, minLines = 2)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                if (category == null) {
                    Button(onClick = { adding = true }, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add document")
                    }
                } else {
                    // All import sources belong to the selected category, including links and apps.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImportAction("Files", Icons.Outlined.Description, Modifier.weight(1f), !importing) { files.launch(arrayOf("*/*")) }
                        ImportAction("Photos", Icons.Outlined.Photo, Modifier.weight(1f), !importing) { gallery.launch(arrayOf("image/*")) }
                        ImportAction("Camera", Icons.Outlined.CameraAlt, Modifier.weight(1f), !importing) {
                            runCatching {
                                val folder = File(context.filesDir, "travel_photos").apply { mkdirs() }
                                val file = File.createTempFile("trip_", ".jpg", folder)
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                cameraUri = uri.toString(); cameraCategory = category.name
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) camera.launch(uri)
                                else cameraPermission.launch(android.Manifest.permission.CAMERA)
                            }.onFailure { error = "Camera is unavailable. You can add a photo from Files instead." }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { links = true }, modifier = Modifier.weight(1f)) { Text("Add link") }
                        OutlinedButton(onClick = { apps = true }, modifier = Modifier.weight(1f)) { Text("Select app") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (entries.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon((category ?: TravelCategory.Other).icon, null, tint = Color(0xFFDDC4F4), modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Ready for your next trip", color = Color.White, fontFamily = Outfit, fontSize = 18.sp)
                            Text("Add files, a web link or a travel app.", color = Color(0xFFD5C8E1), fontSize = 12.sp)
                        }
                    }
                    items(entries, key = { it.travelCategory.name + it.uri }) { item ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.08f))
                            .border(.8.dp, Color.White.copy(.25f), RoundedCornerShape(22.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).clickable {
                                if (!openTravelItem(context, state, vm, item)) error = "This item is unavailable. Check the file or reinstall the app."
                            }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                val app = state.visibleApps.firstOrNull { it.key == item.uri && item.mime == "application/x-lumen-app" }
                                if (app != null) AppIcon(app.packageName, app.activityName, 46.dp, vm.icons, showNotificationBadge = false)
                                else if (item.mime.startsWith("image/")) AsyncImage(item.uri, null,
                                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                                else TravelBadge(item.travelCategory)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(item.title, color = Color.White, fontFamily = Outfit, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(item.travelCategory.title + "  ·  " + when {
                                        item.mime == "application/x-lumen-app" -> "Open app"
                                        item.mime == "text/uri-list" -> "Web link"
                                        item.mime.startsWith("image/") -> "Photo"
                                        item.mime == "application/pdf" -> "PDF"
                                        else -> "File"
                                    }, color = item.travelCategory.tint, fontSize = 11.sp)
                                }
                            }
                            IconButton(onClick = { vm.removeTravelAttachment(item) }) {
                                Icon(Icons.Outlined.Close, "Remove " + item.title, tint = Color.White.copy(.85f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(.16f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFFC8BED3), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Removing a shortcut keeps your original file.", color = Color(0xFFC8BED3), fontSize = 10.sp, lineHeight = 14.sp)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD09AF4), contentColor = Color(0xFF291738))) { Text("Done") }
        }
    }
    if (links && category != null) TravelLinkDialog(category, onDismiss = { links = false }) { url, title ->
        save(url, title, "text/uri-list", category)
        links = false
    }
    if (apps && category != null) {
        var query by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { apps = false }, title = { Text("Choose a travel app") },
            text = { Column {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Find an app") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(state.visibleApps.filter { it.label.contains(query, true) }.sortedBy { it.label }, key = { it.key }) { app ->
                        Row(Modifier.fillMaxWidth().clickable {
                            save(app.key, app.label, "application/x-lumen-app", category); apps = false
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app.packageName, app.activityName, 36.dp, vm.icons, showNotificationBadge = false)
                            Text(app.label, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { apps = false }) { Text("Cancel") } })
    }
}

@Composable
internal fun TravelBadge(category: TravelCategory) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.linearGradient(listOf(category.tint.copy(.6f), category.tint.copy(.12f))))
        .border(1.dp, category.tint.copy(.6f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(category.icon, null, tint = category.tint, modifier = Modifier.size(27.dp))
    }
}

@Composable
private fun ImportAction(label: String, icon: ImageVector, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.07f)).clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = Color(0xFFDDC4F4), modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

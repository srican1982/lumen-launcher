package com.lumen.launcher.travel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumen.launcher.travel.data.TripAutoBackup
import com.lumen.launcher.travel.data.TripBackupManager
import com.lumen.launcher.ui.theme.Lumen
import kotlinx.coroutines.launch

@Composable
fun TripBackupControls(tripId: Long? = null, title: String = "Lumen trips") {
    val context = LocalContext.current
    val manager = remember(context) { TripBackupManager(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val prefs = remember(context) { TripAutoBackup.preferences(context) }
    var autoEnabled by remember { mutableStateOf(TripAutoBackup.enabled(context)) }
    var lastSuccess by remember { mutableStateOf(prefs.getLong("success", 0)) }
    var backupError by remember { mutableStateOf(prefs.getString("error", null)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            autoEnabled = TripAutoBackup.enabled(context)
            lastSuccess = prefs.getLong("success", 0)
            backupError = prefs.getString("error", null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun task(action: suspend () -> String) {
        scope.launch {
            busy = true
            try { message = action() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "Could not finish. Please try again." }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) task { manager.export(uri, tripId); "Trip organization saved. This file contains photo references, not photo files. Back up your photos separately." }
    }
    val autoDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) task {
            TripAutoBackup.enable(context, uri)
            "Automatic trip backup is on. Lumen updates this file after changes and periodically in the background. Choose cloud-backed storage to protect against losing your phone. Photos are backed up separately."
        }
    }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingReconnect by remember { mutableStateOf(false) }
    fun restoreFile(uri: android.net.Uri) = task {
        val r = manager.restore(uri)
        // Retry must repair an album already imported without photo access, not just skip it.
        val links = manager.reconnect()
        "Restored ${r.trips} trips; ${r.skipped} already existed. Library: ${links.connected} photos connected; ${links.missing} unavailable. Missing photos may need to be downloaded or included in Lumen's photo access."
    }
    fun reconnectPhotos() = task {
        val r = manager.reconnect()
        "${r.connected} photos connected; ${r.missing} unavailable. Check that these photos are stored on this phone and that Lumen has access to them."
    }
    val photoAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val uri = pendingRestore
        val reconnect = pendingReconnect
        pendingRestore = null; pendingReconnect = false
        if (granted) { if (uri != null) restoreFile(uri) else if (reconnect) reconnectPhotos() }
        else message = "Photo access is needed to reconnect trip photos. In Android Settings → Apps → Lumen → Permissions → Photos, allow all photos, then restore or reconnect again. Your backup and album entries are preserved."
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (TripViewModel.hasMediaPermission(context)) restoreFile(uri)
            else { pendingRestore = uri; photoAccess.launch(TripViewModel.mediaPermission()) }
        }
    }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && tripId != null) task {
            val r = manager.exportPhotos(uri, tripId)
            "ZIP saved with ${r.connected} photos and trip organization. ${r.missing} photos were unavailable and could not be included."
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Box {
            TextButton(onClick = { expanded = true }, enabled = !busy) { Text(if (busy) "Working…" else "Backup & restore", color = Lumen.Accent) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(if (tripId == null) "Back up all trips" else "Export Lumen Trip") }, onClick = {
                    expanded = false; export.launch(title.replace(Regex("[^a-zA-Z0-9 ._-]"), "_").take(100) + ".lumentrip")
                })
                DropdownMenuItem(text = { Text(if (autoEnabled) "Change automatic backup file" else "Enable automatic backup") }, onClick = {
                    expanded = false; autoDestination.launch("Lumen automatic trips.lumentrip")
                })
                if (autoEnabled) {
                    DropdownMenuItem(text = { Text("Back up now") }, onClick = { expanded = false; task {
                        if (TripAutoBackup.backup(context)) "Automatic backup updated." else "Backup failed. Check the destination and connection."
                    } })
                    DropdownMenuItem(text = { Text("Turn off automatic backup") }, onClick = { expanded = false; TripAutoBackup.disable(context) })
                }
                DropdownMenuItem(text = { Text("Restore / import trips") }, onClick = { expanded = false; restore.launch(arrayOf("*/*")) })
                DropdownMenuItem(text = { Text("Reconnect photos on this phone") }, onClick = {
                    expanded = false
                    if (TripViewModel.hasMediaPermission(context)) reconnectPhotos()
                    else { pendingReconnect = true; photoAccess.launch(TripViewModel.mediaPermission()) }
                })
                if (tripId != null) DropdownMenuItem(text = { Text("Export album with photos (ZIP)") }, onClick = {
                    expanded = false; zip.launch(title.replace(Regex("[^a-zA-Z0-9 ._-]"), "_").take(100) + ".zip")
                })
            }
        }
        if (autoEnabled) Text(backupError ?: if (lastSuccess > 0) "Automatic backup · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSuccess)) else "Automatic backup pending",
            color = Lumen.Muted)
    }
    if (message != null) AlertDialog(onDismissRequest = { message = null }, title = { Text("Trip backup") }, text = { Text(message!!) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
}

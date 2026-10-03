package com.lumen.launcher.ui.focus

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lumen.launcher.focus.FocusCallAccess

@Composable
internal fun FocusStartButtonAction(onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (FocusCallAccess.ready(context)) onReady() else error = "Call screening wasn't enabled. Focus has not started."
    }
    fun requestRole() {
        if (Build.VERSION.SDK_INT < 29) { error = "Selected-list call filtering requires Android 10 or newer."; return }
        val manager = context.getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            error = "This phone does not offer call screening. Selected-list Focus cannot start."; return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) onReady()
        else runCatching { role.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }
            .onFailure { error = "Couldn't open call-screening setup." }
    }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestRole() else error = "Contacts access is required to screen calls from saved contacts."
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false }, containerColor = Color(0xFF17212B),
        title = { Text("Enable selected-list calls") },
        text = { Text("Lumen needs Contacts access and Android's caller ID & spam role to silence callers outside your list. This replaces your current call-screening app. Calls are silenced, not rejected. It does not filter WhatsApp/Messenger calls or messages. Other DND rules and silent mode still apply.") },
        confirmButton = { TextButton(onClick = {
            explain = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) contacts.launch(Manifest.permission.READ_CONTACTS)
            else requestRole()
        }) { Text("Set up") } }, dismissButton = { TextButton(onClick = { explain = false }) { Text("Cancel") } })
    if (error.isNotBlank()) AlertDialog(onDismissRequest = { error = "" }, title = { Text("Focus not started") }, text = { Text(error) }, confirmButton = { TextButton(onClick = { error = "" }) { Text("OK") } })
    return { if (FocusCallAccess.ready(context)) onReady() else explain = true }
}

package com.lumen.launcher.ui.focus

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.lumen.launcher.focus.FocusRingDiagnostics

@Composable
internal fun FocusRingCheck() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    fun refresh() {
        val prefs = context.getSharedPreferences("focus_call_diagnostics", 0)
        val last = prefs.getLong("time", 0)
        val outcome = if (last == 0L) "No screened call recorded by this version yet." else {
            val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(last))
            "Last screened call ($time): " + if (prefs.getBoolean("silenced", false)) "Lumen silenced it." else "Lumen allowed it; Android still controls the ringtone."
        }
        report = FocusRingDiagnostics.read(context) + "\n\n" + outcome +
            if (last == 0L) "" else "\n\nAt the time of that call:\n" + prefs.getString("sound", "")
    }
    TextButton(onClick = { refresh(); open = true }) { Text("Check ringing", color = FocusAccent) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Call ringing check") },
        text = { androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) { Text(report) } },
        confirmButton = { TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }; open = false }) { Text("Sound settings") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}

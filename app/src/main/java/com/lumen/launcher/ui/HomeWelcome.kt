package com.lumen.launcher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import com.lumen.launcher.data.*
import com.lumen.launcher.vm.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeWelcome(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("home_welcome", 0) }
    var ready by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<List<String>?>(null) }
    var usageMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save(keys: List<String>) {
        vm.saveModeApps(SpaceKind.Home, keys)
        prefs.edit().putBoolean("done", true).putLong("learning_since", System.currentTimeMillis()).apply()
        show = false
    }
    LaunchedEffect(state.apps, state.homePreferencesReady) {
        if (!ready && state.homePreferencesReady && state.apps.isNotEmpty()) {
            ready = true
            val existing = SpaceKind.Home.name in state.modeApps || state.launchHours.isNotEmpty()
            if (!prefs.contains("done") && existing) prefs.edit().putBoolean("done", true).putLong("learning_since", System.currentTimeMillis()).apply()
            initial = HomeStarterApps.picks(state.visibleApps, state.dock.map { it.packageName }.toSet()).map { it.key }
            show = !prefs.getBoolean("done", false)
        }
    }
    fun loadUsage() {
        scope.launch {
            val ranked = withContext(Dispatchers.IO) { runCatching { HomeUsage.rank(context, state.visibleApps) }.getOrDefault(emptyList()) }
            if (ranked.isNotEmpty()) initial = HomeStarterApps.fromUsage(state.favoriteApps + ranked, state.dock.map { it.packageName }.toSet()).map { it.key }
            else usageMessage = "No usage history available yet. You can pick your apps below."
        }
    }
    val access = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { loadUsage() }
    if (show) {
        ModeAppPicker(state.copy(spaceOverride = SpaceKind.Home), vm.icons,
            onDismiss = { save(initial.orEmpty()) }, onSave = { save(it) }, welcome = true,
            initialKeys = initial, onUsage = {
                if (HomeUsage.allowed(context)) loadUsage()
                else access.launch(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        if (usageMessage != null) AlertDialog(onDismissRequest = { usageMessage = null }, text = { Text(usageMessage!!) }, confirmButton = { TextButton(onClick = { usageMessage = null }) { Text("OK") } })
    }
    // Suggestions never move existing icons; acceptance only appends the chosen app.
    var suggestion by remember { mutableStateOf<AppInfo?>(null) }
    LaunchedEffect(ready, show, state.homePulse) {
        val now = System.currentTimeMillis()
        val week = 7 * 86_400_000L
        if (ready && !show && state.activeSpace == SpaceKind.Home && now - prefs.getLong("learning_since", now) >= week && now - prefs.getLong("last_suggestion", 0) >= week) {
            val counts = state.launchHours.groupingBy { it.key }.eachCount()
            suggestion = AppUsageRanking.rank(state.visibleApps, state.launchHours).firstOrNull { app ->
                (counts[app.key] ?: 0) >= 8 && state.homeApps.none { it.key == app.key } && state.dock.none { it.packageName == app.packageName } && app.key !in prefs.getStringSet("dismissed", emptySet()).orEmpty()
            }
            if (suggestion != null) prefs.edit().putLong("last_suggestion", now).apply()
        }
    }
    suggestion?.let { app ->
        fun dismiss() {
            prefs.edit().putStringSet("dismissed", prefs.getStringSet("dismissed", emptySet()).orEmpty() + app.key).apply()
            suggestion = null
        }
        AlertDialog(onDismissRequest = { dismiss() }, title = { Text("Add ${app.label} to Home?") },
            text = { Text("You open ${app.label} often. Your existing apps will stay in place.") },
            confirmButton = { TextButton(onClick = { vm.saveModeApps(SpaceKind.Home, state.homeApps.map { it.key } + app.key); suggestion = null }) { Text("Add to Home") } },
            dismissButton = { TextButton(onClick = { dismiss() }) { Text("Not now") } })
    }
}

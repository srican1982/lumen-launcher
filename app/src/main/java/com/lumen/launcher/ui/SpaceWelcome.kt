package com.lumen.launcher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.lumen.launcher.data.*
import com.lumen.launcher.vm.*

@Composable
fun SpaceWelcome(state: LauncherUiState, vm: LauncherViewModel) {
    val space = state.activeSpace
    // Home and Focus have their own first-run flows; Locked Space is configured explicitly.
    if (space !in setOf(SpaceKind.Work, SpaceKind.Personal, SpaceKind.Travel)) return
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("space_welcome", 0) }
    var show by remember(space) { mutableStateOf(false) }
    val picks = remember(space, state.visibleApps, state.dock) {
        SpaceStarterApps.picks(space, state.visibleApps, state.dock.map { it.packageName }.toSet()).map { it.key }
    }
    LaunchedEffect(space, state.homePreferencesReady, state.apps) {
        if (state.homePreferencesReady && state.apps.isNotEmpty()) {
            if (space.name in state.modeApps) prefs.edit().putBoolean(space.name, true).apply()
            show = !prefs.getBoolean(space.name, false)
        }
    }
    fun save(keys: List<String>) {
        vm.saveModeApps(space, keys)
        prefs.edit().putBoolean(space.name, true).apply()
        show = false
    }
    if (show) ModeAppPicker(state, vm.icons, onDismiss = { save(picks) }, onSave = { save(it) }, welcome = true, initialKeys = picks)
}

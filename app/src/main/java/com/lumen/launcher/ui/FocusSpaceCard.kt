package com.lumen.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumen.launcher.focus.FocusAllowedPeopleRepository
import com.lumen.launcher.focus.FocusPolicyController
import com.lumen.launcher.ui.focus.FocusActiveScreen
import com.lumen.launcher.ui.focus.FocusPeoplePicker
import com.lumen.launcher.ui.focus.FocusSetupSheet
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.delay

/**
 * Focus Space entry — people-first quiet mode.
 * Session keeps running when the user leaves this Space.
 */
@Composable
fun FocusSpaceCard(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val peopleRepo = remember { FocusAllowedPeopleRepository.get(context) }
    val manager = remember { com.lumen.launcher.focus.FocusSessionManager.get(context) }
    val session by manager.snapshot.collectAsState()
    val policy = remember { FocusPolicyController(context) }

    val people by peopleRepo.people.collectAsState()
    val settings by peopleRepo.settings.collectAsState()
    val groups by peopleRepo.groups.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPeople by remember { mutableStateOf(false) }
    var customFocus by remember { mutableStateOf(false) }
    // Launcher Home does not destroy this Activity — close Focus overlays on homePulse.
    val homePulse = state.homePulse
    val homePulseBaseline = remember { homePulse }
    LaunchedEffect(homePulse) {
        if (homePulse != homePulseBaseline) {
            showPeople = false
            customFocus = false
        }
    }

    LaunchedEffect(state.focusRunning) {
        while (state.focusRunning) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remainingMs = when {
        state.focusPaused -> state.focusPausedRemainingMs
        state.focusRunning -> (state.focusUntil - now).coerceAtLeast(0L)
        else -> 0L
    }
    val totalMs = state.focusTotalMs.takeIf { it > 0 } ?: remainingMs.coerceAtLeast(1L)
    val capability = remember(people, settings) { policy.capabilityNote(people, settings) }

    LaunchedEffect(people, settings) { manager.refreshPolicy() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (session.error.isNotBlank()) androidx.compose.material3.Text(session.error, color = androidx.compose.ui.graphics.Color(0xFFFDA4AF))
        if (state.focusing) {
            FocusActiveScreen(
                remainingMs = remainingMs,
                totalMs = totalMs,
                paused = state.focusPaused,
                people = people,
                capabilityNote = capability,
                onPause = vm::pauseFocus,
                onResume = vm::resumeFocus,
                onExtend = { vm.extendFocus(15) },
                onEnd = vm::endFocus,
                onAddSomeone = { showPeople = true }
            )
        } else {
            FocusSetupSheet(
                people = people,
                settings = settings,
                groups = groups,
                capabilityNote = capability,
                dismissSignal = homePulse,
                onDuration = { peopleRepo.setLastDuration(it) },
                onOpenPeople = { showPeople = true },
                onSettingsChange = { peopleRepo.setSettings(it) },
                onStart = { minutes -> vm.startFocus(minutes) },
                onCustomDuration = { customFocus = true }
            )
        }
    }

    if (showPeople) {
        FocusPeoplePicker(
            selected = people,
            dismissSignal = homePulse,
            onDismiss = { showPeople = false },
            onSave = {
                peopleRepo.setPeople(it)
                showPeople = false
            }
        )
    }
    if (customFocus) {
        FocusDurationDialog(
            onDismiss = { customFocus = false },
            onStart = { minutes ->
                customFocus = false
                peopleRepo.setLastDuration(minutes)
            },
            initialMinutes = settings.lastDurationMinutes, confirmLabel = "Set time"
        )
    }
}

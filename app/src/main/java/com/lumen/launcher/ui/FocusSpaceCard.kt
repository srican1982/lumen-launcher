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

    val guideStore = remember { com.lumen.launcher.focus.FocusOnboardingStore(context) }
    var showGuide by remember { mutableStateOf(guideStore.prepare(peopleRepo)) }
    var guideStep by remember { mutableStateOf(guideStore.step()) }
    fun finishGuide() { guideStore.complete(); showGuide = false }

    val people by peopleRepo.people.collectAsState()
    val settings by peopleRepo.settings.collectAsState()
    val groups by peopleRepo.groups.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPeople by remember { mutableStateOf(false) }
    var customFocus by remember { mutableStateOf(false) }
    var pickTask by remember { mutableStateOf(false) }
    // Launcher Home does not destroy this Activity — close Focus overlays on homePulse.
    val homePulse = state.homePulse
    val homePulseBaseline = remember { homePulse }
    LaunchedEffect(homePulse) {
        if (homePulse != homePulseBaseline) {
            showPeople = false
            customFocus = false
            pickTask = false
        }
    }
    val openTasks = remember(state.todos) {
        state.todos
            .filterNot { it.done }
            .sortedWith(
                compareByDescending<com.lumen.launcher.data.TodoItem> { it.priority }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
            )
    }
    val focusTaskLabel = state.focusTask?.takeUnless { it.done }?.text
    val doneEntries = remember(state.focusDonePulse) {
        com.lumen.launcher.focus.FocusDoneStore.load(context)
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
                taskLabel = focusTaskLabel,
                doneEntries = doneEntries,
                onPause = vm::pauseFocus,
                onResume = vm::resumeFocus,
                onExtend = { vm.extendFocus(15) },
                onEnd = vm::endFocus,
                onAddSomeone = { showPeople = true },
                onLaunchApp = vm::launch
            )
        } else if (showGuide) {
            com.lumen.launcher.ui.focus.FocusOnboardingScreen(
                step = guideStep,
                minutes = settings.lastDurationMinutes,
                dismissSignal = homePulse,
                onStep = { guideStep = it; guideStore.setStep(it) },
                onDuration = peopleRepo::setLastDuration,
                onCustom = { customFocus = true },
                onFinish = { finishGuide() },
                onStart = {
                    finishGuide()
                    vm.startFocus(settings.lastDurationMinutes, state.focusTaskId.takeIf { it.isNotBlank() } ?: state.focusTask?.id)
                }
            )
        } else {
            androidx.compose.material3.TextButton(onClick = { guideStep = 0; showGuide = true }) {
                androidx.compose.material3.Text("How Focus works", color = androidx.compose.ui.graphics.Color(0xFFB8A6FF))
            }
            FocusSetupSheet(
                people = people,
                settings = settings,
                groups = groups,
                capabilityNote = capability,
                dismissSignal = homePulse,
                taskLabel = focusTaskLabel,
                doneEntries = doneEntries,
                onPickTask = { pickTask = true },
                onDuration = { peopleRepo.setLastDuration(it) },
                onOpenPeople = { showPeople = true },
                onSettingsChange = { peopleRepo.setSettings(it) },
                onStart = { minutes -> vm.startFocus(minutes, state.focusTaskId.takeIf { it.isNotBlank() } ?: state.focusTask?.id) },
                onCustomDuration = { customFocus = true }
            )
        }
    }

    if (showPeople) {
        com.lumen.launcher.ui.focus.FocusListsDialog(onDismiss = { showPeople = false }, dismissSignal = homePulse)
    }
    if (pickTask) {
        FocusTaskPicker(
            tasks = openTasks,
            selectedId = state.focusTaskId.takeIf { it.isNotBlank() } ?: state.focusTask?.id,
            onDismiss = { pickTask = false },
            onSelect = { id ->
                vm.selectFocusTask(id)
                pickTask = false
            },
            onAdd = {
                pickTask = false
                vm.openTodoList()
            }
        )
    }
    if (customFocus) {
        val nextEvent = state.upcomingEvents.firstOrNull { it.begin > System.currentTimeMillis() }?.begin
        FocusDurationDialog(
            onDismiss = { customFocus = false },
            onStart = { minutes ->
                customFocus = false
                peopleRepo.setLastDuration(minutes)
            },
            initialMinutes = settings.lastDurationMinutes,
            confirmLabel = "Set time",
            nextEventAtMs = nextEvent
        )
    }
}

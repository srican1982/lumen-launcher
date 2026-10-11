package com.lumen.launcher.vm

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.launcher.data.AppCategory
import com.lumen.launcher.alarm.AlarmScheduler
import com.lumen.launcher.data.HomeFolder
import com.lumen.launcher.data.ContactLookup
import com.lumen.launcher.data.TodoRepeat
import com.lumen.launcher.data.TodoTime
import com.lumen.launcher.inbox.InboxDigest
import com.lumen.launcher.search.AliasHints
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.IntentIndex
import com.lumen.launcher.search.LauncherCommand
import com.lumen.launcher.search.VoiceCommands
import com.lumen.launcher.BuildConfig
import com.lumen.launcher.voice.VoiceQuery
import com.lumen.launcher.voice.intent.AiIntentFallback
import com.lumen.launcher.voice.intent.VoiceAction
import com.lumen.launcher.voice.intent.VoiceConfidence
import com.lumen.launcher.voice.intent.VoiceIntent
import com.lumen.launcher.voice.intent.VoiceRisk
import com.lumen.launcher.voice.intent.VoiceIntentMapper
import com.lumen.launcher.voice.intent.VoiceQueryRouter
import com.lumen.launcher.voice.intent.ClarificationResolution
import com.lumen.launcher.voice.intent.ClarificationResolver
import com.lumen.launcher.data.NewsTopic
import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.util.TorchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Voice conversation, pending slots, confirmations and speech state are owned here. */
class VoiceViewModel(application: Application, private val home: HomeActions, private val tasks: TaskActions, private val alarms: AlarmActions) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state = _state.asStateFlow()
    private val contacts = ContactLookup(application)
    private val torch = TorchController(application)
    private var skipVoiceGreeting = false
    private var closeVoiceAfterSpeak = false
    var promptExactAfterSpeak = false
    private var parkedNews: Set<String> = emptySet()
    private var voiceMisses = 0
    private val voiceConversation = com.lumen.launcher.voice.intent.VoiceConversation()
    private var voiceAsksForDetail = false
    private var activeVoiceAction = VoiceAction.UNKNOWN
    private var voiceAiJob: kotlinx.coroutines.Job? = null
    private var pendingVoiceIntent: VoiceIntent? = null
    private var openedByVoiceAt = 0L
    private var correctionGuesses: List<String> = emptyList()
    private var lastTranscripts: List<String> = emptyList()
    private var sessionGeneration = 0L
    var voiceTyping = false
        private set
    private val onRequestMic = { home.emit(LauncherEvent.Permission.Mic) }
    private val onRequestContacts = { home.emit(LauncherEvent.Permission.Contacts) }
    private val onSpeak: (String, Boolean) -> Unit = { text, listen -> home.emit(LauncherEvent.Speak(text, listen)) }
    private val onListen = { home.emit(LauncherEvent.Listen) }
    private val onStopVoice = { home.emit(LauncherEvent.StopVoice) }
    private enum class HomeVoice { Pin, Unpin, Dock, Undock, Hide, Unhide }
    private fun isLocked() = getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
    fun cancelSession(stopListening: Boolean = true) {
        sessionGeneration++
        closeVoiceAfterSpeak = false
        voiceAiJob?.cancel()
        clearVoicePending()
        if (stopListening) onStopVoice()
        _state.update { it.copy(voiceHeard = "", voiceHint = "", voiceListening = false, voiceLevel = 0f, voiceCanRetry = false) }
    }
    override fun onCleared() { voiceAiJob?.cancel(); super.onCleared() }

    fun openVoice(fromWake: Boolean = false) {
        voiceTyping = false
        if (isLocked()) return
        dismissVoiceReturn()
        sessionGeneration++
        lastTranscripts = emptyList()
        voiceAiJob?.cancel()
        voiceConversation.clear()
        voiceAsksForDetail = false
        skipVoiceGreeting = fromWake
        voiceMisses = 0
        clearVoicePending()
        home.setSheet(Sheet.Voice)
        _state.update {
            it.copy(
                voiceHint = if (fromWake) "I'm listening." else "Hi, I'm Lumen.",
                voiceHeard = "",
                voiceListening = false,
                voiceLevel = 0f,
                voiceCanRetry = false
            )
        }
        onRequestMic()
    }

    fun onWakeWord(remainder: String) {
        voiceTyping = false
        if (isLocked()) return
        sessionGeneration++
        voiceAiJob?.cancel()
        dismissVoiceReturn()
        voiceMisses = 0
        clearVoicePending()
        if (remainder.isBlank()) {
            openVoice(fromWake = true)
            return
        }
        skipVoiceGreeting = true
        home.setSheet(Sheet.Voice)
        _state.update {
            it.copy(
                voiceHint = "On it.",
                voiceHeard = remainder,
                voiceListening = false,
                voiceLevel = 0f,
                voiceCanRetry = false
            )
        }
        onVoiceResults(listOf(remainder))
    }

    fun speechHints(): List<String> {
        val state = home.snapshot()
        val preferred = (state.favorites + state.dock.map { it.key }).distinct()
        val labels = preferred.mapNotNull { key -> state.visibleApps.find { it.key == key }?.label }
        return com.lumen.launcher.voice.SpeechContext.hints(labels + state.folders.map { it.name } + state.visibleApps.map { it.label })
    }

    private fun clearVoicePending() {
        pendingVoiceIntent = null
        _state.update { it.copy(voicePending = null, voiceAlternatives = emptyList()) }
    }

    fun editVoiceTranscript() {
        if (home.snapshot().sheet != Sheet.Voice) return
        voiceTyping = true
        sessionGeneration++
        voiceAiJob?.cancel()
        onStopVoice()
        closeVoiceAfterSpeak = false
        _state.update { it.copy(voiceListening = false, voiceCanRetry = false) }
    }

    fun chooseVoiceOption(index: Int, remember: Boolean) {
        if (home.snapshot().sheet != Sheet.Voice) return
        voiceTyping = false
        val pending = pendingVoiceIntent ?: return
        if (pending.missingSlot != null) return
        val chosen = pending.alternatives.getOrNull(index) ?: return
        onStopVoice()
        voiceAiJob?.cancel()
        clearVoicePending()
        if (remember && chosen.action == VoiceAction.OPEN_APP) {
            val app = home.snapshot().visibleApps.find { FuzzySearch.normalize(it.label) == FuzzySearch.normalize(chosen.appName.orEmpty()) }
            val wording = com.lumen.launcher.voice.intent.NaturalVoicePhrases.normalize(pending.originalText, home.snapshot().visibleApps.map { it.label })
            val alias = AliasHints.aliasKey(wording)
            if (app != null && alias.length in 2..32 && alias.split(' ').size <= 6) {
                home.rememberAlias(alias, app.key)
            }
        }
        // Selecting a suggestion identifies it; sensitive choices still require Yes/No.
        if (pending.alternatives.size > 1 && VoiceRisk.requiresConfirmation(chosen)) {
            askToClarify(chosen.copy(confirmed = false))
        } else executeVoiceIntent(ClarificationResolver.confirmed(chosen))
    }

    fun cancelVoiceChoice() {
        sessionGeneration++
        onStopVoice()
        voiceAiJob?.cancel()
        clearVoicePending()
        finishVoice("Okay, I won't do that.", continueTalking = false)
    }

    fun onMicPermission(granted: Boolean) {
        if (home.snapshot().sheet != Sheet.Voice) return
        if (!granted) {
            skipVoiceGreeting = false
            _state.update {
                it.copy(
                    voiceHint = "Microphone access is needed so I can hear you.",
                    voiceListening = false,
                    voiceCanRetry = true,
                    voiceLevel = 0f
                )
            }
            return
        }
        if (skipVoiceGreeting) {
            skipVoiceGreeting = false
            onListen()
            _state.update { it.copy(voiceHint = "I'm listening.") }
            return
        }
        onSpeak("Hi, I'm Lumen.", true)
        _state.update { it.copy(voiceHint = "Hi, I'm Lumen.", voiceListening = false) }
    }

    fun onVoiceStarting() {
        _state.update {
            it.copy(
                voiceListening = false,
                voiceHint = "Give me a second.",
                voiceHeard = if (pendingVoiceIntent != null) it.voiceHeard else "",
                voiceCanRetry = false,
                voiceLevel = 0f
            )
        }
    }

    fun onVoiceListening() {
        _state.update {
            it.copy(voiceListening = true, voiceHint = "I'm listening.", voiceCanRetry = false)
        }
    }

    fun onVoicePartial(text: String) {
        if (text.isBlank() || pendingVoiceIntent != null) return
        _state.update { it.copy(voiceHeard = text, voiceListening = true, voiceCanRetry = false) }
    }

    fun onVoiceLevel(level: Float) {
        if (home.snapshot().sheet != Sheet.Voice) return
        _state.update { it.copy(voiceLevel = level.coerceIn(0f, 1f)) }
    }

    fun onVoiceHardError(message: String) {
        if (home.snapshot().sheet != Sheet.Voice) return
        _state.update {
            it.copy(
                voiceListening = false,
                voiceHint = message,
                voiceHeard = "",
                voiceCanRetry = true,
                voiceLevel = 0f
            )
        }
    }

    fun retryVoice() {
        if (home.snapshot().sheet != Sheet.Voice) return
        voiceTyping = false
        if (pendingVoiceIntent?.missingSlot == null) clearVoicePending()
        voiceMisses = 0
        voiceAiJob?.cancel()
        _state.update {
            it.copy(
                voiceCanRetry = false,
                voiceHeard = "",
                voiceHint = "Give me a second.",
                voiceLevel = 0f
            )
        }
        onListen()
    }

    fun onVoiceResult(text: String) {
        onVoiceResults(listOf(text))
    }

    fun submitVoiceText(text: String) {
        if (home.snapshot().sheet != Sheet.Voice || text.isBlank()) return
        voiceTyping = false
        onStopVoice()
        pendingVoiceIntent?.let { pending ->
            if (ClarificationResolver.resolve(listOf(text), pending, home.snapshot().visibleApps.map { it.label }) == ClarificationResolution.NoMatch) clearVoicePending()
        }
        onVoiceResults(listOf(text))
    }

    fun onVoiceResults(candidates: List<String>) {
        if (home.snapshot().sheet != Sheet.Voice) return
        voiceAiJob?.cancel()
        val originals = candidates.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
        val cleaned = originals.map { VoiceQuery.clean(it) }
        if (cleaned.isEmpty()) {
            onVoiceNoSpeech()
            return
        }
        if (cleaned.all { VoiceQuery.isOwnSpeech(it) }) {
            onListen()
            return
        }
        val previous = _state.value
        val heard = if (pendingVoiceIntent != null) previous.voiceHeard else originals.first()
        val alternatives = if (pendingVoiceIntent != null) previous.voiceAlternatives else originals.drop(1)
        _state.update {
            it.copy(
                voiceHeard = heard,
                voiceAlternatives = alternatives,
                voiceListening = false,
                voiceHint = "On it.",
                voiceCanRetry = false,
                voiceLevel = 0f
            )
        }
        voiceMisses = 0
        lastTranscripts = originals
        sessionGeneration++
        handleVoice(originals)
    }

    fun onVoiceNoSpeech() {
        if (home.snapshot().sheet != Sheet.Voice) return
        voiceMisses += 1
        if (voiceMisses >= 2) {
            onStopVoice()
            _state.update { it.copy(voiceListening = false, voiceCanRetry = true,
                voiceHint = "I didn't catch that. Try again or type below.", voiceLevel = 0f) }
            return
        }
        _state.update {
            it.copy(voiceListening = false, voiceHint = "I'm listening.", voiceHeard = if (pendingVoiceIntent != null) it.voiceHeard else "", voiceLevel = 0f)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            if (home.snapshot().sheet == Sheet.Voice && _state.value.voiceHeard.isBlank() && !(_state.value.voiceCanRetry)) onListen()
        }
    }

    fun onVoiceFailed() {
        onVoiceNoSpeech()
    }

    fun onVoiceEngineStuck() {
        finishVoice("I couldn't hear you. Tap Lumen and try again.", continueTalking = false)
    }

    private fun handleVoice(candidates: List<String>) {
        activeVoiceAction = VoiceAction.UNKNOWN
        if (getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { finishVoice("Unlock your phone first.", continueTalking = false); return }
        pendingVoiceIntent?.let { pending ->
            when (val resolution = ClarificationResolver.resolve(
                candidates = candidates,
                pending = pending,
                appLabels = home.snapshot().visibleApps.map { it.label }
            )) {
                is ClarificationResolution.Execute -> {
                    clearVoicePending()
                    if (pending.alternatives.size > 1 && VoiceRisk.requiresConfirmation(resolution.intent)) {
                        askToClarify(resolution.intent.copy(confirmed = false))
                    } else executeVoiceIntent(resolution.intent)
                }
                ClarificationResolution.Cancel -> {
                    clearVoicePending()
                    finishVoice("Okay, I won't do that.")
                }
                ClarificationResolution.End -> {
                    clearVoicePending()
                    finishVoice("Okay.", continueTalking = false)
                }
                is ClarificationResolution.Ask -> finishVoice(resolution.message)
                ClarificationResolution.NoMatch -> {
                    if (pending.missingSlot != null) {
                        clearVoicePending()
                        handleVoice(candidates)
                        return
                    }
                    val appActions = setOf(
                        VoiceAction.OPEN_APP,
                        VoiceAction.ADD_TO_DOCK,
                        VoiceAction.REMOVE_FROM_DOCK,
                        VoiceAction.PIN_APP,
                        VoiceAction.UNPIN_APP,
                        VoiceAction.HIDE_APP,
                        VoiceAction.UNHIDE_APP,
                        VoiceAction.MOVE_TO_PRIVATE,
                        VoiceAction.REMOVE_FROM_PRIVATE,
                        VoiceAction.WHERE_APP,
                        VoiceAction.LEARN_ALIAS,
                        VoiceAction.ADD_TO_FOLDER
                    )
                    val missingApp = pending.alternatives.any {
                        it.action in appActions && it.appName.isNullOrBlank()
                    }
                    finishVoice(
                        if (missingApp) "Tell me the app name."
                        else "Say first, second, or describe the option you want."
                    )
                }
            }
            return
        }

        val labels = home.snapshot().visibleApps.map { it.label }
        val normalized = com.lumen.launcher.voice.intent.NaturalVoicePhrases.normalize(candidates.first(), labels)
        val alias = if (normalized.startsWith("open ")) home.snapshot().aliases[AliasHints.aliasKey(normalized)] else null
        val aliasApp = home.snapshot().visibleApps.find { it.key == alias }
        val intent = aliasApp?.let { VoiceIntent(VoiceAction.OPEN_APP, 1f, candidates.first(), appName = it.label) }
            ?: VoiceQueryRouter.route(candidates = candidates, appLabels = labels, folderNames = home.snapshot().folders.map { it.name })
        decideVoice(intent, candidates, labels, allowAi = true)
    }

    private fun decideVoice(
        intent: VoiceIntent,
        candidates: List<String>,
        labels: List<String>,
        allowAi: Boolean
    ) {
        _state.update { it.copy(voiceHeard = intent.originalText.ifBlank { candidates.firstOrNull().orEmpty() }) }
        if (intent.missingSlot == null && ((VoiceRisk.requiresConfirmation(intent) && intent.confidence >= VoiceConfidence.ASK) ||
            (intent.action == VoiceAction.CLARIFY && intent.alternatives.isNotEmpty()))) {
            askToClarify(intent)
            return
        }
        if (intent.missingSlot != null) {
            pendingVoiceIntent = intent
            _state.update { it.copy(voicePending = intent) }
            finishVoice(intent.textValue.orEmpty())
            return
        }
        if (intent.clockQuery != null) {
            val now = java.time.ZonedDateTime.now()
            val pattern = when (intent.clockQuery) {
                com.lumen.launcher.voice.intent.ClockQuery.TIME -> "h:mm a"
                com.lumen.launcher.voice.intent.ClockQuery.DATE -> "EEEE, MMMM d, yyyy"
                com.lumen.launcher.voice.intent.ClockQuery.DAY -> "EEEE"
            }
            finishVoice(now.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault())))
            return
        }
        val key = home.snapshot().geminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        val contextual = voiceConversation.needsContext(candidates.firstOrNull().orEmpty(), intent)
        val consult = allowAi &&
            home.snapshot().smartVoice &&
            (AiIntentFallback.needsFallback(intent) || contextual)
        if (consult) {
            if (key.isBlank()) {
                if (contextual) {
                    finishVoice("I need AI connected to understand that follow-up. Please say the full request.")
                    return
                }
                if (VoiceConfidence.shouldExecute(intent)) {
                    executeVoiceIntent(intent)
                    return
                }
                if (intent.action == VoiceAction.CLARIFY || VoiceConfidence.shouldAsk(intent)) {
                    askToClarify(intent)
                    return
                }
                finishVoice(AiIntentFallback.spokenError(AiIntentFallback.Error.NO_KEY))
                return
            }
            voiceAiJob?.cancel()
            voiceAiJob = viewModelScope.launch {
                _state.update { it.copy(voiceHint = "On it.", voiceListening = false) }
                val context = AiIntentFallback.Context(
                    apps = labels,
                    folders = home.snapshot().folders.map { it.name },
                    contacts = if (home.snapshot().aiContactNames) withContext(Dispatchers.IO) { contacts.names(40) } else emptyList(),
                    alarms = alarms.upcomingAlarms().take(6).map {
                        "${formatHour(it.hour, it.minute)} ${it.whenLabel().lowercase()}"
                    },
                    history = voiceConversation.history(),
                    now = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, h:mm a")),
                    transcripts = candidates
                )
                val outcome = withContext(Dispatchers.IO) {
                    AiIntentFallback.interpret(key, candidates, context)
                }
                if (!isActive || home.snapshot().sheet != Sheet.Voice) return@launch
                when (outcome) {
                    is AiIntentFallback.Outcome.Ok -> {
                        if (outcome.intent.action != VoiceAction.UNKNOWN) {
                            decideVoice(outcome.intent, candidates, labels, allowAi = false)
                            return@launch
                        }
                    }
                    is AiIntentFallback.Outcome.Failed -> {
                        if (contextual) {
                            finishVoice(AiIntentFallback.spokenError(outcome.error) + " Please say the full request.")
                            return@launch
                        }
                        if (!VoiceConfidence.shouldExecute(intent) &&
                            intent.action != VoiceAction.CLARIFY &&
                            !VoiceConfidence.shouldAsk(intent)
                        ) {
                            finishVoice(AiIntentFallback.spokenError(outcome.error))
                            return@launch
                        }
                    }
                }
                if (VoiceConfidence.shouldExecute(intent)) {
                    executeVoiceIntent(intent)
                    return@launch
                }
                if (intent.action == VoiceAction.CLARIFY || VoiceConfidence.shouldAsk(intent)) {
                    askToClarify(intent)
                    return@launch
                }
                finishVoice("I'm not sure what you meant. Say help to hear what I can do.")
            }
            return
        }
        if (VoiceConfidence.shouldExecute(intent)) {
            executeVoiceIntent(intent)
            return
        }
        if (intent.action == VoiceAction.CLARIFY || VoiceConfidence.shouldAsk(intent)) {
            askToClarify(intent)
            return
        }
        finishVoice("I'm not sure what you meant. Say help to hear what I can do.")
    }

    private fun askToClarify(intent: VoiceIntent) {
        activeVoiceAction = intent.alternatives.firstOrNull()?.action ?: intent.action
        pendingVoiceIntent = if (intent.action == VoiceAction.CLARIFY) {
            intent
        } else {
            VoiceIntent(
                action = VoiceAction.CLARIFY,
                confidence = intent.confidence,
                originalText = intent.originalText,
                clarify = listOf(VoiceQueryRouter.describe(intent)),
                alternatives = listOf(intent)
            )
        }
        _state.update { it.copy(voicePending = pendingVoiceIntent) }
        finishVoice(clarifySpeech(pendingVoiceIntent!!))
    }

    private fun executeVoiceIntent(intent: VoiceIntent) {
        activeVoiceAction = intent.action
        if (getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            finishVoice("Unlock your phone to use Lumen Voice.", continueTalking = false)
            return
        }
        if (VoiceRisk.requiresConfirmation(intent) && !intent.confirmed) {
            askToClarify(intent)
            return
        }
        clearVoicePending()
        voiceAsksForDetail = intent.action == VoiceAction.ASK_USER
        if (intent.action == VoiceAction.NEXT_EVENT && intent.textValue == "today") {
            val now = System.currentTimeMillis()
            if (!home.snapshot().calendarAccess) {
                finishVoice("Allow Calendar access in Flow to see today's events.")
                return
            }
            val events = home.snapshot().upcomingEvents.filter { it.end > now && com.lumen.launcher.data.EventPresentation.isToday(it, now) }.sortedBy { it.begin }
            home.goToPage(0)
            finishVoice(if (events.isEmpty()) "No more calendar events today." else events.take(3).joinToString(". ") {
                "${it.title}: ${com.lumen.launcher.data.EventPresentation.whenText(it, now)}"
            })
            return
        }
        if (intent.action == VoiceAction.OPEN_APP) {
            voiceOpenApp(intent)
            return
        }
        VoiceIntentMapper.toCommand(intent)?.let { command ->
            runLauncherCommand(command)
            return
        }
        (voiceHandlers[intent.action] ?: ::handleVoiceUnsupported)(intent)
    }

    private val voiceHandlers: Map<VoiceAction, (VoiceIntent) -> Unit> by lazy {
        mapOf(
        VoiceAction.SET_ALARM to ::handleVoiceSetAlarm,
        VoiceAction.CANCEL_ALARM to ::handleVoiceCancelAlarm,
        VoiceAction.LIST_ALARMS to ::handleVoiceListAlarms,
        VoiceAction.SHOW_TASKS to ::handleVoiceShowTasks,
        VoiceAction.SAVE_NOTE to ::handleVoiceSaveNote,
        VoiceAction.SAVE_LATER to ::handleVoiceSaveLater,
        VoiceAction.START_FOCUS to ::handleVoiceStartFocus,
        VoiceAction.END_FOCUS to ::handleVoiceEndFocus,
        VoiceAction.DAILY_REVIEW to ::handleVoiceDailyReview,
        VoiceAction.SET_REMINDER to ::handleVoiceSetReminder,
        VoiceAction.COMPLETE_TASK to ::handleVoiceCompleteTask,
        VoiceAction.DELETE_TASK to ::handleVoiceDeleteTask,
        VoiceAction.CALCULATE to ::handleVoiceCalculate,
        VoiceAction.ANSWER to ::handleVoiceAnswer,
        VoiceAction.ASK_USER to ::handleVoiceAskUser,
        VoiceAction.CALL to ::handleVoiceCall,
        VoiceAction.SEND_MESSAGE to ::handleVoiceSendMessage,
        VoiceAction.SET_TIMER to ::handleVoiceSetTimer,
        VoiceAction.NAVIGATE to ::handleVoiceNavigate,
        VoiceAction.PLAY_MEDIA to ::handleVoicePlayMedia,
        VoiceAction.TOGGLE_TORCH to ::handleVoiceToggleTorch,
        VoiceAction.OPEN_APP to ::handleVoiceOpenApp
        )
    }

    private fun handleVoiceSetAlarm(intent: VoiceIntent) {
        val hour = intent.hour ?: run { finishVoice("What time should I set the alarm for?"); return }
        val minute = intent.minute ?: 0
        if (hour !in 0..23 || minute !in 0..59) { finishVoice("Please give a valid alarm time."); return }
        val deadline = if (intent.daily) null else com.lumen.launcher.alarm.AlarmDeadline.forRequest(hour, minute, intent.originalText)
        val alarm = if (deadline != null) {
            alarms.addAt(hour, minute, deadline).also { alarms.ensureNotifications() }
        } else alarms.addAlarm(hour, minute, intent.daily)
        val spoken = buildString {
            append("Alarm for ${formatHour(alarm.hour, alarm.minute)}")
            if (intent.daily) append(", every day") else if (deadline != null) append(", tomorrow")
            append(".")
        }
        if (!AlarmScheduler.canExact(getApplication())) promptExactAfterSpeak = true
        finishVoice(spoken)
    }

    private fun handleVoiceCancelAlarm(intent: VoiceIntent) {
        val removed = alarms.cancelAlarms(intent.hour, intent.minute)
        finishVoice(
            if (removed == 0) "You don't have an alarm to cancel."
            else if (removed == 1) "Okay, alarm off."
            else "Okay, I cancelled $removed alarms."
        )
    }

    private fun handleVoiceListAlarms(intent: VoiceIntent) {
        val upcoming = alarms.upcomingAlarms()
        val spoken = if (upcoming.isEmpty()) {
            "No alarms yet."
        } else {
            "You have " + upcoming.take(4).joinToString(". ") {
                "${formatHour(it.hour, it.minute)} ${it.whenLabel().lowercase()}"
            } + "."
        }
        finishVoice(spoken)
    }

    private fun handleVoiceShowTasks(intent: VoiceIntent) {
        home.goToPage(2)
        val open = home.snapshot().spaceTodos.filterNot { it.done }
        val spoken = if (open.isEmpty()) "Your ${ home.snapshot().activeSpace.title } list is empty."
        else "On ${home.snapshot().activeSpace.title}: " + open.take(6).joinToString(". ") { it.text }
        finishVoice(spoken)
    }

    private fun handleVoiceSaveNote(intent: VoiceIntent) {
        val item = intent.textValue ?: return
        tasks.addNote(item)
        home.goToPage(0)
        finishVoice("Saved a note. $item.")
    }

    private fun handleVoiceSaveLater(intent: VoiceIntent) {
        tasks.saveForLater()
        finishVoice("Saved for later.")
    }

    private fun handleVoiceStartFocus(intent: VoiceIntent) {
        val mins = intent.intValue ?: 30
        tasks.startFocus(mins)
        val task = home.snapshot().focusTask?.text
        finishVoice(
            if (task.isNullOrBlank()) "Focus for $mins minutes."
            else "Focus for $mins minutes on $task."
        )
    }

    private fun handleVoiceEndFocus(intent: VoiceIntent) {
        tasks.endFocus()
        finishVoice("Focus is off.")
    }

    private fun handleVoiceDailyReview(intent: VoiceIntent) {
        home.goToPage(0)
        finishVoice(home.snapshot().dailyReview.spoken.ifBlank { "You're clear." })
    }

    private fun handleVoiceSetReminder(intent: VoiceIntent) {
        val item = intent.textValue ?: return
        val due = VoiceCommands.taskDue(intent.originalText)
        val repeat = VoiceCommands.taskRepeat(intent.originalText)
        val priority = VoiceCommands.taskPriority(intent.originalText)
        tasks.addTodo(item, due, repeat, priority)
        home.goToPage(2)
        val whenLabel = if (TodoTime.hasClock(due)) TodoTime.timeLabel(due) else "anytime"
        val repeatLabel = if (repeat.isNotBlank()) ", ${TodoRepeat.chip(repeat).lowercase()}" else ""
        val priorityLabel = if (priority) ", high priority" else ""
        finishVoice("Added to ${home.snapshot().activeSpace.title}, $whenLabel$repeatLabel$priorityLabel. $item.")
    }

    private fun handleVoiceCompleteTask(intent: VoiceIntent) {
        val asked = intent.textValue ?: "this"
        val match = tasks.matchTodo(asked, includeDone = false)
        if (match == null) {
            home.goToPage(2)
            finishVoice("I couldn't find that on your list.")
        } else {
            tasks.toggleTodo(match.id, done = true)
            home.goToPage(2)
            finishVoice("Done. ${match.text}.")
        }
    }

    private fun handleVoiceDeleteTask(intent: VoiceIntent) {
        val asked = intent.textValue ?: return
        val match = tasks.matchTodo(asked)
        if (match == null) {
            home.goToPage(2)
            finishVoice("I couldn't find that on your list.")
        } else {
            tasks.deleteTodo(match.id)
            home.goToPage(2)
            finishVoice("Removed ${match.text}.")
        }
    }

    private fun handleVoiceCalculate(intent: VoiceIntent) {
        finishVoice(intent.textValue ?: "I couldn't calculate that.")
    }

    private fun handleVoiceAnswer(intent: VoiceIntent) {
        finishVoice(intent.textValue ?: "I'm not sure.")
    }

    private fun handleVoiceAskUser(intent: VoiceIntent) {
        finishVoice(intent.textValue ?: "What should I do?")
    }

    private fun handleVoiceCall(intent: VoiceIntent) {
        voiceCall(intent)
    }

    private fun handleVoiceSendMessage(intent: VoiceIntent) {
        voiceMessage(intent)
    }

    private fun handleVoiceSetTimer(intent: VoiceIntent) {
        voiceTimer(intent)
    }

    private fun handleVoiceNavigate(intent: VoiceIntent) {
        voiceNavigate(intent)
    }

    private fun handleVoicePlayMedia(intent: VoiceIntent) {
        voicePlay(intent)
    }

    private fun handleVoiceToggleTorch(intent: VoiceIntent) {
        val on = intent.enabled?.let { torch.set(it) } ?: torch.toggle()
        finishVoice(if (intent.enabled == true && !on) "I couldn't turn the torch on." else if (on) "Torch on." else "Torch off.")
    }

    private fun handleVoiceOpenApp(intent: VoiceIntent) {
        val name = intent.appName ?: return
        if (!openNamedApp(name)) finishVoice("I couldn't find $name.")
    }

    private fun handleVoiceUnsupported(intent: VoiceIntent) {
        finishVoice("I'm not sure what you meant. Say help to hear what I can do.")
    }

    private fun clarifySpeech(intent: VoiceIntent): String {
        val options = intent.clarify
        if (intent.alternatives.size == 1) {
            val alternative = intent.alternatives.first()
            val appActions = setOf(
                VoiceAction.OPEN_APP,
                VoiceAction.ADD_TO_DOCK,
                VoiceAction.REMOVE_FROM_DOCK,
                VoiceAction.PIN_APP,
                VoiceAction.UNPIN_APP,
                VoiceAction.HIDE_APP,
                VoiceAction.UNHIDE_APP,
                VoiceAction.MOVE_TO_PRIVATE,
                VoiceAction.REMOVE_FROM_PRIVATE,
                VoiceAction.WHERE_APP,
                VoiceAction.LEARN_ALIAS,
                VoiceAction.ADD_TO_FOLDER
            )
            if (alternative.action in appActions && alternative.appName.isNullOrBlank()) {
                return "Which app do you mean?"
            }
            if (alternative.action == VoiceAction.ADD_TO_FOLDER && alternative.folderName.isNullOrBlank()) {
                return "Which folder do you mean?"
            }
        }
        if (options.size > 1) {
            val choices = options.mapIndexed { index, option -> "${index + 1}, $option" }
            return "Which one: ${choices.joinToString("; ")}?"
        }
        if (options.size == 1) {
            return "Do you want me to ${options[0]}?"
        }
        return "I think you want ${VoiceQueryRouter.describe(intent)}. Should I do that?"
    }

    fun onVoiceHomeResume() {
        if (isLocked() || _state.value.openedApp.isBlank()) return
        val fresh = _state.value.questionToCopy.isNotBlank() || System.currentTimeMillis() - openedByVoiceAt < 120_000
        _state.update { it.copy(returnVisible = fresh) }
        if (!fresh) dismissVoiceReturn()
    }

    fun dismissVoiceReturn() {
        _state.update { it.copy(returnVisible = false, openedApp = "", questionToCopy = "") }
    }

    fun correctVoiceApp() {
        val guesses = correctionGuesses
        dismissVoiceReturn()
        openVoice(fromWake = true)
        editVoiceTranscript()
        _state.update { it.copy(voiceHint = "Which app did you mean?", voiceAlternatives = guesses) }
    }

    fun copyVoiceQuestion() {
        if (getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
        val question = _state.value.questionToCopy.takeIf { it.isNotBlank() } ?: return
        getApplication<Application>().getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("Lumen question", question))
        _state.update { it.copy(questionToCopy = "") }
    }

    private fun voiceOpenApp(intent: VoiceIntent) {
        val app = home.resolveApp(intent.appName.orEmpty(), minScore = 860, voice = true)
            ?: run { finishVoice("I couldn't find ${intent.appName}."); return }
        val question = intent.textValue.orEmpty()
        val guesses = lastTranscripts.drop(1)
        correctionGuesses = guesses
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(app.packageName).putExtra(Intent.EXTRA_TEXT, question)
        val shared = question.isNotBlank() && home.startIntent(share)
        if (!shared && !home.launchApp(app)) { finishVoice("I couldn't open ${app.label}."); return }
        openedByVoiceAt = System.currentTimeMillis()
        _state.update { it.copy(openedApp = app.label, questionToCopy = if (shared) "" else question, returnVisible = false, voiceAlternatives = guesses) }
        finishVoice(if (shared) "Opening ${app.label} with your question. Review it there."
            else if (question.isNotBlank()) "Opening ${app.label}. Return to Lumen to copy your question."
            else "Opening ${app.label}.", continueTalking = false)
    }

    private fun voiceContact(intent: VoiceIntent, action: (String, String) -> Unit) {
        val who = (intent.recipientName ?: intent.appName).orEmpty()
        val number = intent.phoneNumber ?: who.takeIf { it.matches(Regex("[+0-9 ()-]{3,}")) }
        if (number != null) { action(who, number); return }
        if (!contacts.hasAccess()) {
            onRequestContacts()
            finishVoice("Allow Contacts, then try again.")
            return
        }
        voiceAiJob = viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) { contacts.matches(who, 3) }
            if (!isActive || home.snapshot().sheet != Sheet.Voice || getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return@launch
            val best = matches.firstOrNull()
            if (best == null) { finishVoice("I couldn't find $who."); return@launch }
            if (best.score < 900 || matches.drop(1).any { best.score - it.score < 80 }) {
                askToClarify(VoiceIntent(VoiceAction.CLARIFY, .8f, intent.originalText,
                    clarify = matches.map { "${it.name} (${it.phone.takeLast(4)})" },
                    alternatives = matches.map { intent.copy(recipientName = it.name, phoneNumber = it.phone, confirmed = false) }))
            } else action(best.name, best.phone)
        }
    }

    private fun voiceCall(intent: VoiceIntent) {
        voiceContact(intent) { name, phone ->
            val channel = intent.messagingApp
            if (!channel.isNullOrBlank() && !channel.equals("WhatsApp", true) && !channel.equals("Phone", true)) {
                val app = home.resolveApp(channel, minScore = 860, voice = true)
                if (app == null) finishVoice("I couldn't find $channel.")
                else if (home.launchApp(app)) finishVoice("Opening ${app.label}. Find $name and tap call there.", continueTalking = false)
                else finishVoice("I couldn't open ${app.label}.")
                return@voiceContact
            }
            val whatsapp = channel.equals("WhatsApp", true)
            val dial = if (whatsapp) Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phone.filter(Char::isDigit)}")).setPackage("com.whatsapp")
                else Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
            if (home.startIntent(dial)) finishVoice(if (whatsapp) "Opening WhatsApp for $name. Tap the call button in their chat." else "Opening the dialer for $name.", continueTalking = false)
            else finishVoice("I couldn't open the phone app.")
        }
    }

    private fun voiceMessage(intent: VoiceIntent) {
        voiceContact(intent) { name, phone ->
            val body = intent.textValue.orEmpty()
            val channel = intent.messagingApp
            if (!channel.isNullOrBlank() && !channel.equals("WhatsApp", true) && !channel.equals("Messages", true) && !channel.equals("SMS", true)) {
                val app = home.resolveApp(channel, minScore = 860, voice = true)
                if (app == null) { finishVoice("I couldn't find $channel."); return@voiceContact }
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(app.packageName).putExtra(Intent.EXTRA_TEXT, body)
                if (body.isNotBlank() && home.startIntent(share)) finishVoice("Choose $name in ${app.label} and review the message before sending.", continueTalking = false)
                else if (home.launchApp(app)) finishVoice("Opening ${app.label}. Choose $name and compose your message there.", continueTalking = false)
                else finishVoice("I couldn't open ${app.label}.")
                return@voiceContact
            }
            val composer = if (intent.messagingApp.equals("WhatsApp", true)) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phone.filter(Char::isDigit)}?text=${Uri.encode(body)}")).setPackage("com.whatsapp")
            } else Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).putExtra("sms_body", body)
            if (channel.equals("Messages", true)) {
                val app = home.resolveApp("Messages", minScore = 920, voice = true)
                    ?: run { finishVoice("I couldn't find Messages."); return@voiceContact }
                composer.setPackage(app.packageName)
            }
            if (home.startIntent(composer)) finishVoice("Opening a message to $name. Review it before sending.", continueTalking = false)
            else finishVoice("I couldn't open the message app.")
        }
    }

    private fun voiceTimer(intent: VoiceIntent) {
        val minutes = intent.durationSeconds?.let { (it + 59) / 60 } ?: intent.intValue
            ?: intent.hour?.let { it * 60 + (intent.minute ?: 0) }
            ?: intent.minute
        if (minutes == null || minutes !in 1..1440) {
            finishVoice("How many minutes?")
            return
        }
        alarms.addTimerSeconds(intent.durationSeconds ?: minutes * 60)
        alarms.ensureNotifications()
        if (!AlarmScheduler.canExact(getApplication())) promptExactAfterSpeak = true
        finishVoice("Timer for ${com.lumen.launcher.voice.intent.VoiceDuration.describe(intent.durationSeconds ?: minutes * 60)}.")
    }

    private fun voiceNavigate(intent: VoiceIntent) {
        val place = intent.textValue ?: intent.appName
        if (place.isNullOrBlank()) {
            finishVoice("Where should I navigate?")
            return
        }
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(place)}"))
        val maps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))
        if (home.startIntent(nav) || home.startIntent(maps)) {
            finishVoice("Navigating to $place.", continueTalking = false)
        } else {
            finishVoice("I couldn't open maps.")
        }
    }

    private fun voicePlay(intent: VoiceIntent) {
        if (!intent.appName.isNullOrBlank()) {
            val app = home.resolveApp(intent.appName, voice = true, minScore = 860)
            if (app == null) { finishVoice("I couldn't find ${intent.appName}."); return }
            val current = com.lumen.launcher.media.NowPlayingRepository.state.value
            if (current?.packageName == app.packageName) {
                if (!current.playing) com.lumen.launcher.media.NowPlayingRepository.playPause(getApplication())
                finishVoice(if (current.playing) "Already playing." else "Resuming playback.")
            } else openNamedApp(app.label)
            return
        }
        val query = intent.textValue ?: intent.appName
        if (!query.isNullOrBlank() && openNamedApp(query)) return
        val search = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query.orEmpty())}"))
        if (home.startIntent(search)) finishVoice("Opening video search for $query.", continueTalking = false)
        else finishVoice("I couldn't find something to play.")
    }

    private fun finishVoice(spoken: String, continueTalking: Boolean = true) {
        val heard = _state.value.voiceHeard
        val shareable = when (activeVoiceAction) {
            VoiceAction.INBOX_DIGEST -> home.snapshot().aiInboxSummary
            VoiceAction.CALL, VoiceAction.SEND_MESSAGE -> home.snapshot().aiContactNames
            VoiceAction.NEXT_EVENT, VoiceAction.DAILY_REVIEW, VoiceAction.SHOW_TASKS,
            VoiceAction.SAVE_NOTE, VoiceAction.COMPLETE_TASK, VoiceAction.DELETE_TASK,
            VoiceAction.WHERE_APP, VoiceAction.MOVE_TO_PRIVATE, VoiceAction.REMOVE_FROM_PRIVATE,
            VoiceAction.HIDE_APP, VoiceAction.UNHIDE_APP -> false
            else -> true
        }
        voiceConversation.record(heard, spoken, voiceAsksForDetail || spoken.trim().endsWith("?"), shareable)
        voiceAsksForDetail = false
        closeVoiceAfterSpeak = !continueTalking
        _state.update { it.copy(voiceHint = spoken, voiceListening = false, voiceCanRetry = false, voiceLevel = 0f) }
        onSpeak(spoken, continueTalking)
    }

    fun onSpeakFinished() {
        val close = closeVoiceAfterSpeak
        closeVoiceAfterSpeak = false
        val promptExact = promptExactAfterSpeak
        promptExactAfterSpeak = false
        if (close && home.snapshot().sheet == Sheet.Voice) closeVoiceOnly()
        if (promptExact) AlarmScheduler.requestExactAccess(getApplication())
    }

    private fun closeVoiceOnly() {
        clearVoicePending()
        voiceAiJob?.cancel()
        onStopVoice()
        if (home.snapshot().sheet == Sheet.Voice) home.setSheet(Sheet.None)
        _state.update { it.copy(voiceHeard = "", voiceHint = "", voiceListening = false, voiceLevel = 0f, voiceCanRetry = false) }
    }

    private fun runLauncherCommand(command: LauncherCommand) {
        if (getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            finishVoice("Unlock your phone first.", continueTalking = false)
            return
        }
        commandHandlers[command.javaClass]?.invoke(command)
    }

    private val commandHandlers: Map<Class<out LauncherCommand>, (LauncherCommand) -> Unit> by lazy {
        mapOf<Class<out LauncherCommand>, (LauncherCommand) -> Unit>(
            LauncherCommand.OpenFlow::class.java to { command -> handleCommandOpenFlow(command as LauncherCommand.OpenFlow) },
            LauncherCommand.OpenHome::class.java to { command -> handleCommandOpenHome(command as LauncherCommand.OpenHome) },
            LauncherCommand.OpenDrawer::class.java to { command -> handleCommandOpenDrawer(command as LauncherCommand.OpenDrawer) },
            LauncherCommand.OpenSearch::class.java to { command -> handleCommandOpenSearch(command as LauncherCommand.OpenSearch) },
            LauncherCommand.OpenRecents::class.java to { command -> handleCommandOpenRecents(command as LauncherCommand.OpenRecents) },
            LauncherCommand.OpenSettings::class.java to { command -> handleCommandOpenSettings(command as LauncherCommand.OpenSettings) },
            LauncherCommand.OpenPersonalize::class.java to { command -> handleCommandOpenPersonalize(command as LauncherCommand.OpenPersonalize) },
            LauncherCommand.OpenPrivate::class.java to { command -> handleCommandOpenPrivate(command as LauncherCommand.OpenPrivate) },
            LauncherCommand.SwitchSpace::class.java to { command -> handleCommandSwitchSpace(command as LauncherCommand.SwitchSpace) },
            LauncherCommand.IconSize::class.java to { command -> handleCommandIconSize(command as LauncherCommand.IconSize) },
            LauncherCommand.Labels::class.java to { command -> handleCommandLabels(command as LauncherCommand.Labels) },
            LauncherCommand.Grid::class.java to { command -> handleCommandGrid(command as LauncherCommand.Grid) },
            LauncherCommand.DockCapacity::class.java to { command -> handleCommandDockCapacity(command as LauncherCommand.DockCapacity) },
            LauncherCommand.CreateFolder::class.java to { command -> handleCommandCreateFolder(command as LauncherCommand.CreateFolder) },
            LauncherCommand.AddToFolder::class.java to { command -> handleCommandAddToFolder(command as LauncherCommand.AddToFolder) },
            LauncherCommand.Pin::class.java to { command -> handleCommandPin(command as LauncherCommand.Pin) },
            LauncherCommand.Unpin::class.java to { command -> handleCommandUnpin(command as LauncherCommand.Unpin) },
            LauncherCommand.Dock::class.java to { command -> handleCommandDock(command as LauncherCommand.Dock) },
            LauncherCommand.Undock::class.java to { command -> handleCommandUndock(command as LauncherCommand.Undock) },
            LauncherCommand.HideApp::class.java to { command -> handleCommandHideApp(command as LauncherCommand.HideApp) },
            LauncherCommand.UnhideApp::class.java to { command -> handleCommandUnhideApp(command as LauncherCommand.UnhideApp) },
            LauncherCommand.MoveToPrivate::class.java to { command -> handleCommandMoveToPrivate(command as LauncherCommand.MoveToPrivate) },
            LauncherCommand.RemoveFromPrivate::class.java to { command -> handleCommandRemoveFromPrivate(command as LauncherCommand.RemoveFromPrivate) },
            LauncherCommand.WhereApp::class.java to { command -> handleCommandWhereApp(command as LauncherCommand.WhereApp) },
            LauncherCommand.LearnAlias::class.java to { command -> handleCommandLearnAlias(command as LauncherCommand.LearnAlias) },
            LauncherCommand.HideNews::class.java to { command -> handleCommandHideNews(command as LauncherCommand.HideNews) },
            LauncherCommand.ShowNews::class.java to { command -> handleCommandShowNews(command as LauncherCommand.ShowNews) },
            LauncherCommand.SetFlowCard::class.java to { command -> handleCommandSetFlowCard(command as LauncherCommand.SetFlowCard) },
            LauncherCommand.MoveFlowCard::class.java to { command -> handleCommandMoveFlowCard(command as LauncherCommand.MoveFlowCard) },
            LauncherCommand.NeedNow::class.java to { command -> handleCommandNeedNow(command as LauncherCommand.NeedNow) },
            LauncherCommand.UsedYesterday::class.java to { command -> handleCommandUsedYesterday(command as LauncherCommand.UsedYesterday) },
            LauncherCommand.Weather::class.java to { command -> handleCommandWeather(command as LauncherCommand.Weather) },
            LauncherCommand.NextEvent::class.java to { command -> handleCommandNextEvent(command as LauncherCommand.NextEvent) },
            LauncherCommand.InboxDigest::class.java to { command -> handleCommandInboxDigest(command as LauncherCommand.InboxDigest) },
            LauncherCommand.Help::class.java to { command -> handleCommandHelp(command as LauncherCommand.Help) },
            LauncherCommand.EndTalk::class.java to { command -> handleCommandEndTalk(command as LauncherCommand.EndTalk) },
            LauncherCommand.OpenApp::class.java to { command -> handleCommandOpenApp(command as LauncherCommand.OpenApp) },
            LauncherCommand.Purpose::class.java to { command -> handleCommandPurpose(command as LauncherCommand.Purpose) }
        )
    }

    private fun handleCommandOpenFlow(command: LauncherCommand.OpenFlow) {
        home.goToPage(0)
        finishVoice("Opening Flow.")
    }

    private fun handleCommandOpenHome(command: LauncherCommand.OpenHome) {
        home.goToPage(1)
        finishVoice("Home.")
    }

    private fun handleCommandOpenDrawer(command: LauncherCommand.OpenDrawer) {
        home.openDrawer()
        finishVoice("All apps.", continueTalking = false)
    }

    private fun handleCommandOpenSearch(command: LauncherCommand.OpenSearch) {
        home.openSearch()
        finishVoice("Search.", continueTalking = false)
    }

    private fun handleCommandOpenRecents(command: LauncherCommand.OpenRecents) {
        home.goToPage(1, recents = true)
        finishVoice("Recents.")
    }

    private fun handleCommandOpenSettings(command: LauncherCommand.OpenSettings) {
        home.openSettings()
        finishVoice("Lumen settings.", continueTalking = false)
    }

    private fun handleCommandOpenPersonalize(command: LauncherCommand.OpenPersonalize) {
        home.goToPage(0)
        home.personalizeFlow()
        finishVoice("Personalize Flow.", continueTalking = false)
    }

    private fun handleCommandOpenPrivate(command: LauncherCommand.OpenPrivate) {
        home.goToPage(1)
        home.openPrivateSpace()
        finishVoice("Locked Space needs your unlock.", continueTalking = false)
    }

    private fun handleCommandSwitchSpace(command: LauncherCommand.SwitchSpace) {
        home.selectSpace(command.space)
        home.goToPage(1)
        finishVoice(
            if (command.space == null) "Back to the automatic layout."
            else "Switched to ${command.space.title}."
        )
    }

    private fun handleCommandIconSize(command: LauncherCommand.IconSize) {
        val current = home.snapshot().iconSizeDp
        val next = (command.sizeDp ?: (current + (command.delta ?: 0f))).coerceIn(44f, 72f)
        home.setIconSize(next)
        finishVoice("Icon size updated.")
    }

    private fun handleCommandLabels(command: LauncherCommand.Labels) {
        home.setShowLabels(command.show)
        finishVoice(if (command.show) "App labels are on." else "App labels are hidden.")
    }

    private fun handleCommandGrid(command: LauncherCommand.Grid) {
        home.setGridColumns(command.columns)
        finishVoice("Home grid set to ${command.columns} columns.")
    }

    private fun handleCommandDockCapacity(command: LauncherCommand.DockCapacity) {
        home.setDockCapacity(command.count)
        finishVoice("Dock set to ${command.count} apps.")
    }

    private fun handleCommandCreateFolder(command: LauncherCommand.CreateFolder) {
        val name = command.name?.trim().orEmpty()
        if (name.isBlank()) {
            home.openFolderCreator()
            finishVoice("Name the folder.", continueTalking = false)
        } else {
            voiceCreateFolder(name, command.apps)
        }
    }

    private fun handleCommandAddToFolder(command: LauncherCommand.AddToFolder) {
        voiceAddToFolder(
        command.appName,
        command.folderName,
        command.extraApps
            )
    }

    private fun handleCommandPin(command: LauncherCommand.Pin) {
        voiceHomeApp(command.name, HomeVoice.Pin)
    }

    private fun handleCommandUnpin(command: LauncherCommand.Unpin) {
        voiceHomeApp(command.name, HomeVoice.Unpin)
    }

    private fun handleCommandDock(command: LauncherCommand.Dock) {
        voiceHomeApp(command.name, HomeVoice.Dock)
    }

    private fun handleCommandUndock(command: LauncherCommand.Undock) {
        voiceHomeApp(command.name, HomeVoice.Undock)
    }

    private fun handleCommandHideApp(command: LauncherCommand.HideApp) {
        voiceHomeApp(command.name, HomeVoice.Hide)
    }

    private fun handleCommandUnhideApp(command: LauncherCommand.UnhideApp) {
        voiceHomeApp(command.name, HomeVoice.Unhide)
    }

    private fun handleCommandMoveToPrivate(command: LauncherCommand.MoveToPrivate) {
        voiceMoveToPrivate(command.name, lock = true)
    }

    private fun handleCommandRemoveFromPrivate(command: LauncherCommand.RemoveFromPrivate) {
        voiceMoveToPrivate(command.name, lock = false)
    }

    private fun handleCommandWhereApp(command: LauncherCommand.WhereApp) {
        voiceWhereApp(command.name)
    }

    private fun handleCommandLearnAlias(command: LauncherCommand.LearnAlias) {
        voiceLearnAlias(command.alias, command.name)
    }

    private fun handleCommandHideNews(command: LauncherCommand.HideNews) {
        home.setFlowModule(FlowModule.News, false)
        val current = home.snapshot().newsInterests
        if (current.isNotEmpty()) parkedNews = current
        home.setNewsInterests(emptySet())
        home.goToPage(0)
        finishVoice("News is hidden from Flow.")
    }

    private fun handleCommandShowNews(command: LauncherCommand.ShowNews) {
        home.setFlowModule(FlowModule.News, true)
        val next = parkedNews.ifEmpty { setOf(NewsTopic.Technology.name, NewsTopic.World.name) }
        home.setNewsInterests(next)
        home.goToPage(0)
        finishVoice("News is back on Flow.")
    }

    private fun handleCommandSetFlowCard(command: LauncherCommand.SetFlowCard) {
        home.setFlowModule(command.module, command.enabled)
        home.goToPage(0)
        finishVoice(
            if (command.enabled) "${command.module.title} is on Flow."
            else "${command.module.title} is off Flow."
        )
    }

    private fun handleCommandMoveFlowCard(command: LauncherCommand.MoveFlowCard) {
        home.moveFlowModuleTo(command.module, command.before)
        home.goToPage(0)
        val above = command.before?.title
        finishVoice(
            if (above != null) "Moved ${command.module.title} above $above."
            else "Moved ${command.module.title} on Flow."
        )
    }

    private fun handleCommandNeedNow(command: LauncherCommand.NeedNow) {
        val hints = home.snapshot().needNowHints
        if (hints.isEmpty()) {
            finishVoice("I don't have a routine for this hour yet.")
        } else {
            home.goToPage(0)
            val task = home.snapshot().focusTask?.takeUnless { it.done }?.text
            val names = hints.joinToString(", ") { it.title }
            finishVoice(
                if (task.isNullOrBlank()) "Need now: $names."
                else "For $task, try $names."
            )
        }
    }

    private fun handleCommandUsedYesterday(command: LauncherCommand.UsedYesterday) {
        speakYesterday()
    }

    private fun handleCommandWeather(command: LauncherCommand.Weather) {
        val weather = home.snapshot().weather
        if (weather == null) {
            home.refreshWeather()
            finishVoice("I don't have the weather yet.")
        } else {
            finishVoice("${weather.temperatureLabel()} and ${weather.summary} in ${weather.place}.")
        }
    }

    private fun handleCommandNextEvent(command: LauncherCommand.NextEvent) {
        val upNext = home.snapshot().upNext
        val event = home.snapshot().nextEvent
        home.goToPage(0)
        finishVoice(
            when {
                upNext != null -> upNext.spoken
                event != null -> "${event.title} is next."
                else -> "Nothing coming up on the calendar or in notifications right now."
            }
        )
    }

    private fun handleCommandInboxDigest(command: LauncherCommand.InboxDigest) {
        val digest = home.snapshot().inboxDigest.ifBlank { InboxDigest.local(home.snapshot().inbox) }
        home.goToPage(0)
        finishVoice(
            digest.ifBlank { "I don't see unread messages yet. Allow notification access in Flow if you want a digest." }
        )
    }

    private fun handleCommandHelp(command: LauncherCommand.Help) {
        finishVoice(
        "I can open apps, set alarms and timers, add things to your list, call or message people, answer questions, change Home, and move apps to the dock or Locked Space. What do you need?"
            )
    }

    private fun handleCommandEndTalk(command: LauncherCommand.EndTalk) {
        finishVoice("Okay.", continueTalking = false)
    }

    private fun handleCommandOpenApp(command: LauncherCommand.OpenApp) {
        if (!openNamedApp(command.name)) {
            finishVoice("I couldn't find ${command.name}.")
        }
    }

    private fun handleCommandPurpose(command: LauncherCommand.Purpose) {
        showPurpose(command.query)
    }

    private fun voiceCreateFolder(name: String, apps: List<String> = emptyList()) {
        val folders = home.snapshot().folders.toMutableList()
        if (folders.any { it.name.equals(name, ignoreCase = true) }) {
            if (apps.isEmpty()) {
                finishVoice("You already have a $name folder.")
                return
            }
        } else {
            folders += HomeFolder(
                id = java.util.UUID.randomUUID().toString(),
                name = name.replaceFirstChar { it.titlecase() },
                appKeys = emptyList(),
                space = home.snapshot().activeSpace.name
            )
            home.setFolders(folders)
        }
        if (apps.isEmpty()) {
            home.goToPage(1)
            finishVoice("Created the $name folder. Say add an app to $name folder.")
            return
        }
        voiceAddToFolder(apps.first(), name, apps.drop(1))
    }

    private fun voiceAddToFolder(appName: String, folderName: String, extraApps: List<String> = emptyList()) {
        val names = listOf(appName) + extraApps
        val found = names.mapNotNull { home.resolveApp(it, minScore = 860, voice = true) }.distinctBy { it.key }
        if (found.isEmpty()) {
            finishVoice("I couldn't find $appName.")
            return
        }
        val folders = home.snapshot().folders.toMutableList()
        val index = folders.indexOfFirst { it.name.equals(folderName, ignoreCase = true) }
        val title = folderName.replaceFirstChar { it.titlecase() }
        if (index < 0) {
            finishVoice("I couldn't find a folder named $folderName. Tell me an existing folder name, or ask me to create one.")
            return
        } else {
            val folder = folders[index]
            folders[index] = folder.copy(appKeys = (folder.appKeys + found.map { it.key }).distinct())
        }
        home.setFolders(folders)
        home.goToPage(1)
        val labels = found.joinToString(", ") { it.label }
        finishVoice("Added $labels to ${folders.lastOrNull { it.name.equals(folderName, true) }?.name ?: title}.")
    }

    private fun voiceMoveToPrivate(name: String, lock: Boolean) {
        val generation = sessionGeneration
        home.authenticate({ viewModelScope.launch {
            if (isLocked() || generation != sessionGeneration) return@launch
            val app = home.resolveApp(name, includeHidden = true, includePrivate = true, minScore = 860, voice = true)
                ?: home.snapshot().apps.maxByOrNull { FuzzySearch.score(name, it.label) }?.takeIf { FuzzySearch.score(name, it.label) >= 860 }
            if (app == null) { finishVoice("I couldn't find $name."); return@launch }
            val next = home.snapshot().privateApps.toMutableSet()
            if (lock) next.add(app.key) else next.remove(app.key)
            home.setPrivateApps(next)
            home.lockPrivate()
            finishVoice(if (lock) "Moved ${app.label} to Locked Space." else "${app.label} is out of Locked Space.")
        } }, {})
    }

    private fun voiceWhereApp(name: String) {
        val app = home.resolveApp(name, includeHidden = false, includePrivate = home.snapshot().privateUnlocked, minScore = 860, voice = true)
        if (app == null) {
            finishVoice("I couldn't find $name.")
            return
        }
        val state = home.snapshot()
        val spots = mutableListOf<String>()
        if (app.key in state.privateApps) spots += "Locked Space"
        if (app.packageName in state.hidden) spots += "hidden"
        val dock = state.dockKeys ?: state.dock.map { it.key }
        if (app.key in dock) spots += "the dock"
        if (app.key in state.favorites) spots += "Home"
        state.folders.filter { app.key in it.appKeys }.forEach { spots += "the ${it.name} folder" }
        val spoken = when {
            spots.isEmpty() -> "${app.label} is in All apps."
            else -> "${app.label} is in ${spots.joinToString(" and ")}."
        }
        finishVoice(spoken)
    }

    private fun voiceLearnAlias(alias: String, name: String) {
        val app = home.resolveApp(name, includeHidden = true, includePrivate = true, minScore = 860, voice = true)
        if (app == null) {
            finishVoice("I couldn't find $name.")
            return
        }
        val key = AliasHints.aliasKey(alias)
        if (key.length !in 2..32 || key.split(' ').size > 6) {
            finishVoice("I couldn't keep that nickname.")
            return
        }
        home.rememberAlias(key, app.key)
        finishVoice("Okay. $key opens ${app.label}.")
    }

    private fun voiceHomeApp(name: String, action: HomeVoice) {
        val app = home.resolveApp(
            name,
            includeHidden = action == HomeVoice.Unhide,
            minScore = 860,
            voice = true
        )
        if (app == null) {
            finishVoice("I couldn't find $name.")
            return
        }
        when (action) {
            HomeVoice.Pin -> {
                val current = home.snapshot().favorites.toMutableList()
                if (app.key in current) {
                    finishVoice("${app.label} is already on Home.")
                } else {
                    current += app.key
                    home.setFavorites(current)
                    home.goToPage(1)
                    finishVoice("Pinned ${app.label} to Home.")
                }
            }
            HomeVoice.Unpin -> {
                val current = home.snapshot().favorites.toMutableList()
                if (app.key !in current) {
                    finishVoice("${app.label} isn't on Home.")
                } else {
                    current.remove(app.key)
                    home.setFavorites(current)
                    finishVoice("Removed ${app.label} from Home.")
                }
            }
            HomeVoice.Dock -> {
                val current = (home.snapshot().dockKeys ?: home.snapshot().dock.map { it.key }).toMutableList()
                when {
                    app.key in current -> finishVoice("${app.label} is already in the dock.")
                    current.size >= home.snapshot().dockCapacity -> finishVoice("The dock is full.")
                    else -> {
                        current += app.key
                        home.setDockKeys(current)
                        home.goToPage(1)
                        finishVoice("Added ${app.label} to the dock.")
                    }
                }
            }
            HomeVoice.Undock -> {
                val current = (home.snapshot().dockKeys ?: home.snapshot().dock.map { it.key }).toMutableList()
                if (current.none { it == app.key }) {
                    finishVoice("${app.label} isn't in the dock.")
                } else {
                    current.removeAll { it == app.key }
                    home.setDockKeys(current)
                    finishVoice("Removed ${app.label} from the dock.")
                }
            }
            HomeVoice.Hide -> {
                val generation = sessionGeneration
                home.authenticate({ viewModelScope.launch {
                    if (isLocked() || generation != sessionGeneration) return@launch
                    home.setHidden(home.snapshot().hidden + app.packageName)
                    finishVoice("Hidden ${app.label}.")
                } }, {})
            }
            HomeVoice.Unhide -> {
                val generation = sessionGeneration
                home.authenticate({ viewModelScope.launch {
                    if (isLocked() || generation != sessionGeneration) return@launch
                    home.setHidden(home.snapshot().hidden - app.packageName)
                    finishVoice("${app.label} is visible again.")
                } }, {})
            }
        }
    }

    private fun openNamedApp(raw: String): Boolean {
        val name = VoiceQuery.clean(raw)
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .removePrefix("run ")
            .removePrefix("go to ")
            .trim()
        val app = home.resolveApp(name, minScore = 860, voice = true) ?: return false
        if (!home.launchApp(app)) return false
        onSpeak("Opening ${app.label}.", false)
        return true
    }

    private fun speakYesterday() {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val apps = home.snapshot().launchTimes.entries
            .filter { it.value in start until end }
            .sortedByDescending { it.value }
            .mapNotNull { entry -> home.snapshot().visibleApps.find { it.key == entry.key } }
            .distinctBy { it.key }
        if (apps.isEmpty()) {
            finishVoice("I don't remember an app from yesterday yet.")
            return
        }
        val first = apps.first()
        finishVoice("Yesterday you used ${apps.take(3).joinToString(", ") { it.label }}. Opening ${first.label}.", continueTalking = false)
        home.launchApp(first)
    }

    private fun showPurpose(query: String) {
        val intent = IntentIndex.match(query).firstOrNull()
        val apps = if (intent != null) {
            IntentIndex.appsFor(intent, home.snapshot().visibleApps, 8)
        } else {
            home.snapshot().visibleApps.filter { it.category == AppCategory.Finance }.take(8)
        }
        if (apps.isEmpty()) {
            finishVoice("I don't see matching apps on this phone.")
            return
        }
        val title = intent?.title ?: "Finance"
        home.showPurposeResults(title, apps)
        finishVoice("Here are your ${title.lowercase()} apps.", continueTalking = false)
    }

    private fun formatHour(hour: Int, minute: Int): String {
        val mer = if (hour >= 12) "p m" else "a m"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return if (minute == 0) "$h $mer" else "$h ${minute.toString().padStart(2, '0')} $mer"
    }

}

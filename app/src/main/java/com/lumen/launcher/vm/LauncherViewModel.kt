package com.lumen.launcher.vm

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.lumen.launcher.data.AppCategory
import com.lumen.launcher.data.LumenThemeMode
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.AppRepository
import com.lumen.launcher.data.CalendarEvent
import com.lumen.launcher.data.CalendarRepository
import com.lumen.launcher.data.CalendarRole
import com.lumen.launcher.data.CallLogRepository
import com.lumen.launcher.data.DeviceCalendar
import com.lumen.launcher.data.DeviceFirstName
import com.lumen.launcher.data.MissedCall
import com.lumen.launcher.data.MissedSnapshot
import com.lumen.launcher.data.PhoneAccount
import com.lumen.launcher.badge.NotificationBadgeMode
import com.lumen.launcher.badge.NotificationBadgeRepository
import com.lumen.launcher.inbox.InboxHub
import com.lumen.launcher.inbox.InboxItem
import com.lumen.launcher.inbox.LumenNotificationListener
import com.lumen.launcher.inbox.MeetingHub
import com.lumen.launcher.inbox.WhatsAppMissedHub
import com.lumen.launcher.alarm.AlarmScheduler
import com.lumen.launcher.alarm.AlarmTonePlayer
import com.lumen.launcher.alarm.AlarmTones
import com.lumen.launcher.alarm.LumenAlarm
import com.lumen.launcher.data.DockApp
import com.lumen.launcher.data.DockResolver
import com.lumen.launcher.data.GestureAction
import com.lumen.launcher.data.HomeFolder
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.data.LauncherPreferences
import com.lumen.launcher.data.TouchpadHaptics
import com.lumen.launcher.lock.LockAdminReceiver
import com.lumen.launcher.data.ActionCard
import com.lumen.launcher.data.ContactLookup
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.data.SpaceSense
import com.lumen.launcher.data.CaptureKind
import com.lumen.launcher.data.CaptureNote
import com.lumen.launcher.data.DailyReview
import com.lumen.launcher.data.DailyReviewResolver
import com.lumen.launcher.data.FocusAppsResolver
import com.lumen.launcher.data.FocusPick
import com.lumen.launcher.data.IconSkin
import com.lumen.launcher.data.GlassDepth
import com.lumen.launcher.data.AlphabetIndex
import com.lumen.launcher.data.SmartClusterResolver
import com.lumen.launcher.data.SpaceWallpaper
import com.lumen.launcher.data.LaterItem
import com.lumen.launcher.data.NeedNowHint
import com.lumen.launcher.data.NeedNowResolver
import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.data.TodoRepeat
import com.lumen.launcher.data.TodoTime
import com.lumen.launcher.data.UpNext
import com.lumen.launcher.data.UpNextResolver
import com.lumen.launcher.inbox.InboxDigest
import com.lumen.launcher.search.AliasHints
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.IntentIndex
import com.lumen.launcher.search.LauncherCommand
import com.lumen.launcher.search.LauncherVoice
import com.lumen.launcher.search.PeopleActions
import com.lumen.launcher.search.Routine
import com.lumen.launcher.search.SearchHit
import com.lumen.launcher.search.SearchInterpreter
import com.lumen.launcher.search.VoiceCommands
import com.lumen.launcher.search.VoiceMatch
import com.lumen.launcher.BuildConfig
import com.lumen.launcher.voice.VoiceQuery
import com.lumen.launcher.voice.intent.AiIntentFallback
import com.lumen.launcher.voice.intent.VoiceAction
import com.lumen.launcher.voice.intent.VoiceConfidence
import com.lumen.launcher.voice.intent.VoiceIntent
import com.lumen.launcher.voice.intent.VoiceIntentMapper
import com.lumen.launcher.voice.intent.VoiceQueryRouter
import com.lumen.launcher.voice.intent.ClarificationResolution
import com.lumen.launcher.voice.intent.ClarificationResolver
import com.lumen.launcher.data.NewsItem
import com.lumen.launcher.data.NewsRepository
import com.lumen.launcher.data.NewsTopic
import com.lumen.launcher.data.WeatherRepository
import com.lumen.launcher.data.WeatherSnapshot
import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.social.SocialCreateCoordinator
import com.lumen.launcher.social.SocialCreateTool
import com.lumen.launcher.social.CreationItem
import com.lumen.launcher.flow.defaultFlowEnabled
import com.lumen.launcher.flow.defaultFlowNames
import com.lumen.launcher.flow.parseFlowOrder
import com.lumen.launcher.util.AssistantRole
import com.lumen.launcher.util.CompetingLaunchers
import com.lumen.launcher.util.HomeRole
import com.lumen.launcher.util.StatusBarController
import com.lumen.launcher.util.TorchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val preferences = LauncherPreferences(application)
    private val weatherRepository = WeatherRepository()
    private val newsRepository = NewsRepository()
    var onAuthenticate: ((onSuccess: () -> Unit, onFail: () -> Unit) -> Unit)? = null
    var onRequestContacts: (() -> Unit)? = null
    var onStartActivity: ((Intent) -> Boolean)? = null
    var onRequestMic: (() -> Unit)? = null
    var onRequestMicQuiet: (() -> Unit)? = null
    var onRequestCalendar: (() -> Unit)? = null
    var onRequestCallLog: (() -> Unit)? = null
    var onRequestNotifications: (() -> Unit)? = null
    var onSpeak: ((String, Boolean) -> Unit)? = null
    var onListen: (() -> Unit)? = null
    var onStopVoice: (() -> Unit)? = null
    var onRequestAssistant: (() -> Unit)? = null
    private var skipVoiceGreeting = false
    private var closeVoiceAfterSpeak = false
    private var promptExactAfterSpeak = false
    private var parkedNews: Set<String> = emptySet()
    private var voiceMisses = 0
    private val voiceTurns = ArrayDeque<String>()
    private var voiceAiJob: kotlinx.coroutines.Job? = null
    private var pendingVoiceIntent: VoiceIntent? = null
    private var deviceEvents: List<CalendarEvent> = emptyList()
    private var noticeEvents: List<CalendarEvent> = emptyList()
    private var digestStamp = ""
    private val torch = TorchController(application)
    val icons = IconCache(application)
    val socialCreate = SocialCreateCoordinator(application, viewModelScope)
    private val contacts = ContactLookup(application)
    private var pendingPeople: SearchHit.Action? = null
    private var padLeft = 0f
    private var padTop = 0f
    private var padRight = 0f
    private var padBottom = 0f
    private val threeDaysMs = 3L * 24 * 60 * 60 * 1000

    private val _state = MutableStateFlow(LauncherUiState())
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) == false
            ) {
                intent.data?.schemeSpecificPart?.let { NotificationBadgeRepository.removePackage(it) }
            }
            refreshApps()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            application.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(packageReceiver, filter)
        }
        viewModelScope.launch {
            socialCreate.creations.collect { list ->
                _state.update { it.copy(socialCreations = list) }
            }
        }
        viewModelScope.launch {
            InboxHub.items.collect { items ->
                _state.update {
                    applyContext(
                        it.copy(inbox = items, inboxAccess = InboxHub.hasAccess(getApplication()))
                    )
                }
                refreshInboxDigest(items)
            }
        }
        viewModelScope.launch {
            MeetingHub.items.collect { notices ->
                noticeEvents = notices
                applyCalendarMerge()
            }
        }
        viewModelScope.launch {
            WhatsAppMissedHub.snapshot.collect { snap ->
                mergeWhatsAppMissed(snap.missed, snap.outgoing)
                refreshMissedCalls()
            }
        }
        viewModelScope.launch {
            refreshApps()
            refreshWeather()
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(15 * 60 * 1000L)
                    refreshWeather()
                }
            }
            var lastInterests: Set<String>? = null
            var lastCalendarKey: String? = null
            var lastAlarmKey: String? = null
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(60_000L)
                    _state.update { applyContext(it) }
                }
            }
            preferences.state.collect { stored ->
                _state.update { current ->
                    current.copy(
                        favorites = stored.favorites,
                        recents = stored.recents,
                        launchTimes = stored.launchTimes,
                        hidden = stored.hidden,
                        iconSizeDp = stored.iconSizeDp,
                        gridColumns = stored.gridColumns,
                        drawerColumns = stored.drawerColumns,
                        dockCapacity = stored.dockCapacity,
                        showLabels = stored.showLabels,
                        iconSkin = IconSkin.parse(stored.iconSkin),
                        theme = LumenThemeMode.parse(stored.theme),
                        glassDepth = GlassDepth.parse(stored.glassDepth),
                        smartCluster = stored.smartCluster,
                        aliases = stored.aliases,
                        spaceOverride = stored.spaceOverride
                            ?.let { runCatching { SpaceKind.valueOf(it) }.getOrNull() }
                            ?.takeUnless { it == SpaceKind.Private },
                        privateApps = stored.privateApps,
                        newsInterests = stored.newsInterests,
                        dockKeys = stored.dockKeys,
                        dock = DockResolver.resolve(getApplication(), current.apps, stored.dockKeys, stored.dockCapacity),
                        tapAction = stored.tapAction,
                        doubleAction = stored.doubleAction,
                        tripleAction = stored.tripleAction,
                        longAction = stored.longAction,
                        swipeUpAction = stored.swipeUpAction,
                        swipeDownAction = stored.swipeDownAction,
                        swipeLeftAction = stored.swipeLeftAction,
                        swipeRightAction = stored.swipeRightAction,
                        touchpadHaptics = stored.touchpadHaptics,
                        tasks = stored.tasks,
                        todos = stored.todos,
                        heyLumen = stored.heyLumen,
                        smartVoice = stored.smartVoice,
                        geminiApiKey = stored.geminiApiKey,
                        flowEnabled = stored.flowEnabled,
                        flowOrder = stored.flowOrder,
                        folders = stored.folders,
                        workCalendars = stored.workCalendars,
                        personalCalendars = stored.personalCalendars,
                        alarms = stored.alarms,
                        alarmTone = stored.alarmTone,
                        storedWhatsAppMissed = stored.whatsAppMissed,
                        launchHours = stored.launchHours,
                        spaceDocks = stored.spaceDocks,
                        notes = stored.notes,
                        later = stored.later,
                        focusUntil = stored.focusUntil,
                        focusTaskId = stored.focusTaskId,
                        focusPins = stored.focusPins,
                        spaceWallpapers = stored.spaceWallpapers,
                        notificationBadges = stored.notificationBadges
                    )
                }
                _state.update { applyContext(it) }
                if (lastInterests != stored.newsInterests) {
                    lastInterests = stored.newsInterests
                    loadNews()
                }
                val calKey = "${stored.spaceOverride}|${stored.workCalendars}|${stored.personalCalendars}"
                if (lastCalendarKey != calKey) {
                    lastCalendarKey = calKey
                    refreshCalendar()
                }
                val alarmKey = "${stored.alarms}|${stored.alarmTone}"
                if (lastAlarmKey != alarmKey) {
                    lastAlarmKey = alarmKey
                    AlarmScheduler.rescheduleAll(getApplication())
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = repository.loadLaunchableApps()
            _state.update {
                applyContext(
                    it.copy(
                        apps = apps,
                        isDefaultHome = isDefaultHome(),
                        competingLaunchers = CompetingLaunchers.find(getApplication())
                    )
                )
            }
        }
    }

    fun onHomeVisible() {
        lockPrivate()
        _state.update {
            it.copy(
                isDefaultHome = isDefaultHome(),
                query = "",
                sheet = Sheet.None,
                privatePageActive = false,
                homePulse = it.homePulse + 1
            )
        }
        refreshWeather()
        _state.update { applyContext(it) }
        refreshInboxDigest(_state.value.inbox)
    }

    private fun refreshWeather() {
        viewModelScope.launch {
            val weather = runCatching { weatherRepository.load() }.getOrNull()
            _state.update { it.copy(weather = weather) }
        }
    }

    fun refreshFlow() {
        refreshWeather()
        refreshCalendar()
        refreshMissedCalls()
        val inboxAccess = InboxHub.hasAccess(getApplication())
        if (inboxAccess) {
            val component = ComponentName(getApplication(), LumenNotificationListener::class.java)
            runCatching { NotificationListenerService.requestRebind(component) }
        }
        _state.update {
            it.copy(
                inboxAccess = inboxAccess,
                calendarAccess = ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED,
                firstName = DeviceFirstName.read(getApplication())
            )
        }
        if (_state.value.newsInterests.isNotEmpty()) loadNews()
        _state.update { applyContext(it) }
        refreshInboxDigest(_state.value.inbox)
    }

    fun requestInboxAccess() {
        val component = ComponentName(getApplication(), LumenNotificationListener::class.java)
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else null
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (detail == null || !startIntent(detail)) startIntent(list)
    }

    fun openMailApp() {
        val preferred = _state.value.inbox.firstOrNull()?.packageName
        val order = listOfNotNull(preferred, "com.google.android.gm", "com.microsoft.office.outlook")
        for (pkg in order.distinct()) {
            val launch = getApplication<Application>().packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null && startIntent(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        }
    }

    fun openInboxItem(item: InboxItem) {
        val pending = InboxHub.contentIntent(item.key)
        val launched = pending != null && runCatching { pending.send() }.isSuccess
        if (!launched) {
            val launch = getApplication<Application>().packageManager.getLaunchIntentForPackage(item.packageName)
            if (launch != null) startIntent(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun requestCalendarAccess() {
        onRequestCalendar?.invoke()
    }

    fun requestCallLogAccess() {
        if (!AssistantRole.isHeld(getApplication())) {
            onRequestAssistant?.invoke()
            return
        }
        onRequestCallLog?.invoke()
    }

    fun requestNotificationAccess() {
        onRequestNotifications?.invoke()
    }

    private fun ensureAlarmNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onRequestNotifications?.invoke()
        }
        if (!AlarmScheduler.canExact(getApplication())) {
            promptExactAfterSpeak = true
        }
    }

    fun onCalendarPermission(granted: Boolean) {
        _state.update { it.copy(calendarAccess = granted) }
        if (granted) refreshCalendar()
    }

    fun onCallLogPermission(granted: Boolean) {
        _state.update { it.copy(callLogAccess = granted) }
        if (granted) refreshMissedCalls()
    }

    fun setCalendarRoles(work: Set<String>, personal: Set<String>) {
        viewModelScope.launch { preferences.setCalendarRoles(work, personal) }
    }

    fun addGoogleAccount() {
        addAccount(arrayOf("com.google"))
    }

    fun addWorkAccount() {
        addAccount(
            arrayOf(
                "com.microsoft.workaccount",
                "com.microsoft.office.outlook",
                "com.android.exchange",
                "com.google.android.gm.exchange"
            )
        )
    }

    fun openCalendarSync() {
        val sync = Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startIntent(sync)) {
            startIntent(Intent(Settings.ACTION_ADD_ACCOUNT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun addAccount(types: Array<String>) {
        val add = Intent(Settings.ACTION_ADD_ACCOUNT)
            .putExtra(Settings.EXTRA_ACCOUNT_TYPES, types)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startIntent(add)) openCalendarSync()
    }

    private fun refreshCalendar() {
        viewModelScope.launch {
            val repo = CalendarRepository(getApplication())
            val calendars = withContext(Dispatchers.IO) { repo.listCalendars() }
            val accounts = withContext(Dispatchers.IO) { repo.listPhoneAccounts() }
            val ids = calendarIdsFor(
                _state.value.workCalendars,
                _state.value.personalCalendars
            )
            val events = withContext(Dispatchers.IO) { repo.upcoming(ids, 3) }
            deviceEvents = events
            applyCalendarMerge()
            _state.update {
                it.copy(
                    deviceCalendars = calendars,
                    phoneAccounts = accounts,
                    calendarAccess = ContextCompat.checkSelfPermission(
                        getApplication(),
                        Manifest.permission.READ_CALENDAR
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
        }
    }

    private fun applyCalendarMerge() {
        val merged = mergeCalendar(deviceEvents, noticeEvents)
        _state.update {
            applyContext(
                it.copy(
                    upcomingEvents = merged,
                    nextEvent = merged.firstOrNull()
                )
            )
        }
    }

    private fun mergeCalendar(
        device: List<CalendarEvent>,
        notices: List<CalendarEvent>
    ): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val kept = ArrayList<CalendarEvent>()
        for (event in device.filter { it.end > now - 60_000L }) kept += event
        for (notice in notices.filter { it.end > now - 60_000L }) {
            val duplicate = kept.any { sameMeeting(it, notice) }
            if (!duplicate) kept += notice
        }
        return kept.sortedBy { it.begin }.take(3)
    }

    private fun sameMeeting(a: CalendarEvent, b: CalendarEvent): Boolean {
        if (!a.title.trim().equals(b.title.trim(), ignoreCase = true)) return false
        return kotlin.math.abs(a.begin - b.begin) < 30 * 60 * 1000L
    }

    private fun applyContext(state: LauncherUiState): LauncherUiState {
        val hour = java.time.LocalTime.now().hour
        val weekday = java.time.LocalDate.now().dayOfWeek.value
        val inferred = SpaceSense.infer(
            hour = hour,
            weekday = weekday,
            recents = state.recents,
            hourHits = state.launchHours,
            apps = state.apps,
            nextEvent = state.nextEvent
        )
        val space = state.spaceOverride ?: inferred
        val keys = SpaceSense.dockKeys(space, state.spaceDocks, state.dockKeys)
        val dock = DockResolver.resolve(getApplication(), state.apps, keys, state.dockCapacity)
        val upNext = UpNextResolver.resolve(System.currentTimeMillis(), state.upcomingEvents, state.inbox)
        val now = System.currentTimeMillis()
        val focusGone = state.focusUntil > 0L && state.focusUntil <= now
        return state.copy(
            inferredSpace = inferred,
            dock = dock,
            upNext = upNext,
            focusUntil = if (focusGone) 0L else state.focusUntil,
            focusTaskId = if (focusGone) "" else state.focusTaskId
        )
    }

    private fun liveDockKeys(): List<String> {
        val state = _state.value
        return SpaceSense.dockKeys(state.activeSpace, state.spaceDocks, state.dockKeys)
            ?: state.dock.map { it.key }
    }

    private fun refreshInboxDigest(items: List<InboxItem>) {
        val stamp = InboxDigest.fingerprint(items)
        val local = InboxDigest.local(items)
        if (local.isNotBlank()) {
            _state.update { it.copy(inboxDigest = local) }
        }
        if (stamp == digestStamp && _state.value.inboxDigest.isNotBlank()) return
        digestStamp = stamp
        val key = _state.value.geminiApiKey
        viewModelScope.launch(Dispatchers.IO) {
            val text = InboxDigest.summarize(key, items)
            _state.update { it.copy(inboxDigest = text) }
        }
    }

    fun openUpNext(item: UpNext) {
        when {
            item.mapsQuery.isNotBlank() -> {
                val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(item.mapsQuery)}"))
                val maps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(item.mapsQuery)}"))
                if (!startIntent(nav)) startIntent(maps)
            }
            item.event != null -> openCalendarEvent(item.event)
            item.inbox != null -> openInboxItem(item.inbox)
        }
    }

    private fun calendarIdsFor(
        work: Set<String>,
        personal: Set<String>
    ): Set<Long>? {
        val taggedWork = work.mapNotNull { it.toLongOrNull() }.toSet()
        val taggedPersonal = personal.mapNotNull { it.toLongOrNull() }.toSet()
        if (taggedWork.isEmpty() && taggedPersonal.isEmpty()) return null
        return (taggedWork + taggedPersonal).ifEmpty { null }
    }

    private fun mergeWhatsAppMissed(live: List<MissedCall>, outgoing: Set<String>) {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - threeDaysMs
            val merged = (_state.value.storedWhatsAppMissed + live)
                .filter { it.at >= since && it.key !in outgoing }
                .groupBy { it.key }
                .map { (_, rows) ->
                    val latest = rows.maxBy { it.at }
                    latest.copy(
                        name = rows.firstOrNull { it.name.isNotBlank() }?.name.orEmpty(),
                        number = rows.firstOrNull { it.number.isNotBlank() }?.number.orEmpty(),
                        count = rows.maxOf { it.count }.coerceAtLeast(rows.size)
                    )
                }
                .sortedByDescending { it.at }
            if (merged.map { it.key to it.at } != _state.value.storedWhatsAppMissed.map { it.key to it.at }) {
                preferences.setWhatsAppMissed(merged)
            }
        }
    }

    private fun refreshMissedCalls() {
        viewModelScope.launch {
            val granted = ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
            val log = if (granted) {
                withContext(Dispatchers.IO) { CallLogRepository(getApplication()).snapshot() }
            } else MissedSnapshot()
            val live = WhatsAppMissedHub.snapshot.value
            val since = System.currentTimeMillis() - threeDaysMs
            val outgoing = log.outgoingKeys + live.outgoing
            val whatsapp = (_state.value.storedWhatsAppMissed + live.missed + log.calls.filter { it.whatsapp })
                .filter { it.at >= since && it.key !in outgoing }
                .groupBy { it.key }
                .map { (_, rows) -> rows.maxBy { it.at }.copy(count = rows.maxOf { it.count }.coerceAtLeast(rows.size)) }
            val phone = log.calls.filter { !it.whatsapp && it.key !in outgoing && it.at >= since }
            val merged = (phone + whatsapp)
                .distinctBy { it.key }
                .sortedByDescending { it.at }
                .take(3)
            _state.update { it.copy(missedCalls = merged, callLogAccess = granted) }
        }
    }

    fun openNeedNow(hint: NeedNowHint) {
        val name = hint.phone ?: hint.title.removePrefix("Call ").trim()
        if (hint.title.startsWith("Call ", ignoreCase = true) && name.isNotBlank()) {
            contacts.matches(name, 1).firstOrNull()?.let { match ->
                val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", match.phone, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (startIntent(dial)) return
            }
        }
        hint.app?.let { launch(it) }
    }

    fun callBack(missed: MissedCall) {
        if (missed.whatsapp) {
            val launched = WhatsAppMissedHub.contentIntent(missed.key)?.let { pending ->
                runCatching { pending.send() }.isSuccess
            } == true
            if (launched) return
            val phone = missed.number.ifBlank {
                contacts.matches(missed.name, 1).firstOrNull()?.phone.orEmpty()
            }
            openWhatsApp(phone, "")
            return
        }
        val number = missed.number.trim()
        if (number.isBlank()) {
            openCallLog()
            return
        }
        val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startIntent(dial)) openCallLog()
    }

    fun openCallLog() {
        val onlyWhatsApp = _state.value.missedCalls.isNotEmpty() &&
            _state.value.missedCalls.all { it.whatsapp }
        if (onlyWhatsApp) {
            openWhatsApp("", "")
            return
        }
        val log = Intent(Intent.ACTION_VIEW).apply {
            type = CallLog.Calls.CONTENT_TYPE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (!startIntent(log)) {
            startIntent(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun completeTask(item: String) {
        val match = matchTodo(item) ?: return
        toggleTodo(match.id, done = true)
    }

    fun addTodo(
        text: String,
        dueAt: Long? = TodoTime.startOfDay(),
        repeat: String = "",
        priority: Boolean = false
    ): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""
        val space = _state.value.activeSpace.takeUnless { it == SpaceKind.Private } ?: SpaceKind.Home
        val item = TodoItem(
            id = java.util.UUID.randomUUID().toString().take(8),
            text = trimmed,
            done = false,
            space = space,
            createdAt = System.currentTimeMillis(),
            dueAt = dueAt,
            priority = priority,
            repeat = repeat
        )
        val next = (_state.value.todos.filterNot {
            !it.done && it.space == space && it.text.equals(trimmed, ignoreCase = true)
        } + item).take(80)
        persistTodos(next)
        return item.id
    }

    fun setTodoDue(id: String, dueAt: Long) {
        persistTodos(_state.value.todos.map { item ->
            if (item.id == id) item.copy(dueAt = dueAt) else item
        })
    }

    fun setTodoRepeat(id: String, repeat: String) {
        persistTodos(_state.value.todos.map { item ->
            if (item.id == id) item.copy(repeat = repeat) else item
        })
    }

    fun setTodoPriority(id: String, priority: Boolean) {
        persistTodos(_state.value.todos.map { item ->
            if (item.id == id) item.copy(priority = priority) else item
        })
    }

    fun toggleTodo(id: String, done: Boolean? = null) {
        val next = _state.value.todos.map { item ->
            if (item.id != id) item
            else {
                val markingDone = done ?: !item.done
                if (markingDone && item.repeat.isNotBlank()) {
                    val due = item.dueAt ?: System.currentTimeMillis()
                    item.copy(done = false, dueAt = TodoRepeat.nextDue(due, item.repeat))
                } else {
                    item.copy(done = markingDone)
                }
            }
        }
        persistTodos(next)
    }

    fun deleteTodo(id: String) {
        persistTodos(_state.value.todos.filterNot { it.id == id })
    }

    private fun persistTodos(next: List<TodoItem>) {
        viewModelScope.launch { preferences.setTodos(next) }
        _state.update { it.copy(todos = next) }
    }

    private fun matchTodo(raw: String, includeDone: Boolean = true): TodoItem? {
        val spaceItems = _state.value.spaceTodos.let { items ->
            if (includeDone) items else items.filterNot { it.done }
        }
        val q = FuzzySearch.normalize(raw)
            .removePrefix("the ")
            .removeSuffix(" as done")
            .removeSuffix(" done")
            .trim()
        if (q.isBlank() || q == "this" || q == "it" || q == "this task") {
            return spaceItems.filterNot { it.done }.lastOrNull()
        }
        return spaceItems.maxByOrNull { FuzzySearch.score(q, it.text) }
            ?.takeIf { FuzzySearch.score(q, it.text) >= 620 }
    }

    fun addAlarm(hour: Int, minute: Int, daily: Boolean = false, label: String = ""): LumenAlarm {
        val existing = _state.value.alarms.find { it.hour == hour && it.minute == minute }
        val alarm = (existing ?: LumenAlarm(hour = hour, minute = minute, daily = daily, label = label))
            .copy(enabled = true, daily = daily || (existing?.daily == true), label = label.ifBlank { existing?.label.orEmpty() })
        val next = (_state.value.alarms.filterNot { it.id == alarm.id } + alarm).take(8)
        viewModelScope.launch { preferences.setAlarms(next) }
        _state.update { it.copy(alarms = next) }
        AlarmScheduler.schedule(getApplication(), alarm, _state.value.alarmTone)
        ensureAlarmNotifications()
        return alarm
    }

    fun toggleAlarm(id: String) {
        val next = _state.value.alarms.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        viewModelScope.launch { preferences.setAlarms(next) }
        _state.update { it.copy(alarms = next) }
        next.find { it.id == id }?.let {
            AlarmScheduler.schedule(getApplication(), it, _state.value.alarmTone)
            if (it.enabled) ensureAlarmNotifications()
        }
    }

    fun deleteAlarm(id: String) {
        AlarmScheduler.cancel(getApplication(), id)
        val next = _state.value.alarms.filterNot { it.id == id }
        viewModelScope.launch { preferences.setAlarms(next) }
        _state.update { it.copy(alarms = next) }
    }

    fun cycleAlarmTone() {
        val tone = AlarmTones.next(_state.value.alarmTone)
        viewModelScope.launch { preferences.setAlarmTone(tone) }
        _state.update { it.copy(alarmTone = tone) }
        AlarmTonePlayer.preview(getApplication(), tone)
    }

    private fun cancelAlarms(hour: Int?, minute: Int?): Int {
        val current = _state.value.alarms
        val victims = if (hour == null) {
            current.filter { it.enabled }
        } else {
            current.filter { it.hour == hour && (minute == null || it.minute == minute) }
        }
        if (victims.isEmpty()) return 0
        victims.forEach { AlarmScheduler.cancel(getApplication(), it.id) }
        val next = current.filterNot { alarm -> victims.any { it.id == alarm.id } }
        viewModelScope.launch { preferences.setAlarms(next) }
        _state.update { it.copy(alarms = next) }
        return victims.size
    }

    private fun upcomingAlarms(): List<LumenAlarm> {
        return _state.value.alarms.filter { it.enabled }.sortedBy { it.nextTriggerMs() }
    }

    fun openCalendarEvent(event: CalendarEvent) {
        if (event.noticeKey.isNotBlank()) {
            val pending = MeetingHub.contentIntent(event.noticeKey)
            if (pending != null && runCatching { pending.send() }.isSuccess) return
            val packages = if (event.teams) {
                listOf(
                    "com.microsoft.teams",
                    "com.microsoft.skype.teams",
                    "com.microsoft.office.outlook"
                )
            } else {
                listOf("com.microsoft.office.outlook", "com.microsoft.teams")
            }
            for (pkg in packages) {
                val launch = getApplication<Application>().packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null && startIntent(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
            }
        }
        if (event.noticeKey.isBlank()) {
            val view = Intent(Intent.ACTION_VIEW).apply {
                data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (startIntent(view)) return
        }
        startIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
    }

    fun openCalendarApp() {
        val calendar = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startIntent(calendar)) {
            startIntent(
                Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun loadNews() {
        viewModelScope.launch {
            val topics = _state.value.selectedNewsTopics
            if (topics.isEmpty()) {
                _state.update { it.copy(news = emptyList(), newsLoading = false) }
                return@launch
            }
            _state.update { it.copy(newsLoading = true) }
            val items = runCatching { newsRepository.load(topics) }.getOrDefault(emptyList())
            _state.update { it.copy(news = items, newsLoading = false) }
        }
    }

    fun setNewsInterests(names: Set<String>) {
        viewModelScope.launch { preferences.setNewsInterests(names) }
    }

    fun toggleNewsInterest(topic: NewsTopic) {
        viewModelScope.launch {
            val next = _state.value.newsInterests.toMutableSet()
            if (topic.name in next) next.remove(topic.name) else next.add(topic.name)
            preferences.setNewsInterests(next)
        }
    }

    fun openNews(item: NewsItem) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun lockPrivate() {
        _state.update { it.copy(privateUnlocked = false) }
    }

    fun unlockPrivate() {
        onAuthenticate?.invoke(
            {
                _state.update { it.copy(privateUnlocked = true, privatePageActive = true, privatePrompting = false) }
            },
            { _state.update { it.copy(privatePrompting = false) } }
        )
    }

    fun openPrivateSpace() {
        armPrivateSpace()
    }

    fun armPrivateSpace() {
        snapshotTouchpad()
        if (_state.value.privateUnlocked) {
            _state.update { it.copy(privatePageActive = true, privatePrompting = false) }
            return
        }
        _state.update { it.copy(privatePrompting = true) }
        onAuthenticate?.invoke(
            {
                snapshotTouchpad()
                _state.update { it.copy(privateUnlocked = true, privatePageActive = true, privatePrompting = false) }
            },
            { _state.update { it.copy(privatePrompting = false) } }
        )
    }

    fun setTouchpadWindow(left: Float, top: Float, right: Float, bottom: Float) {
        padLeft = left
        padTop = top
        padRight = right
        padBottom = bottom
    }

    private fun snapshotTouchpad() {
        _state.update {
            if (
                it.touchpadLeft == padLeft &&
                it.touchpadTop == padTop &&
                it.touchpadRight == padRight &&
                it.touchpadBottom == padBottom
            ) it else it.copy(
                touchpadLeft = padLeft,
                touchpadTop = padTop,
                touchpadRight = padRight,
                touchpadBottom = padBottom
            )
        }
    }

    fun cycleTouchpadHaptics() {
        viewModelScope.launch {
            preferences.setTouchpadHaptics(TouchpadHaptics.next(_state.value.touchpadHaptics))
        }
    }

    fun closePrivatePage() {
        if (!_state.value.privatePageActive && !_state.value.privateUnlocked) return
        lockPrivate()
        _state.update { it.copy(privatePageActive = false) }
    }

    fun selectSpace(space: SpaceKind?) {
        val next = space.takeUnless { it == SpaceKind.Private }
        _state.update { it.copy(spaceOverride = next) }
        viewModelScope.launch { preferences.setSpaceOverride(next?.name) }
    }

    fun togglePrivate(app: AppInfo) {
        viewModelScope.launch {
            val next = _state.value.privateApps.toMutableSet()
            if (app.key in next) next.remove(app.key) else next.add(app.key)
            preferences.setPrivateApps(next)
            lockPrivate()
            dismissAppActions()
        }
    }

    fun moveToPrivate(app: AppInfo) {
        if (app.key in _state.value.privateApps) return
        togglePrivate(app)
    }

    fun openSearch() {
        _state.update { it.copy(sheet = Sheet.Search, query = "", hits = emptyList()) }
    }

    fun openCapture(kind: CaptureKind = CaptureKind.Task) {
        _state.update { it.copy(sheet = Sheet.Capture, captureKind = kind) }
    }

    fun capture(kind: CaptureKind, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        when (kind) {
            CaptureKind.Task -> addTodo(trimmed)
            CaptureKind.Reminder -> {
                val id = addTodo(trimmed)
                _state.update { it.copy(duePickerTodoId = id) }
            }
            CaptureKind.Note -> addNote(trimmed)
        }
        closeSheet()
        if (kind != CaptureKind.Note) goToPage(2) else goToPage(0)
    }

    fun clearDuePicker() {
        _state.update { it.copy(duePickerTodoId = "") }
    }

    fun toggleFocusPin(app: AppInfo) {
        val space = _state.value.activeSpace.name
        val current = _state.value.focusPins[space].orEmpty()
        val nextForSpace = if (app.key in current) {
            current.filterNot { it == app.key }
        } else {
            (listOf(app.key) + current).distinct().take(4)
        }
        persistFocusPins(_state.value.focusPins + (space to nextForSpace))
        dismissAppActions()
    }

    private fun persistFocusPins(next: Map<String, List<String>>) {
        viewModelScope.launch { preferences.setFocusPins(next) }
        _state.update { it.copy(focusPins = next) }
    }

    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val space = _state.value.activeSpace.takeUnless { it == SpaceKind.Private } ?: SpaceKind.Home
        val item = CaptureNote(
            id = java.util.UUID.randomUUID().toString().take(8),
            text = trimmed,
            space = space,
            createdAt = System.currentTimeMillis()
        )
        persistNotes((listOf(item) + _state.value.notes).take(40))
    }

    fun deleteNote(id: String) {
        persistNotes(_state.value.notes.filterNot { it.id == id })
    }

    fun saveForLater(app: AppInfo? = null, text: String? = null) {
        val state = _state.value
        val label = text?.trim().orEmpty().ifBlank {
            app?.label
                ?: state.activeApp?.label
                ?: state.upNext?.title
                ?: state.inbox.firstOrNull { !it.isDigest }?.title
                ?: state.openTasks.firstOrNull()
        } ?: return
        val key = app?.key ?: state.activeApp?.key.orEmpty()
        val item = LaterItem(
            id = java.util.UUID.randomUUID().toString().take(8),
            text = label,
            kind = if (key.isNotBlank()) "app" else "pin",
            appKey = key,
            createdAt = System.currentTimeMillis()
        )
        persistLater((listOf(item) + state.later.filterNot { it.text.equals(label, true) }).take(24))
        dismissAppActions()
        goToPage(0)
    }

    fun openLater(item: LaterItem) {
        if (item.appKey.isNotBlank()) {
            visibleApp(item.appKey)?.let { launch(it) }
            return
        }
        addTodo(item.text)
        dismissLater(item.id)
        goToPage(2)
    }

    fun dismissLater(id: String) {
        persistLater(_state.value.later.filterNot { it.id == id })
    }

    fun startFocus(minutes: Int = 30, taskId: String? = null) {
        val task = taskId?.let { id -> _state.value.todos.find { it.id == id } }
            ?: _state.value.spaceTodos.filterNot { it.done }.firstOrNull()
        val until = System.currentTimeMillis() + minutes.coerceIn(5, 120) * 60 * 1000L
        val id = task?.id.orEmpty()
        _state.update { it.copy(focusUntil = until, focusTaskId = id) }
        viewModelScope.launch { preferences.setFocus(until, id) }
        goToPage(1)
    }

    fun endFocus() {
        _state.update { it.copy(focusUntil = 0L, focusTaskId = "") }
        viewModelScope.launch { preferences.setFocus(0L, "") }
    }

    private fun persistNotes(next: List<CaptureNote>) {
        viewModelScope.launch { preferences.setNotes(next) }
        _state.update { it.copy(notes = next) }
    }

    private fun persistLater(next: List<LaterItem>) {
        viewModelScope.launch { preferences.setLater(next) }
        _state.update { it.copy(later = next) }
    }

    private fun visibleApp(key: String): AppInfo? = _state.value.visibleApps.find { it.key == key }

    fun openDrawer() {
        _state.update { it.copy(sheet = Sheet.Drawer, query = "", hits = emptyList(), activeApp = null) }
    }

    fun setIconBusy(busy: Boolean) {
        _state.update { if (it.iconBusy == busy) it else it.copy(iconBusy = busy) }
    }

    fun dismissPopups() {
        _state.update { current ->
            current.copy(
                sheet = if (current.sheet == Sheet.Menu || current.sheet == Sheet.AppActions) {
                    Sheet.None
                } else {
                    current.sheet
                },
                activeApp = null
            )
        }
    }

    fun dismissAppActions() {
        _state.update { it.copy(activeApp = null) }
    }

    fun closeSheet() {
        closeVoiceAfterSpeak = false
        pendingVoiceIntent = null
        voiceAiJob?.cancel()
        if (_state.value.sheet == Sheet.Voice) onStopVoice?.invoke()
        _state.update { it.copy(sheet = Sheet.None, query = "", hits = emptyList(), activeApp = null, pickerKind = "", voiceHeard = "", voiceHint = "", voiceListening = false, voiceLevel = 0f, voiceCanRetry = false, activeFolderId = null, folderSeedAppKey = null) }
    }

    fun openMenu() {
        if (_state.value.sheet == Sheet.Drawer || _state.value.sheet == Sheet.Search || _state.value.sheet == Sheet.Voice) return
        _state.update { it.copy(sheet = Sheet.Menu) }
    }

    fun openSettings() {
        _state.update { it.copy(sheet = Sheet.Settings) }
    }

    fun onQueryChange(query: String) {
        _state.update { current ->
            current.copy(
                query = query,
                hits = SearchInterpreter.interpret(
                    query = query,
                    apps = current.visibleApps,
                    recents = current.recents,
                    aliases = current.aliases
                )
            )
        }
        viewModelScope.launch {
            val people = withContext(Dispatchers.IO) { PeopleActions.hits(contacts, query) }
            if (query != _state.value.query) return@launch
            if (people.isEmpty()) return@launch
            _state.update { current ->
                val rest = current.hits.filterNot { hit ->
                    hit is SearchHit.Action && hit.id in setOf("call", "whatsapp", "whatsapp_call")
                }
                current.copy(hits = people + rest)
            }
        }
    }

    fun onContactsPermission(granted: Boolean) {
        if (granted) contacts.invalidate()
        val pending = pendingPeople
        pendingPeople = null
        val query = _state.value.query
        onQueryChange(query)
        if (!granted || pending == null) return
        viewModelScope.launch {
            val people = withContext(Dispatchers.IO) { PeopleActions.hits(contacts, query) }
            val match = people.firstOrNull { it.phone.isNotBlank() } ?: return@launch
            runHit(match)
        }
    }

    fun selectCategory(category: AppCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun selectDrawerFilter(filter: DrawerFilter) {
        _state.update { it.copy(drawerFilter = filter) }
    }

    fun submitSearch() {
        val hits = _state.value.hits
        val strong = hits.filterIsInstance<SearchHit.App>().firstOrNull { it.score >= 860 }
        val first = strong ?: hits.firstOrNull { it !is SearchHit.Math }
        if (first != null) runHit(first)
    }

    fun runActionCard(card: ActionCard) {
        when {
            card.actionId == "maps_home" -> runHit(
                SearchHit.Action("maps_home", "Take me home", "Start navigation", "home")
            )
            card.app != null -> launch(card.app)
        }
    }

    fun showAppActions(app: AppInfo) {
        _state.update { it.copy(activeApp = app, sheet = Sheet.AppActions) }
    }

    fun showDockActions(app: DockApp) {
        val info = _state.value.apps.find { it.key == app.key }
            ?: AppInfo(app.label, app.packageName, app.activityName, 0L, AppCategory.Utilities)
        showAppActions(info)
    }

    fun openGesturePicker(kind: String) {
        _state.update { it.copy(sheet = Sheet.AppPicker, pickerKind = kind) }
    }

    fun setGestureAction(value: String) {
        val kind = _state.value.pickerKind
        viewModelScope.launch {
            when (kind) {
                "tap" -> preferences.setTapAction(value)
                "double" -> preferences.setDoubleAction(value)
                "triple" -> preferences.setTripleAction(value)
                "swipeUp" -> preferences.setSwipeUpAction(value)
                "swipeDown" -> preferences.setSwipeDownAction(value)
                "swipeLeft" -> preferences.setSwipeLeftAction(value)
                "swipeRight" -> preferences.setSwipeRightAction(value)
            }
            closeSheet()
        }
    }

    fun runBlankGesture(kind: String) {
        val spec = when (kind) {
            "tap" -> _state.value.tapAction.ifBlank { GestureAction.VOICE }
            "double" -> _state.value.doubleAction
            "triple" -> _state.value.tripleAction
            "long" -> GestureAction.PRIVATE
            "swipeUp" -> _state.value.swipeUpAction
            "swipeDown" -> _state.value.swipeDownAction
            "swipeLeft" -> _state.value.swipeLeftAction
            "swipeRight" -> _state.value.swipeRightAction
            else -> GestureAction.NONE
        }
        if (spec == GestureAction.VOICE) {
            openVoice()
            return
        }
        if (GestureAction.isUnassigned(spec)) {
            openGesturePicker(kind)
            return
        }
        when (spec) {
            GestureAction.NONE, GestureAction.OFF -> Unit
            GestureAction.LOCK -> lockScreen()
            GestureAction.DRAWER -> openDrawer()
            GestureAction.SEARCH -> openSearch()
            GestureAction.PRIVATE -> openPrivateSpace()
            GestureAction.NOTIFICATIONS -> expandNotifications()
            GestureAction.QUICK_SETTINGS -> expandQuickSettings()
            else -> {
                val key = GestureAction.appKey(spec)
                _state.value.apps.find { it.key == key }?.let { launch(it) }
            }
        }
    }

    fun openVoice(fromWake: Boolean = false) {
        skipVoiceGreeting = fromWake
        voiceMisses = 0
        pendingVoiceIntent = null
        _state.update {
            it.copy(
                sheet = Sheet.Voice,
                voiceHint = if (fromWake) "I'm listening." else "Hi, I'm Lumen.",
                voiceHeard = "",
                voiceListening = false,
                voiceLevel = 0f,
                voiceCanRetry = false
            )
        }
        onRequestMic?.invoke()
    }

    fun onWakeWord(remainder: String) {
        voiceMisses = 0
        pendingVoiceIntent = null
        if (remainder.isBlank()) {
            openVoice(fromWake = true)
            return
        }
        skipVoiceGreeting = true
        _state.update {
            it.copy(
                sheet = Sheet.Voice,
                voiceHint = "On it.",
                voiceHeard = remainder,
                voiceListening = false,
                voiceLevel = 0f,
                voiceCanRetry = false
            )
        }
        handleVoice(listOf(VoiceQuery.clean(remainder)).filter { it.isNotBlank() }.ifEmpty { return })
    }

    fun setHeyLumen(enabled: Boolean) {
        viewModelScope.launch { preferences.setHeyLumen(enabled) }
        _state.update { it.copy(heyLumen = enabled) }
        if (enabled) onRequestMicQuiet?.invoke()
    }

    fun setSmartVoice(enabled: Boolean) {
        viewModelScope.launch { preferences.setSmartVoice(enabled) }
        _state.update { it.copy(smartVoice = enabled) }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch { preferences.setGeminiApiKey(key) }
        _state.update { it.copy(geminiApiKey = key.trim()) }
    }

    fun requestDigitalAssistant() {
        onRequestAssistant?.invoke()
    }

    fun onMicPermission(granted: Boolean) {
        if (_state.value.sheet != Sheet.Voice) return
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
            onListen?.invoke()
            _state.update { it.copy(voiceHint = "I'm listening.") }
            return
        }
        onSpeak?.invoke("Hi, I'm Lumen.", true)
        _state.update { it.copy(voiceHint = "Hi, I'm Lumen.", voiceListening = false) }
    }

    fun onVoiceStarting() {
        _state.update {
            it.copy(
                voiceListening = false,
                voiceHint = "Give me a second.",
                voiceHeard = "",
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
        if (text.isBlank()) return
        _state.update { it.copy(voiceHeard = text, voiceListening = true, voiceCanRetry = false) }
    }

    fun onVoiceLevel(level: Float) {
        if (_state.value.sheet != Sheet.Voice) return
        _state.update { it.copy(voiceLevel = level.coerceIn(0f, 1f)) }
    }

    fun onVoiceHardError(message: String) {
        if (_state.value.sheet != Sheet.Voice) return
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
        if (_state.value.sheet != Sheet.Voice) return
        voiceMisses = 0
        _state.update {
            it.copy(
                voiceCanRetry = false,
                voiceHeard = "",
                voiceHint = "Give me a second.",
                voiceLevel = 0f
            )
        }
        onListen?.invoke()
    }

    fun onVoiceResult(text: String) {
        onVoiceResults(listOf(text))
    }

    fun submitVoiceText(text: String) {
        if (_state.value.sheet != Sheet.Voice || text.isBlank()) return
        onStopVoice?.invoke()
        onVoiceResults(listOf(text))
    }

    fun onVoiceResults(candidates: List<String>) {
        val cleaned = candidates.map { VoiceQuery.clean(it) }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) {
            onVoiceNoSpeech()
            return
        }
        if (cleaned.all { VoiceQuery.isOwnSpeech(it) }) {
            onListen?.invoke()
            return
        }
        voiceMisses = 0
        _state.update {
            it.copy(
                voiceHeard = candidates.first { it.isNotBlank() },
                voiceListening = false,
                voiceHint = "On it.",
                voiceCanRetry = false,
                voiceLevel = 0f
            )
        }
        handleVoice(cleaned)
    }

    fun onVoiceNoSpeech() {
        if (_state.value.sheet != Sheet.Voice) return
        voiceMisses += 1
        if (voiceMisses >= 8) {
            finishVoice("I'll be here if you need me.", continueTalking = false)
            return
        }
        _state.update {
            it.copy(voiceListening = false, voiceHint = "I'm listening.", voiceHeard = "", voiceLevel = 0f)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(80)
            if (_state.value.sheet == Sheet.Voice) onListen?.invoke()
        }
    }

    fun onVoiceFailed() {
        onVoiceNoSpeech()
    }

    fun onVoiceEngineStuck() {
        finishVoice("I couldn't hear you. Tap Lumen and try again.", continueTalking = false)
    }

    private fun handleVoice(candidates: List<String>) {
        pendingVoiceIntent?.let { pending ->
            when (val resolution = ClarificationResolver.resolve(
                candidates = candidates,
                pending = pending,
                appLabels = _state.value.visibleApps.map { it.label }
            )) {
                is ClarificationResolution.Execute -> {
                    pendingVoiceIntent = null
                    executeVoiceIntent(resolution.intent)
                }
                ClarificationResolution.Cancel -> {
                    pendingVoiceIntent = null
                    finishVoice("Okay, I won't do that.")
                }
                ClarificationResolution.End -> {
                    pendingVoiceIntent = null
                    finishVoice("Okay.", continueTalking = false)
                }
                is ClarificationResolution.Ask -> finishVoice(resolution.message)
                ClarificationResolution.NoMatch -> {
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

        val labels = _state.value.visibleApps.map { it.label }
        val intent = VoiceQueryRouter.route(candidates = candidates, appLabels = labels)
        decideVoice(intent, candidates, labels, allowAi = true)
    }

    private fun decideVoice(
        intent: VoiceIntent,
        candidates: List<String>,
        labels: List<String>,
        allowAi: Boolean
    ) {
        _state.update { it.copy(voiceHeard = intent.originalText.ifBlank { candidates.firstOrNull().orEmpty() }) }
        val key = _state.value.geminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        val consult = allowAi &&
            _state.value.smartVoice &&
            AiIntentFallback.needsFallback(intent)
        if (consult) {
            if (key.isBlank()) {
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
                    contacts = withContext(Dispatchers.IO) { contacts.names(40) },
                    alarms = upcomingAlarms().take(6).map {
                        "${formatHour(it.hour, it.minute)} ${it.whenLabel().lowercase()}"
                    },
                    history = voiceTurns.toList(),
                    now = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, h:mm a")),
                    transcripts = candidates
                )
                val outcome = withContext(Dispatchers.IO) {
                    AiIntentFallback.interpret(key, candidates, context)
                }
                if (!isActive || _state.value.sheet != Sheet.Voice) return@launch
                when (outcome) {
                    is AiIntentFallback.Outcome.Ok -> {
                        if (outcome.intent.action != VoiceAction.UNKNOWN) {
                            decideVoice(outcome.intent, candidates, labels, allowAi = false)
                            return@launch
                        }
                    }
                    is AiIntentFallback.Outcome.Failed -> {
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
                for (text in candidates) {
                    val people = withContext(Dispatchers.IO) { PeopleActions.hits(contacts, text) }
                    val person = people.firstOrNull { it.phone.isNotBlank() }
                    if (person != null) {
                        runHit(person)
                        onSpeak?.invoke(person.title, false)
                        return@launch
                    }
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
        viewModelScope.launch {
            for (text in candidates) {
                val people = withContext(Dispatchers.IO) { PeopleActions.hits(contacts, text) }
                val person = people.firstOrNull { it.phone.isNotBlank() }
                if (person != null) {
                    runHit(person)
                    onSpeak?.invoke(person.title, false)
                    return@launch
                }
            }
            finishVoice("I'm not sure what you meant. Say help to hear what I can do.")
        }
    }

    private fun askToClarify(intent: VoiceIntent) {
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
        finishVoice(clarifySpeech(pendingVoiceIntent!!))
    }

    private fun executeVoiceIntent(intent: VoiceIntent) {
        VoiceIntentMapper.toCommand(intent)?.let { command ->
            runLauncherCommand(command)
            return
        }
        when (intent.action) {
            VoiceAction.SET_ALARM -> {
                val hour = intent.hour ?: return
                val minute = intent.minute ?: 0
                val alarm = addAlarm(hour, minute, intent.daily)
                val spoken = buildString {
                    append("Alarm for ${formatHour(alarm.hour, alarm.minute)}")
                    if (intent.daily) append(", every day")
                    append(".")
                }
                if (!AlarmScheduler.canExact(getApplication())) promptExactAfterSpeak = true
                finishVoice(spoken)
            }
            VoiceAction.CANCEL_ALARM -> {
                val removed = cancelAlarms(intent.hour, intent.minute)
                finishVoice(
                    if (removed == 0) "You don't have an alarm to cancel."
                    else if (removed == 1) "Okay, alarm off."
                    else "Okay, I cancelled $removed alarms."
                )
            }
            VoiceAction.LIST_ALARMS -> {
                val upcoming = upcomingAlarms()
                val spoken = if (upcoming.isEmpty()) {
                    "No alarms yet."
                } else {
                    "You have " + upcoming.take(4).joinToString(". ") {
                        "${formatHour(it.hour, it.minute)} ${it.whenLabel().lowercase()}"
                    } + "."
                }
                finishVoice(spoken)
            }
            VoiceAction.SHOW_TASKS -> {
                goToPage(2)
                val open = _state.value.spaceTodos.filterNot { it.done }
                val spoken = if (open.isEmpty()) "Your ${ _state.value.activeSpace.title } list is empty."
                else "On ${_state.value.activeSpace.title}: " + open.take(6).joinToString(". ") { it.text }
                finishVoice(spoken)
            }
            VoiceAction.SAVE_NOTE -> {
                val item = intent.textValue ?: return
                addNote(item)
                goToPage(0)
                finishVoice("Saved a note. $item.")
            }
            VoiceAction.SAVE_LATER -> {
                saveForLater()
                finishVoice("Saved for later.")
            }
            VoiceAction.START_FOCUS -> {
                val mins = intent.intValue ?: 30
                startFocus(mins)
                val task = _state.value.focusTask?.text
                finishVoice(
                    if (task.isNullOrBlank()) "Focus for $mins minutes."
                    else "Focus for $mins minutes on $task."
                )
            }
            VoiceAction.END_FOCUS -> {
                endFocus()
                finishVoice("Focus is off.")
            }
            VoiceAction.DAILY_REVIEW -> {
                goToPage(0)
                finishVoice(_state.value.dailyReview.spoken.ifBlank { "You're clear." })
            }
            VoiceAction.SET_REMINDER -> {
                val item = intent.textValue ?: return
                val due = VoiceCommands.taskDue(intent.originalText)
                val repeat = VoiceCommands.taskRepeat(intent.originalText)
                val priority = VoiceCommands.taskPriority(intent.originalText)
                addTodo(item, due, repeat, priority)
                goToPage(2)
                val whenLabel = if (TodoTime.hasClock(due)) TodoTime.timeLabel(due) else "anytime"
                val repeatLabel = if (repeat.isNotBlank()) ", ${TodoRepeat.chip(repeat).lowercase()}" else ""
                val priorityLabel = if (priority) ", high priority" else ""
                finishVoice("Added to ${_state.value.activeSpace.title}, $whenLabel$repeatLabel$priorityLabel. $item.")
            }
            VoiceAction.COMPLETE_TASK -> {
                val asked = intent.textValue ?: "this"
                val match = matchTodo(asked, includeDone = false)
                if (match == null) {
                    goToPage(2)
                    finishVoice("I couldn't find that on your list.")
                } else {
                    toggleTodo(match.id, done = true)
                    goToPage(2)
                    finishVoice("Done. ${match.text}.")
                }
            }
            VoiceAction.DELETE_TASK -> {
                val asked = intent.textValue ?: return
                val match = matchTodo(asked)
                if (match == null) {
                    goToPage(2)
                    finishVoice("I couldn't find that on your list.")
                } else {
                    deleteTodo(match.id)
                    goToPage(2)
                    finishVoice("Removed ${match.text}.")
                }
            }
            VoiceAction.CALCULATE -> {
                finishVoice(intent.textValue ?: "I couldn't calculate that.")
            }
            VoiceAction.ANSWER -> {
                finishVoice(intent.textValue ?: "I'm not sure.")
            }
            VoiceAction.ASK_USER -> {
                finishVoice(intent.textValue ?: "What should I do?")
            }
            VoiceAction.CALL -> voiceCall(intent)
            VoiceAction.SEND_MESSAGE -> voiceMessage(intent)
            VoiceAction.SET_TIMER -> voiceTimer(intent)
            VoiceAction.NAVIGATE -> voiceNavigate(intent)
            VoiceAction.PLAY_MEDIA -> voicePlay(intent)
            VoiceAction.TOGGLE_TORCH -> {
                val on = torch.toggle()
                finishVoice(if (on) "Torch on." else "Torch off.")
            }
            VoiceAction.OPEN_APP -> {
                val name = intent.appName ?: return
                if (!openNamedApp(name)) finishVoice("I couldn't find $name.")
            }
            else -> finishVoice("I'm not sure what you meant. Say help to hear what I can do.")
        }
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

    private fun voiceCall(intent: VoiceIntent) {
        val who = intent.appName ?: intent.textValue
        if (who.isNullOrBlank()) {
            finishVoice("Who should I call?")
            return
        }
        if (!contacts.hasAccess()) {
            onRequestContacts?.invoke()
            finishVoice("I need Contacts to call someone.")
            return
        }
        val match = contacts.matches(who, 1).firstOrNull()
        if (match == null) {
            finishVoice("I couldn't find $who.")
            return
        }
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.phone}"))
        if (startIntent(dial)) finishVoice("Calling ${match.name}.", continueTalking = false)
        else finishVoice("I couldn't open the phone app.")
    }

    private fun voiceMessage(intent: VoiceIntent) {
        val who = intent.appName
        val body = intent.textValue.orEmpty()
        if (who.isNullOrBlank()) {
            finishVoice("Who should I message?")
            return
        }
        val phrase = buildString {
            append(if (intent.originalText.contains("whatsapp", true)) "whatsapp " else "text ")
            append(who)
            if (body.isNotBlank()) append(" $body")
        }
        viewModelScope.launch {
            val people = withContext(Dispatchers.IO) { PeopleActions.hits(contacts, phrase) }
            val person = people.firstOrNull { it.phone.isNotBlank() }
            if (person != null) {
                runHit(person)
                finishVoice(person.title, continueTalking = false)
            } else {
                val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                    putExtra("sms_body", body)
                }
                if (startIntent(sms)) finishVoice("Opening messages.")
                else finishVoice("I couldn't find $who.")
            }
        }
    }

    private fun voiceTimer(intent: VoiceIntent) {
        val minutes = intent.intValue
            ?: intent.hour?.let { it * 60 + (intent.minute ?: 0) }
            ?: intent.minute
        if (minutes == null || minutes <= 0) {
            finishVoice("How many minutes?")
            return
        }
        val whenAt = java.time.LocalDateTime.now().plusMinutes(minutes.toLong())
        addAlarm(whenAt.hour, whenAt.minute, daily = false, label = "Timer")
        if (!AlarmScheduler.canExact(getApplication())) promptExactAfterSpeak = true
        finishVoice("Timer for $minutes ${if (minutes == 1) "minute" else "minutes"}.")
    }

    private fun voiceNavigate(intent: VoiceIntent) {
        val place = intent.textValue ?: intent.appName
        if (place.isNullOrBlank()) {
            finishVoice("Where should I navigate?")
            return
        }
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(place)}"))
        val maps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))
        if (startIntent(nav) || startIntent(maps)) {
            finishVoice("Navigating to $place.", continueTalking = false)
        } else {
            finishVoice("I couldn't open maps.")
        }
    }

    private fun voicePlay(intent: VoiceIntent) {
        val query = intent.textValue ?: intent.appName
        if (!query.isNullOrBlank() && openNamedApp(query)) return
        val search = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query.orEmpty())}"))
        if (startIntent(search)) finishVoice("Playing $query.", continueTalking = false)
        else finishVoice("I couldn't find something to play.")
    }

    private fun finishVoice(spoken: String, continueTalking: Boolean = true) {
        val heard = _state.value.voiceHeard.ifBlank { spoken }
        voiceTurns.addLast("User: $heard")
        voiceTurns.addLast("Lumen: $spoken")
        while (voiceTurns.size > 8) voiceTurns.removeFirst()
        closeVoiceAfterSpeak = !continueTalking
        _state.update { it.copy(voiceHint = spoken, voiceListening = false, voiceCanRetry = false, voiceLevel = 0f) }
        onSpeak?.invoke(spoken, continueTalking)
    }

    fun onSpeakFinished() {
        val close = closeVoiceAfterSpeak
        closeVoiceAfterSpeak = false
        val promptExact = promptExactAfterSpeak
        promptExactAfterSpeak = false
        if (close && _state.value.sheet == Sheet.Voice) closeVoiceOnly()
        if (promptExact) AlarmScheduler.requestExactAccess(getApplication())
    }

    private fun closeVoiceOnly() {
        pendingVoiceIntent = null
        voiceAiJob?.cancel()
        onStopVoice?.invoke()
        _state.update {
            if (it.sheet == Sheet.Voice) {
                it.copy(sheet = Sheet.None, voiceHeard = "", voiceHint = "", voiceListening = false, voiceLevel = 0f, voiceCanRetry = false)
            } else {
                it.copy(voiceHeard = "", voiceHint = "", voiceListening = false, voiceLevel = 0f, voiceCanRetry = false)
            }
        }
    }

    private fun runLauncherCommand(command: LauncherCommand) {
        when (command) {
            LauncherCommand.OpenFlow -> {
                goToPage(0)
                finishVoice("Opening Flow.")
            }
            LauncherCommand.OpenHome -> {
                goToPage(1)
                finishVoice("Home.")
            }
            LauncherCommand.OpenDrawer -> {
                openDrawer()
                finishVoice("All apps.", continueTalking = false)
            }
            LauncherCommand.OpenSearch -> {
                openSearch()
                finishVoice("Search.", continueTalking = false)
            }
            LauncherCommand.OpenRecents -> {
                goToPage(1, recents = true)
                finishVoice("Recents.")
            }
            LauncherCommand.OpenSettings -> {
                openSettings()
                finishVoice("Lumen settings.", continueTalking = false)
            }
            LauncherCommand.OpenPersonalize -> {
                goToPage(0)
                _state.update { it.copy(personalizeFlow = true) }
                finishVoice("Personalize Flow.", continueTalking = false)
            }
            LauncherCommand.OpenPrivate -> {
                goToPage(1)
                openPrivateSpace()
                finishVoice("Locked Space needs your unlock.", continueTalking = false)
            }
            is LauncherCommand.SwitchSpace -> {
                selectSpace(command.space)
                goToPage(1)
                finishVoice(
                    if (command.space == null) "Back to the automatic layout."
                    else "Switched to ${command.space.title}."
                )
            }
            is LauncherCommand.IconSize -> {
                val current = _state.value.iconSizeDp
                val next = (command.sizeDp ?: (current + (command.delta ?: 0f))).coerceIn(44f, 72f)
                setIconSize(next)
                finishVoice("Icon size updated.")
            }
            is LauncherCommand.Labels -> {
                setShowLabels(command.show)
                finishVoice(if (command.show) "App labels are on." else "App labels are hidden.")
            }
            is LauncherCommand.Grid -> {
                viewModelScope.launch { preferences.setGridColumns(command.columns) }
                finishVoice("Home grid set to ${command.columns} columns.")
            }
            is LauncherCommand.DockCapacity -> {
                viewModelScope.launch { preferences.setDockCapacity(command.count) }
                finishVoice("Dock set to ${command.count} apps.")
            }
            is LauncherCommand.CreateFolder -> {
                val name = command.name?.trim().orEmpty()
                if (name.isBlank()) {
                    openFolderCreator()
                    finishVoice("Name the folder.", continueTalking = false)
                } else {
                    voiceCreateFolder(name, command.apps)
                }
            }
            is LauncherCommand.AddToFolder -> voiceAddToFolder(
                command.appName,
                command.folderName,
                command.extraApps
            )
            is LauncherCommand.Pin -> voiceHomeApp(command.name, HomeVoice.Pin)
            is LauncherCommand.Unpin -> voiceHomeApp(command.name, HomeVoice.Unpin)
            is LauncherCommand.Dock -> voiceHomeApp(command.name, HomeVoice.Dock)
            is LauncherCommand.Undock -> voiceHomeApp(command.name, HomeVoice.Undock)
            is LauncherCommand.HideApp -> voiceHomeApp(command.name, HomeVoice.Hide)
            is LauncherCommand.UnhideApp -> voiceHomeApp(command.name, HomeVoice.Unhide)
            is LauncherCommand.MoveToPrivate -> voiceMoveToPrivate(command.name, lock = true)
            is LauncherCommand.RemoveFromPrivate -> voiceMoveToPrivate(command.name, lock = false)
            is LauncherCommand.WhereApp -> voiceWhereApp(command.name)
            is LauncherCommand.LearnAlias -> voiceLearnAlias(command.alias, command.name)
            LauncherCommand.HideNews -> {
                setFlowModule(FlowModule.News, false)
                val current = _state.value.newsInterests
                if (current.isNotEmpty()) parkedNews = current
                setNewsInterests(emptySet())
                goToPage(0)
                finishVoice("News is hidden from Flow.")
            }
            LauncherCommand.ShowNews -> {
                setFlowModule(FlowModule.News, true)
                val next = parkedNews.ifEmpty { setOf(NewsTopic.Technology.name, NewsTopic.World.name) }
                setNewsInterests(next)
                goToPage(0)
                finishVoice("News is back on Flow.")
            }
            is LauncherCommand.SetFlowCard -> {
                setFlowModule(command.module, command.enabled)
                goToPage(0)
                finishVoice(
                    if (command.enabled) "${command.module.title} is on Flow."
                    else "${command.module.title} is off Flow."
                )
            }
            is LauncherCommand.MoveFlowCard -> {
                moveFlowModuleTo(command.module, command.before)
                goToPage(0)
                val above = command.before?.title
                finishVoice(
                    if (above != null) "Moved ${command.module.title} above $above."
                    else "Moved ${command.module.title} on Flow."
                )
            }
            LauncherCommand.NeedNow -> {
                val hints = _state.value.needNowHints
                if (hints.isEmpty()) {
                    finishVoice("I don't have a routine for this hour yet.")
                } else {
                    goToPage(0)
                    val task = _state.value.focusTask?.takeUnless { it.done }?.text
                    val names = hints.joinToString(", ") { it.title }
                    finishVoice(
                        if (task.isNullOrBlank()) "Need now: $names."
                        else "For $task, try $names."
                    )
                }
            }
            LauncherCommand.UsedYesterday -> speakYesterday()
            LauncherCommand.Weather -> {
                val weather = _state.value.weather
                if (weather == null) {
                    refreshWeather()
                    finishVoice("I don't have the weather yet.")
                } else {
                    finishVoice("${weather.temperatureLabel()} and ${weather.summary} in ${weather.place}.")
                }
            }
            LauncherCommand.NextEvent -> {
                val upNext = _state.value.upNext
                val event = _state.value.nextEvent
                goToPage(0)
                finishVoice(
                    when {
                        upNext != null -> upNext.spoken
                        event != null -> "${event.title} is next."
                        else -> "Nothing coming up on the calendar or in notifications right now."
                    }
                )
            }
            LauncherCommand.InboxDigest -> {
                val digest = _state.value.inboxDigest.ifBlank { InboxDigest.local(_state.value.inbox) }
                goToPage(0)
                finishVoice(
                    digest.ifBlank { "I don't see unread messages yet. Allow notification access in Flow if you want a digest." }
                )
            }
            LauncherCommand.Help -> finishVoice(
                "I can open apps, set alarms and timers, add things to your list, call or message people, answer questions, change Home, and move apps to the dock or Locked Space. What do you need?"
            )
            LauncherCommand.EndTalk -> finishVoice("Okay.", continueTalking = false)
            is LauncherCommand.OpenApp -> {
                if (!openNamedApp(command.name)) {
                    finishVoice("I couldn't find ${command.name}.")
                }
            }
            is LauncherCommand.Purpose -> showPurpose(command.query)
        }
    }

    private fun voiceCreateFolder(name: String, apps: List<String> = emptyList()) {
        val folders = _state.value.folders.toMutableList()
        if (folders.any { it.name.equals(name, ignoreCase = true) }) {
            if (apps.isEmpty()) {
                finishVoice("You already have a $name folder.")
                return
            }
        } else {
            folders += HomeFolder(
                id = java.util.UUID.randomUUID().toString(),
                name = name.replaceFirstChar { it.titlecase() },
                appKeys = emptyList()
            )
            _state.update { it.copy(folders = folders) }
            viewModelScope.launch { preferences.setFolders(folders) }
        }
        if (apps.isEmpty()) {
            goToPage(1)
            finishVoice("Created the $name folder. Say add an app to $name folder.")
            return
        }
        voiceAddToFolder(apps.first(), name, apps.drop(1))
    }

    private fun voiceAddToFolder(appName: String, folderName: String, extraApps: List<String> = emptyList()) {
        val names = listOf(appName) + extraApps
        val found = names.mapNotNull { findApp(it, minScore = 860, voice = true) }.distinctBy { it.key }
        if (found.isEmpty()) {
            finishVoice("I couldn't find $appName.")
            return
        }
        val folders = _state.value.folders.toMutableList()
        val index = folders.indexOfFirst { it.name.equals(folderName, ignoreCase = true) }
        val title = folderName.replaceFirstChar { it.titlecase() }
        if (index < 0) {
            folders += HomeFolder(
                id = java.util.UUID.randomUUID().toString(),
                name = title,
                appKeys = found.map { it.key }
            )
        } else {
            val folder = folders[index]
            folders[index] = folder.copy(appKeys = (folder.appKeys + found.map { it.key }).distinct())
        }
        _state.update { it.copy(folders = folders) }
        viewModelScope.launch { preferences.setFolders(folders) }
        goToPage(1)
        val labels = found.joinToString(", ") { it.label }
        finishVoice("Added $labels to ${folders.lastOrNull { it.name.equals(folderName, true) }?.name ?: title}.")
    }

    private fun voiceMoveToPrivate(name: String, lock: Boolean) {
        val app = findApp(name, includeHidden = true, includePrivate = true, minScore = 860, voice = true)
        if (app == null) {
            finishVoice("I couldn't find $name.")
            return
        }
        val locked = app.key in _state.value.privateApps
        if (lock && locked) {
            finishVoice("${app.label} is already in Locked Space.")
            return
        }
        if (!lock && !locked) {
            finishVoice("${app.label} isn't in Locked Space.")
            return
        }
        togglePrivate(app)
        finishVoice(
            if (lock) "Moved ${app.label} to Locked Space."
            else "${app.label} is out of Locked Space."
        )
    }

    private fun voiceWhereApp(name: String) {
        val app = findApp(name, includeHidden = true, includePrivate = true, minScore = 860, voice = true)
        if (app == null) {
            finishVoice("I couldn't find $name.")
            return
        }
        val state = _state.value
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
        val app = findApp(name, includeHidden = true, includePrivate = true, minScore = 860, voice = true)
        if (app == null) {
            finishVoice("I couldn't find $name.")
            return
        }
        val key = AliasHints.aliasKey(alias)
        if (key.length !in 2..32 || key.split(' ').size > 6) {
            finishVoice("I couldn't keep that nickname.")
            return
        }
        viewModelScope.launch { preferences.rememberAlias(key, app.key) }
        _state.update { it.copy(aliases = it.aliases + (key to app.key)) }
        finishVoice("Okay. $key opens ${app.label}.")
    }

    private enum class HomeVoice { Pin, Unpin, Dock, Undock, Hide, Unhide }

    fun openTodoList() {
        goToPage(2)
    }

    private fun goToPage(page: Int, recents: Boolean = false) {
        _state.update {
            it.copy(
                pagerPage = page,
                pagerPulse = it.pagerPulse + 1,
                recentsOpen = recents,
                personalizeFlow = if (page == 0) it.personalizeFlow else false,
                privatePageActive = if (page == 0) false else it.privatePageActive
            )
        }
    }

    fun setRecentsOpen(open: Boolean) {
        _state.update {
            when {
                !open -> it.copy(recentsOpen = false, socialCreateTool = null)
                it.recentsOpen == open -> it
                else -> it.copy(recentsOpen = true)
            }
        }
    }

    fun openSocialTool(tool: SocialCreateTool) {
        _state.update { it.copy(recentsOpen = false, socialCreateTool = tool) }
    }

    fun closeSocialTool() {
        _state.update { it.copy(socialCreateTool = null) }
    }

    fun closePersonalize() {
        _state.update { it.copy(personalizeFlow = false) }
    }

    fun toggleFlowModule(name: String) {
        val current = _state.value.flowEnabled.toMutableSet()
        if (name in current) current.remove(name) else current.add(name)
        viewModelScope.launch { preferences.setFlowEnabled(current) }
        _state.update { it.copy(flowEnabled = current) }
    }

    fun moveFlowModule(name: String, delta: Int) {
        val list = parseFlowOrder(_state.value.flowOrder).map { it.name }.toMutableList()
        val from = list.indexOf(name)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.removeAt(from)
        list.add(to, name)
        viewModelScope.launch { preferences.setFlowOrder(list) }
        _state.update { it.copy(flowOrder = list) }
    }

    private fun setFlowModule(module: FlowModule, enabled: Boolean) {
        val current = _state.value.flowEnabled.toMutableSet()
        if (enabled) current += module.name else current -= module.name
        viewModelScope.launch { preferences.setFlowEnabled(current) }
        _state.update { it.copy(flowEnabled = current) }
    }

    private fun moveFlowModuleTo(module: FlowModule, before: FlowModule?) {
        val list = parseFlowOrder(_state.value.flowOrder).map { it.name }.toMutableList()
        list.remove(module.name)
        val index = before?.let { list.indexOf(it.name).takeIf { i -> i >= 0 } } ?: 0
        list.add(index, module.name)
        viewModelScope.launch { preferences.setFlowOrder(list) }
        _state.update { it.copy(flowOrder = list) }
    }

    private fun voiceHomeApp(name: String, action: HomeVoice) {
        val app = findApp(
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
                val current = _state.value.favorites.toMutableList()
                if (app.key in current) {
                    finishVoice("${app.label} is already on Home.")
                } else {
                    current += app.key
                    viewModelScope.launch { preferences.setFavorites(current) }
                    goToPage(1)
                    finishVoice("Pinned ${app.label} to Home.")
                }
            }
            HomeVoice.Unpin -> {
                val current = _state.value.favorites.toMutableList()
                if (app.key !in current) {
                    finishVoice("${app.label} isn't on Home.")
                } else {
                    current.remove(app.key)
                    viewModelScope.launch { preferences.setFavorites(current) }
                    finishVoice("Removed ${app.label} from Home.")
                }
            }
            HomeVoice.Dock -> {
                val current = (_state.value.dockKeys ?: _state.value.dock.map { it.key }).toMutableList()
                when {
                    app.key in current -> finishVoice("${app.label} is already in the dock.")
                    current.size >= _state.value.dockCapacity -> finishVoice("The dock is full.")
                    else -> {
                        current += app.key
                        viewModelScope.launch { preferences.setDockKeys(current) }
                        goToPage(1)
                        finishVoice("Added ${app.label} to the dock.")
                    }
                }
            }
            HomeVoice.Undock -> {
                val current = (_state.value.dockKeys ?: _state.value.dock.map { it.key }).toMutableList()
                if (current.none { it == app.key }) {
                    finishVoice("${app.label} isn't in the dock.")
                } else {
                    current.removeAll { it == app.key }
                    viewModelScope.launch { preferences.setDockKeys(current) }
                    finishVoice("Removed ${app.label} from the dock.")
                }
            }
            HomeVoice.Hide -> {
                viewModelScope.launch { preferences.setHidden(_state.value.hidden + app.packageName) }
                finishVoice("Hidden ${app.label}.")
            }
            HomeVoice.Unhide -> {
                viewModelScope.launch { preferences.setHidden(_state.value.hidden - app.packageName) }
                finishVoice("${app.label} is visible again.")
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
        val app = findApp(name, minScore = 860, voice = true) ?: return false
        launch(app)
        onSpeak?.invoke("Opening ${app.label}.", false)
        return true
    }

    private fun findApp(
        name: String,
        includeHidden: Boolean = false,
        includePrivate: Boolean = false,
        minScore: Int = 500,
        voice: Boolean = false
    ): AppInfo? {
        val state = _state.value
        val pool = when {
            includeHidden && includePrivate -> state.apps
            includePrivate -> (state.visibleApps + state.privateAppList).distinctBy { it.key }
            includeHidden -> state.apps.filter { it.key !in state.privateApps }
            else -> state.visibleApps
        }
        val aliases = _state.value.aliases
        val cleaned = FuzzySearch.normalize(name)
            .removePrefix("the ")
            .replace(Regex("\\s+app$"), "")
            .replace("whats app", "whatsapp")
            .replace("what sap", "whatsapp")
            .trim()
        if (cleaned.isBlank()) return null

        fun match(query: String): AppInfo? {
            if (query.isBlank()) return null
            val compact = query.replace(" ", "")
            pool.find { FuzzySearch.normalize(it.label).replace(" ", "") == compact }?.let { return it }
            val aliasKey = aliases[query] ?: aliases[compact]
            aliasKey?.let { key -> pool.find { it.key == key } }?.let { return it }
            if (voice) {
                val label = VoiceMatch.best(query, pool.map { it.label }, minScore) ?: return null
                return pool.firstOrNull { FuzzySearch.normalize(it.label) == FuzzySearch.normalize(label) }
            }
            if (compact.length >= 4) {
                pool.find { it.packageName.contains(compact, ignoreCase = true) }?.let { return it }
            }
            val scored = pool.map { app -> app to FuzzySearch.score(query, app.label) }
            return scored.maxByOrNull { it.second }?.takeIf { it.second >= minScore }?.first
        }

        match(cleaned)?.let { return it }
        if (!voice) {
            val words = cleaned.split(' ').filter { it.length >= 3 }
            if (words.size >= 2) {
                match(words.last())?.let { return it }
                words.maxByOrNull { it.length }?.let { match(it) }?.let { return it }
            }
        }
        return null
    }

    private fun speakYesterday() {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val apps = _state.value.launchTimes.entries
            .filter { it.value in start until end }
            .sortedByDescending { it.value }
            .mapNotNull { entry -> _state.value.visibleApps.find { it.key == entry.key } }
            .distinctBy { it.key }
        if (apps.isEmpty()) {
            finishVoice("I don't remember an app from yesterday yet.")
            return
        }
        val first = apps.first()
        finishVoice("Yesterday you used ${apps.take(3).joinToString(", ") { it.label }}. Opening ${first.label}.", continueTalking = false)
        launch(first)
    }

    private fun showPurpose(query: String) {
        val intent = IntentIndex.match(query).firstOrNull()
        val apps = if (intent != null) {
            IntentIndex.appsFor(intent, _state.value.visibleApps, 8)
        } else {
            _state.value.visibleApps.filter { it.category == AppCategory.Finance }.take(8)
        }
        if (apps.isEmpty()) {
            finishVoice("I don't see matching apps on this phone.")
            return
        }
        val title = intent?.title ?: "Finance"
        _state.update {
            it.copy(
                sheet = Sheet.Search,
                query = title,
                hits = listOf(
                    SearchHit.IntentGroup(title, "Apps on this phone", apps)
                ) + apps.map { app -> SearchHit.App(app, 900) }
            )
        }
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

    fun reorderDock(fromKey: String, toKey: String) {
        if (fromKey == toKey || fromKey.isBlank() || toKey.isBlank()) return
        viewModelScope.launch {
            val list = liveDockKeys().toMutableList()
            val from = list.indexOfFirst { it == fromKey }
            val to = list.indexOfFirst { it == toKey }
            if (from < 0 || to < 0) return@launch
            list.removeAt(from)
            list.add(to, fromKey)
            preferences.setDockKeys(list, _state.value.activeSpace.name)
        }
    }

    fun placeOnDock(app: AppInfo, slotIndex: Int) {
        if (app.key.isBlank()) return
        viewModelScope.launch {
            val capacity = _state.value.dockCapacity.coerceIn(3, 6)
            val current = liveDockKeys().toMutableList()
            val target = slotIndex.coerceIn(0, capacity - 1)
            val occupant = current.getOrNull(target)
            current.removeAll { it == app.key }
            val at = occupant?.let { key -> current.indexOf(key).takeIf { it >= 0 } } ?: target.coerceAtMost(current.size)
            if (at < current.size) current[at] = app.key
            else current.add(app.key)
            preferences.setDockKeys(current.take(capacity), _state.value.activeSpace.name)
        }
    }

    fun toggleDock(app: AppInfo) {
        viewModelScope.launch {
            val current = liveDockKeys().toMutableList()
            if (app.key in current) {
                current.remove(app.key)
            } else if (current.size < _state.value.dockCapacity) {
                current.add(app.key)
            } else {
                closeSheet()
                return@launch
            }
            preferences.setDockKeys(current, _state.value.activeSpace.name)
            dismissAppActions()
        }
    }

    fun removeFromDock(app: DockApp) {
        viewModelScope.launch {
            val current = liveDockKeys().toMutableList()
            current.removeAll { it == app.key || it == app.id }
            preferences.setDockKeys(current, _state.value.activeSpace.name)
        }
    }

    fun launch(app: AppInfo) {
        if (app.key in _state.value.privateApps && !_state.value.privateUnlocked) {
            onAuthenticate?.invoke(
                {
                    _state.update { it.copy(privateUnlocked = true, privatePrompting = false) }
                    launchNow(app)
                },
                { _state.update { it.copy(privatePrompting = false) } }
            )
            return
        }
        launchNow(app)
    }

    private fun launchNow(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startIntent(intent)
        viewModelScope.launch {
            preferences.recordLaunch(app.key)
            val spoken = _state.value.query.trim().ifBlank { _state.value.voiceHeard.trim() }
            if (spoken.isNotEmpty()) preferences.noteSpokenOpen(spoken, app.key, app.label)
        }
        closeSheet()
    }

    fun launchDock(app: DockApp) {
        val pm = getApplication<Application>().packageManager
        val tries = buildList {
            when (app.id) {
                "messages" -> {
                    add(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
                    add(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
                    add(Intent(Intent.ACTION_VIEW, Uri.parse("sms:")))
                }
                "camera" -> {
                    add(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                    add(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
                }
                "phone" -> add(Intent(Intent.ACTION_DIAL))
                "browser" -> {
                    add(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER))
                    add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                }
            }
            add(Intent(app.fallbackIntent))
            if (app.packageName.isNotBlank()) {
                pm.getLaunchIntentForPackage(app.packageName)?.let { add(it) }
            }
        }
        for (intent in tries) {
            if (startIntent(intent)) {
                if (app.key.isNotBlank()) {
                    viewModelScope.launch { preferences.recordLaunch(app.key) }
                }
                return
            }
        }
        if (app.packageName.isNotBlank() && app.activityName.isNotBlank()) {
            launchNow(
                AppInfo(app.label, app.packageName, app.activityName, 0L, AppCategory.Utilities)
            )
        }
    }

    private fun startIntent(intent: Intent): Boolean {
        val ready = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (onStartActivity?.invoke(ready) == true) return true
        return runCatching { getApplication<Application>().startActivity(ready) }.isSuccess
    }

    fun runHit(hit: SearchHit) {
        val context = getApplication<Application>()
        when (hit) {
            is SearchHit.App -> launch(hit.app)
            is SearchHit.Math -> Unit
            is SearchHit.Web -> {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", hit.query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { context.startActivity(intent) }.isFailure) {
                    val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(hit.query)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(view) }
                }
                closeSheet()
            }
            is SearchHit.IntentGroup -> hit.apps.firstOrNull()?.let { launch(it) }
            is SearchHit.Discovery -> hit.apps.firstOrNull()?.let { launch(it) }
            is SearchHit.Action -> {
                when (hit.id) {
                    "wifi" -> start(Settings.ACTION_WIFI_SETTINGS)
                    "bluetooth" -> start(Settings.ACTION_BLUETOOTH_SETTINGS)
                    "airplane" -> start(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    "settings" -> start(Settings.ACTION_SETTINGS)
                    "camera" -> _state.value.dock.firstOrNull { it.id == "camera" }?.let { launchDock(it) }
                    "phone" -> _state.value.dock.firstOrNull { it.id == "phone" }?.let { launchDock(it) }
                    "messages" -> _state.value.dock.firstOrNull { it.id == "messages" }?.let { launchDock(it) }
                    "flashlight" -> {
                        torch.toggle()
                        closeSheet()
                    }
                    "maps_home" -> {
                        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Home"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (runCatching { context.startActivity(nav) }.isFailure) {
                            val maps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Home"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(maps) }
                        }
                        closeSheet()
                    }
                    "photos" -> {
                        val photos = _state.value.visibleApps.firstOrNull {
                            val hay = "${it.label} ${it.packageName}".lowercase()
                            hay.contains("photo") || hay.contains("gallery")
                        }
                        if (photos != null) launch(photos) else start(Intent.ACTION_VIEW)
                    }
                    "work_mode" -> {
                        selectSpace(SpaceKind.Work)
                        closeSheet()
                    }
                    "whatsapp", "whatsapp_call" -> {
                        if (hit.phone.isBlank()) {
                            pendingPeople = hit
                            onRequestContacts?.invoke()
                            return
                        }
                        openWhatsApp(hit.phone, hit.message)
                        closeSheet()
                    }
                    "open" -> {
                        val name = hit.title.removePrefix("Open ").trim()
                        val match = _state.value.visibleApps
                            .maxByOrNull { FuzzySearch.score(name, it.label) }
                        if (match != null && FuzzySearch.score(name, match.label) >= 400) {
                            launch(match)
                        }
                    }
                    "call" -> {
                        if (hit.phone.isBlank() && hit.query.filter { it.isDigit() || it == '+' }.isBlank()) {
                            pendingPeople = hit
                            onRequestContacts?.invoke()
                            return
                        }
                        val digits = hit.phone.ifBlank { hit.query.filter { it.isDigit() || it == '+' } }
                        if (digits.isNotBlank()) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        } else {
                            start(Intent.ACTION_DIAL)
                        }
                        closeSheet()
                    }
                }
            }
        }
    }

    fun reorderHome(fromKey: String, toKey: String) {
        if (fromKey == toKey || fromKey.isBlank() || toKey.isBlank()) return
        viewModelScope.launch {
            val current = _state.value
            val list = (if (current.favorites.isNotEmpty()) current.favorites else current.homeApps.map { it.key })
                .toMutableList()
            val from = list.indexOf(fromKey)
            val to = list.indexOf(toKey)
            if (from < 0 || to < 0) return@launch
            list.removeAt(from)
            list.add(to, fromKey)
            preferences.setFavorites(list)
        }
    }

    fun toggleFavorite(app: AppInfo) {
        viewModelScope.launch {
            val current = _state.value.favorites.toMutableList()
            if (app.key in current) current.remove(app.key) else current.add(app.key)
            preferences.setFavorites(current)
            dismissAppActions()
        }
    }

    fun toggleHidden(app: AppInfo) {
        viewModelScope.launch {
            val current = _state.value.hidden.toMutableSet()
            if (app.packageName in current) current.remove(app.packageName) else current.add(app.packageName)
            preferences.setHidden(current)
            dismissAppActions()
        }
    }

    fun unhide(packageName: String) {
        viewModelScope.launch {
            preferences.setHidden(_state.value.hidden - packageName)
        }
    }

    fun setIconSize(sizeDp: Float) {
        viewModelScope.launch { preferences.setIconSize(sizeDp) }
    }

    fun cycleGridColumns() {
        val next = if (_state.value.gridColumns >= 6) 3 else _state.value.gridColumns + 1
        viewModelScope.launch { preferences.setGridColumns(next) }
    }

    fun cycleDrawerColumns() {
        val next = if (_state.value.drawerColumns >= 6) 3 else _state.value.drawerColumns + 1
        viewModelScope.launch { preferences.setDrawerColumns(next) }
    }

    fun cycleDockCapacity() {
        val next = if (_state.value.dockCapacity >= 6) 3 else _state.value.dockCapacity + 1
        viewModelScope.launch { preferences.setDockCapacity(next) }
    }

    fun setShowLabels(show: Boolean) {
        viewModelScope.launch { preferences.setShowLabels(show) }
    }

    fun cycleNotificationBadges() {
        val next = _state.value.notificationBadges.next()
        _state.update { it.copy(notificationBadges = next) }
        viewModelScope.launch { preferences.setNotificationBadges(next) }
    }

    fun cycleTheme() {
        val next = LumenThemeMode.next(_state.value.theme)
        _state.update { it.copy(theme = next) }
        viewModelScope.launch { preferences.setTheme(next.name) }
    }

    fun cycleIconSkin() {
        val next = IconSkin.next(_state.value.iconSkin)
        _state.update { it.copy(iconSkin = next) }
        viewModelScope.launch { preferences.setIconSkin(next.name) }
    }

    fun cycleGlassDepth() {
        val next = GlassDepth.next(_state.value.glassDepth)
        _state.update { it.copy(glassDepth = next) }
        viewModelScope.launch { preferences.setGlassDepth(next.name) }
    }

    fun setSmartCluster(enabled: Boolean) {
        _state.update { it.copy(smartCluster = enabled) }
        viewModelScope.launch { preferences.setSmartCluster(enabled) }
    }

    fun openFolderCreator(seedAppKey: String? = null) {
        _state.update {
            it.copy(sheet = Sheet.FolderEditor, activeFolderId = null, folderSeedAppKey = seedAppKey, activeApp = null)
        }
    }

    fun openFolder(folder: HomeFolder) {
        _state.update { it.copy(sheet = Sheet.Folder, activeFolderId = folder.id, activeApp = null) }
    }

    fun editFolder(folder: HomeFolder) {
        _state.update { it.copy(sheet = Sheet.FolderEditor, activeFolderId = folder.id, folderSeedAppKey = null, activeApp = null) }
    }

    fun saveFolder(name: String, appKeys: Set<String>) {
        val trimmed = name.trim().ifBlank { "Folder" }
        val keys = appKeys.filter { it.isNotBlank() }.distinct()
        val folders = _state.value.folders.toMutableList()
        val id = _state.value.activeFolderId
        if (id != null) {
            val index = folders.indexOfFirst { it.id == id }
            if (index >= 0) folders[index] = folders[index].copy(name = trimmed, appKeys = keys)
        } else {
            folders += HomeFolder(id = java.util.UUID.randomUUID().toString(), name = trimmed, appKeys = keys)
        }
        _state.update { it.copy(folders = folders, activeFolderId = null, folderSeedAppKey = null, sheet = Sheet.None) }
        viewModelScope.launch { preferences.setFolders(folders) }
    }

    fun deleteActiveFolder() {
        val id = _state.value.activeFolderId ?: return
        val next = _state.value.folders.filterNot { it.id == id }
        _state.update { it.copy(folders = next, activeFolderId = null, folderSeedAppKey = null, sheet = Sheet.None) }
        viewModelScope.launch { preferences.setFolders(next) }
    }

    fun expandNotifications() {
        StatusBarController.expandNotifications(getApplication())
    }

    fun expandQuickSettings() {
        StatusBarController.expandQuickSettings(getApplication())
    }

    fun openWallpaperPicker() {
        _state.update { it.copy(wallpaperPickPulse = it.wallpaperPickPulse + 1) }
        closeSheet()
    }

    fun openSystemWallpaperPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
        closeSheet()
    }

    fun setSpaceWallpaper(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val space = _state.value.activeSpace.takeUnless { it == SpaceKind.Private } ?: SpaceKind.Home
            val path = SpaceWallpaper.copy(getApplication(), uri, space) ?: return@launch
            persistSpaceWallpapers(_state.value.spaceWallpapers + (space.name to path))
        }
    }

    fun clearSpaceWallpaper() {
        val space = _state.value.activeSpace.takeUnless { it == SpaceKind.Private } ?: SpaceKind.Home
        SpaceWallpaper.delete(getApplication(), space)
        persistSpaceWallpapers(_state.value.spaceWallpapers - space.name)
    }

    private fun persistSpaceWallpapers(next: Map<String, String>) {
        viewModelScope.launch { preferences.setSpaceWallpapers(next) }
        _state.update { it.copy(spaceWallpapers = next) }
    }

    fun openPackageInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
        dismissAppActions()
    }

    fun openAppInfo(app: AppInfo) {
        openPackageInfo(app.packageName)
    }

    fun uninstallApp(app: AppInfo) {
        val pkg = app.packageName
        if (pkg.isBlank() || pkg == getApplication<Application>().packageName) return
        val uris = listOf(Uri.fromParts("package", pkg, null), Uri.parse("package:$pkg"))
        val actions = listOf(Intent.ACTION_DELETE, "android.intent.action.UNINSTALL_PACKAGE")
        for (action in actions) {
            for (uri in uris) {
                if (startIntent(Intent(action).setData(uri))) {
                    dismissAppActions()
                    return
                }
            }
        }
        openPackageInfo(pkg)
    }

    fun lockScreen() {
        val context = getApplication<Application>()
        val admin = ComponentName(context, LockAdminReceiver::class.java)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allow Lumen to lock the screen on double-tap.")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    private fun openWhatsApp(phone: String, message: String) {
        val context = getApplication<Application>()
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) {
            val wa = _state.value.visibleApps.firstOrNull { it.packageName.contains("whatsapp", true) }
            if (wa != null) launch(wa)
            return
        }
        val path = if (message.isBlank()) "https://wa.me/$digits" else "https://wa.me/$digits?text=${Uri.encode(message)}"
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(path)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val targeted = Intent(view).apply {
            setPackage(
                when {
                    installed("com.whatsapp") -> "com.whatsapp"
                    installed("com.whatsapp.w4b") -> "com.whatsapp.w4b"
                    else -> null
                }
            )
        }
        if (runCatching { context.startActivity(targeted) }.isFailure) {
            runCatching { context.startActivity(view) }
        }
    }

    private fun installed(packageName: String): Boolean {
        return runCatching {
            getApplication<Application>().packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun start(action: String) {
        startIntent(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        closeSheet()
    }

    private fun isDefaultHome(): Boolean {
        return HomeRole.isHeld(getApplication())
    }
}

enum class Sheet { None, Search, Drawer, Menu, Settings, AppActions, AppPicker, Voice, Folder, FolderEditor, Capture }

enum class DrawerFilter { Az, MostUsed, Categories }

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val dock: List<DockApp> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val launchTimes: Map<String, Long> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val iconSizeDp: Float = 60f,
    val gridColumns: Int = 4,
    val drawerColumns: Int = 4,
    val dockCapacity: Int = 4,
    val showLabels: Boolean = true,
    val iconSkin: IconSkin = IconSkin.MatchSpace,
    val theme: LumenThemeMode = LumenThemeMode.Violet,
    val glassDepth: GlassDepth = GlassDepth.Balanced,
    val smartCluster: Boolean = true,
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val selectedCategory: AppCategory = AppCategory.All,
    val drawerFilter: DrawerFilter = DrawerFilter.Az,
    val sheet: Sheet = Sheet.None,
    val activeApp: AppInfo? = null,
    val isDefaultHome: Boolean = false,
    val weather: WeatherSnapshot? = null,
    val aliases: Map<String, String> = emptyMap(),
    val spaceOverride: SpaceKind? = null,
    val competingLaunchers: List<com.lumen.launcher.util.CompetingLauncher> = emptyList(),
    val privateApps: Set<String> = emptySet(),
    val privateUnlocked: Boolean = false,
    val privatePageActive: Boolean = false,
    val privatePrompting: Boolean = false,
    val homePulse: Int = 0,
    val news: List<NewsItem> = emptyList(),
    val newsLoading: Boolean = false,
    val newsInterests: Set<String> = emptySet(),
    val dockKeys: List<String>? = null,
    val tapAction: String = GestureAction.VOICE,
    val doubleAction: String = "",
    val tripleAction: String = "",
    val longAction: String = GestureAction.PRIVATE,
    val swipeUpAction: String = "",
    val swipeDownAction: String = "",
    val swipeLeftAction: String = "",
    val swipeRightAction: String = "",
    val pickerKind: String = "",
    val iconBusy: Boolean = false,
    val touchpadHaptics: String = TouchpadHaptics.STANDARD,
    val touchpadLeft: Float = 0f,
    val touchpadTop: Float = 0f,
    val touchpadRight: Float = 0f,
    val touchpadBottom: Float = 0f,
    val voiceHint: String = "",
    val voiceHeard: String = "",
    val voiceListening: Boolean = false,
    val voiceLevel: Float = 0f,
    val voiceCanRetry: Boolean = false,
    val tasks: List<String> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val heyLumen: Boolean = false,
    val smartVoice: Boolean = true,
    val geminiApiKey: String = "",
    val flowEnabled: Set<String> = defaultFlowEnabled(),
    val flowOrder: List<String> = defaultFlowNames(),
    val folders: List<HomeFolder> = emptyList(),
    val activeFolderId: String? = null,
    val folderSeedAppKey: String? = null,
    val nextEvent: CalendarEvent? = null,
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val deviceCalendars: List<DeviceCalendar> = emptyList(),
    val phoneAccounts: List<PhoneAccount> = emptyList(),
    val workCalendars: Set<String> = emptySet(),
    val personalCalendars: Set<String> = emptySet(),
    val alarms: List<LumenAlarm> = emptyList(),
    val alarmTone: String = AlarmTones.AURA,
    val inbox: List<InboxItem> = emptyList(),
    val inboxAccess: Boolean = false,
    val calendarAccess: Boolean = false,
    val missedCalls: List<MissedCall> = emptyList(),
    val callLogAccess: Boolean = false,
    val storedWhatsAppMissed: List<MissedCall> = emptyList(),
    val inferredSpace: SpaceKind = SpaceKind.Home,
    val launchHours: List<SpaceSense.LaunchHour> = emptyList(),
    val spaceDocks: Map<String, List<String>> = emptyMap(),
    val upNext: UpNext? = null,
    val inboxDigest: String = "",
    val firstName: String? = null,
    val pagerPage: Int = 1,
    val pagerPulse: Int = 0,
    val recentsOpen: Boolean = false,
    val socialCreations: List<CreationItem> = emptyList(),
    val socialCreateTool: SocialCreateTool? = null,
    val personalizeFlow: Boolean = false,
    val notes: List<CaptureNote> = emptyList(),
    val later: List<LaterItem> = emptyList(),
    val focusUntil: Long = 0L,
    val focusTaskId: String = "",
    val focusPins: Map<String, List<String>> = emptyMap(),
    val captureKind: CaptureKind = CaptureKind.Task,
    val duePickerTodoId: String = "",
    val spaceWallpapers: Map<String, String> = emptyMap(),
    val wallpaperPickPulse: Int = 0,
    val notificationBadges: NotificationBadgeMode = NotificationBadgeMode.Number
) {
    val selectedNewsTopics: List<NewsTopic>
        get() = NewsTopic.entries.filter { it.name in newsInterests }

    val flowModules: List<FlowModule>
        get() = parseFlowOrder(flowOrder).filter { module ->
            module.name in flowEnabled ||
                module == FlowModule.Review ||
                module == FlowModule.Later ||
                module == FlowModule.Notes
        }

    val activeFolder: HomeFolder?
        get() = folders.find { it.id == activeFolderId }

    val folderAppKeys: Set<String>
        get() = folders.flatMap { it.appKeys }.toSet()

    val upcomingAlarms: List<LumenAlarm>
        get() = alarms.filter { it.enabled }.sortedBy { it.nextTriggerMs() }

    val visibleApps: List<AppInfo>
        get() = apps.filterNot { it.packageName in hidden }.filter { it.key !in privateApps }

    val favoriteApps: List<AppInfo>
        get() = favorites.mapNotNull { key -> visibleApps.find { it.key == key } }

    val recentApps: List<AppInfo>
        get() = recents.mapNotNull { key -> visibleApps.find { it.key == key } }.take(12)

    val privateAppList: List<AppInfo>
        get() = privateApps.mapNotNull { key -> apps.find { it.key == key } }

    val activeSpace: SpaceKind
        get() = spaceOverride ?: inferredSpace

    val spaceTodos: List<TodoItem>
        get() = todos.filter { it.space.name == activeSpace.name }
            .sortedWith(compareByDescending<TodoItem> { it.priority }.thenBy { it.dueAt ?: Long.MAX_VALUE })

    val openTasks: List<String>
        get() = spaceTodos.filterNot { it.done }.map { it.text }

    val spaceNotes: List<CaptureNote>
        get() = notes.filter { it.space.name == activeSpace.name }

    val spaceWallpaper: String?
        get() = spaceWallpapers[activeSpace.name]?.takeIf { java.io.File(it).exists() }

    val clusterApps: List<AppInfo>
        get() = SmartClusterResolver.apps(
            space = activeSpace,
            focusing = focusing,
            focusApps = focusPicks.map { it.app },
            visible = visibleApps,
            recents = recents,
            needNow = needNowHints.mapNotNull { it.app }
        )

    val focusTask: TodoItem?
        get() = spaceTodos.find { it.id == focusTaskId } ?: spaceTodos.filterNot { it.done }.firstOrNull()

    val focusing: Boolean
        get() = focusUntil > System.currentTimeMillis()

    val dailyReview: DailyReview
        get() = DailyReviewResolver.resolve(spaceTodos, upcomingEvents)

    val needNowHints: List<NeedNowHint>
        get() = NeedNowResolver.hints(focusTask?.takeUnless { it.done } ?: spaceTodos.filterNot { it.done }.firstOrNull(), visibleApps, recents, activeSpace)

    val spaceAutomatic: Boolean
        get() = spaceOverride == null

    val likelyNext: List<AppInfo>
        get() = needNowHints.mapNotNull { it.app }.ifEmpty { Routine.likelyNext(visibleApps, recents, activeSpace) }

    val actionCards: List<ActionCard>
        get() = Routine.actionCards(visibleApps, recents, activeSpace)

    val focusPicks: List<FocusPick>
        get() = if (!focusing) {
            emptyList()
        } else {
            FocusAppsResolver.picks(
                task = focusTask?.takeUnless { it.done },
                apps = visibleApps,
                recents = recents,
                space = activeSpace,
                focusPins = focusPins[activeSpace.name].orEmpty(),
                favorites = favorites
            )
        }

    val homeApps: List<AppInfo>
        get() {
            if (focusing) return focusPicks.map { it.app }
            val all = favoriteApps.ifEmpty { (likelyNext + recentApps + visibleApps).distinctBy { it.key } }
            return all.take(24)
        }

    fun focusReason(app: AppInfo): String? =
        focusPicks.find { it.app.key == app.key }?.reason(activeSpace)

    val categorizedApps: List<AppInfo>
        get() = when (selectedCategory) {
            AppCategory.All -> visibleApps
            AppCategory.Recent -> recentApps
            else -> visibleApps.filter { it.category == selectedCategory }
        }

    val usedCategories: List<AppCategory>
        get() {
            val present = visibleApps.map { it.category }.toSet()
            return listOf(AppCategory.All, AppCategory.Recent) +
                AppCategory.entries.filter { it != AppCategory.All && it != AppCategory.Recent && it in present }
        }

    val drawerSections: List<Pair<String, List<AppInfo>>>
        get() = when (drawerFilter) {
            DrawerFilter.Az -> AlphabetIndex.buildAz(
                apps = visibleApps,
                space = activeSpace,
                recents = recents
            ).first
            DrawerFilter.MostUsed -> listOf("Recently used" to recentApps) +
                listOf("All apps" to visibleApps.filterNot { app -> recentApps.any { it.key == app.key } })
            DrawerFilter.Categories -> visibleApps.groupBy { it.category.label }
                .toSortedMap()
                .map { it.key to it.value }
        }
}

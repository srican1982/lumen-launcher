package com.lumen.launcher.vm

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
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
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.PeopleActions
import com.lumen.launcher.search.Routine
import com.lumen.launcher.search.SearchHit
import com.lumen.launcher.search.VoiceMatch
import com.lumen.launcher.BuildConfig
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
import com.lumen.launcher.util.CompetingLaunchers
import com.lumen.launcher.util.HomeRole
import com.lumen.launcher.util.StatusBarController
import com.lumen.launcher.util.TorchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val preferences = LauncherPreferences(application)
    private val weatherRepository = WeatherRepository(application)
    private lateinit var alarmModel: com.lumen.launcher.alarm.AlarmViewModel
    private var alarmStateJob: kotlinx.coroutines.Job? = null
    fun bindAlarms(model: com.lumen.launcher.alarm.AlarmViewModel) {
        if (::alarmModel.isInitialized && alarmModel === model) return
        alarmModel = model
        alarmStateJob?.cancel()
        alarmStateJob = viewModelScope.launch {
            model.state.collect { alarms -> _state.update { it.copy(alarms = alarms.alarms, alarmTone = alarms.tone) } }
        }
    }
    private val newsRepository = NewsRepository()
    private val weatherRefresh = com.lumen.launcher.data.RefreshGate(viewModelScope, 30 * 60_000L)
    private val newsRefresh = com.lumen.launcher.data.RefreshGate(viewModelScope, 45 * 60_000L)
    private val calendarRefresh = com.lumen.launcher.data.RefreshGate(viewModelScope, 60_000L)
    private val appsRefresh = com.lumen.launcher.data.RefreshGate(viewModelScope, Long.MAX_VALUE)
    private val missedRefresh = com.lumen.launcher.data.RefreshGate(viewModelScope, 30_000L)
    private var lastRebindAt = -60_001L
    private val launcherApps = application.getSystemService(android.content.pm.LauncherApps::class.java)
    private val packageCallback = object : android.content.pm.LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = refreshApps(true)
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = refreshApps(true)
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
            NotificationBadgeRepository.removePackage(packageName); refreshApps(true)
        }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = refreshApps(true)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = refreshApps(true)
    }
    private val calendarObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshCalendar(true) }
    }

    private val eventChannel = kotlinx.coroutines.channels.Channel<LauncherEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private fun emit(event: LauncherEvent) { eventChannel.trySend(event) }
    private val onAuthenticate: (()->Unit, ()->Unit)->Unit = { success, failure ->
        emit(LauncherEvent.Authenticate(success, failure))
    }
    private val onRequestContacts = { emit(LauncherEvent.Permission.Contacts) }
    private val onRequestMediaImages = { emit(LauncherEvent.Permission.MediaImages) }
    private val onRequestCalendar = { emit(LauncherEvent.Permission.Calendar) }
    private val onRequestCallLog = { emit(LauncherEvent.Permission.CallLog) }
    private val onRequestNotifications = { emit(LauncherEvent.Permission.Notifications) }
    private val onRequestMic = { emit(LauncherEvent.Permission.Mic) }
    private val onRequestMicQuiet = { emit(LauncherEvent.Permission.MicQuiet) }
    private val onRequestAssistant = { emit(LauncherEvent.Permission.Assistant) }
    private var deviceEvents: List<CalendarEvent> = emptyList()
    private var noticeEvents: List<CalendarEvent> = emptyList()
    private var digestStamp = ""
    private val torch = TorchController(application)
    val icons = IconCache(application)
    val socialCreate = SocialCreateCoordinator(application, viewModelScope)
    private val contacts = ContactLookup(application)
    private val deviceContentSearch = com.lumen.launcher.search.DeviceContentSearch(application)
    private var pendingPeople: SearchHit.Action? = null
    private var padLeft = 0f
    private var padTop = 0f
    private var padRight = 0f
    private var padBottom = 0f
    private val threeDaysMs = 3L * 24 * 60 * 60 * 1000

    private val _state = MutableStateFlow(LauncherUiState())
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()
    val chromeState = state
    val tasksState = state.map { TasksUiState(it.todos, it.activeSpace, it.duePickerTodoId) }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    lateinit var voice: VoiceViewModel
        private set
    fun bindVoice(model: VoiceViewModel) { voice = model }
    val homeActions: HomeActions = object : HomeActions {
        override fun snapshot(): VoiceHomeSnapshot {
            val s = _state.value
            return VoiceHomeSnapshot(
            activeSpace = s.activeSpace,
            aiContactNames = s.aiContactNames,
            aiInboxSummary = s.aiInboxSummary,
            aliases = s.aliases,
            apps = s.apps,
            calendarAccess = s.calendarAccess,
            dailyReview = s.dailyReview,
            dock = s.dock,
            dockCapacity = s.dockCapacity,
            dockKeys = s.dockKeys,
            favorites = s.favorites,
            focusTask = s.focusTask,
            folders = s.folders,
            geminiApiKey = s.geminiApiKey,
            hidden = s.hidden,
            iconSizeDp = s.iconSizeDp,
            inbox = s.inbox,
            inboxDigest = s.inboxDigest,
            launchTimes = s.launchTimes,
            needNowHints = s.needNowHints,
            newsInterests = s.newsInterests,
            nextEvent = s.nextEvent,
            privateApps = s.privateApps,
            privateUnlocked = s.privateUnlocked,
            sheet = s.sheet,
            smartVoice = s.smartVoice,
            spaceTodos = s.spaceTodos,
            upNext = s.upNext,
            upcomingEvents = s.upcomingEvents,
            visibleApps = s.visibleApps,
            weather = s.weather
            )
        }
        override fun emit(event: LauncherEvent) = this@LauncherViewModel.emit(event)
        override fun setSheet(sheet: Sheet) { _state.update { it.copy(sheet = sheet) } }
        override fun resolveApp(name: String, includeHidden: Boolean, includePrivate: Boolean, minScore: Int, voice: Boolean) = findApp(name, includeHidden, includePrivate, minScore, voice)
        override fun launchApp(app: AppInfo): Boolean {
            if (app.key in _state.value.privateApps && !_state.value.privateUnlocked) return false
            return launchNow(app)
        }
        override fun startIntent(intent: Intent) = this@LauncherViewModel.startIntent(intent)
        override fun goToPage(page: Int, recents: Boolean) = this@LauncherViewModel.goToPage(page, recents)
        override fun openDrawer() = this@LauncherViewModel.openDrawer()
        override fun openSearch() = this@LauncherViewModel.openSearch()
        override fun openSettings() = this@LauncherViewModel.openSettings()
        override fun openPrivateSpace() = this@LauncherViewModel.openPrivateSpace()
        override fun openFolderCreator() = this@LauncherViewModel.openFolderCreator()
        override fun selectSpace(space: SpaceKind?) = this@LauncherViewModel.selectSpace(space)
        override fun setIconSize(size: Float) = this@LauncherViewModel.setIconSize(size)
        override fun setShowLabels(show: Boolean) = this@LauncherViewModel.setShowLabels(show)
        override fun setGridColumns(columns: Int) { viewModelScope.launch { preferences.setGridColumns(columns) } }
        override fun setDockCapacity(count: Int) { viewModelScope.launch { preferences.setDockCapacity(count) } }
        override fun setFolders(folders: List<HomeFolder>) {
            _state.update { it.copy(folders = folders) }
            viewModelScope.launch { preferences.setFolders(folders) }
        }
        override fun rememberAlias(alias: String, key: String) {
            _state.update { it.copy(aliases = it.aliases + (alias to key)) }
            viewModelScope.launch { preferences.rememberAlias(alias, key) }
        }
        override fun setPrivateApps(keys: Set<String>) { viewModelScope.launch {
            if (!getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) preferences.setPrivateApps(keys)
        } }
        override fun lockPrivate() = this@LauncherViewModel.lockPrivate()
        override fun setFavorites(keys: List<String>) { viewModelScope.launch { preferences.setFavorites(keys) } }
        override fun setDockKeys(keys: List<String>) { viewModelScope.launch { preferences.setDockKeys(keys) } }
        override fun setHidden(packages: Set<String>) { viewModelScope.launch {
            if (!getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) preferences.setHidden(packages)
        } }
        override fun setNewsInterests(topics: Set<String>) = this@LauncherViewModel.setNewsInterests(topics)
        override fun setFlowModule(module: FlowModule, enabled: Boolean) = this@LauncherViewModel.setFlowModule(module, enabled)
        override fun moveFlowModuleTo(module: FlowModule, before: FlowModule?) = this@LauncherViewModel.moveFlowModuleTo(module, before)
        override fun refreshWeather() = this@LauncherViewModel.refreshWeather()
        override fun personalizeFlow() { _state.update { it.copy(personalizeFlow = true) } }
        override fun showPurposeResults(title: String, apps: List<AppInfo>) {
            _state.update { it.copy(sheet = Sheet.Search, query = title, hits = listOf(SearchHit.IntentGroup(title, "Apps on this phone", apps)) + apps.map { app -> SearchHit.App(app, 900) }) }
        }
        override fun authenticate(success: () -> Unit, failure: () -> Unit) = onAuthenticate(success, failure)
    }
    val taskActions: TaskActions = object : TaskActions {
        override fun addTodo(text: String, due: Long, repeat: String, priority: Boolean) { this@LauncherViewModel.addTodo(text, due, repeat, priority) }
        override fun addNote(text: String) { this@LauncherViewModel.addNote(text) }
        override fun saveForLater() = this@LauncherViewModel.saveForLater()
        override fun startFocus(minutes: Int) = this@LauncherViewModel.startFocus(minutes)
        override fun endFocus() = this@LauncherViewModel.endFocus()
        override fun matchTodo(text: String, includeDone: Boolean) = this@LauncherViewModel.matchTodo(text, includeDone)
        override fun toggleTodo(id: String, done: Boolean) = this@LauncherViewModel.toggleTodo(id, done)
        override fun deleteTodo(id: String) = this@LauncherViewModel.deleteTodo(id)
    }
    val alarmActions: AlarmActions = object : AlarmActions {
        override fun addAlarm(hour: Int, minute: Int, daily: Boolean) = this@LauncherViewModel.addAlarm(hour, minute, daily)
        override fun addAt(hour: Int, minute: Int, deadline: Long) = alarmModel.addAt(hour, minute, deadline)
        override fun addTimerSeconds(seconds: Int) = alarmModel.addTimerSeconds(seconds)
        override fun cancelAlarms(hour: Int?, minute: Int?) = alarmModel.cancel(hour, minute)
        override fun upcomingAlarms() = alarmModel.upcoming()
        override fun ensureNotifications() = ensureAlarmNotifications()
    }

    init {
        viewModelScope.launch {
            com.lumen.launcher.focus.FocusSessionManager.get(getApplication()).snapshot.collect { snap ->
                val prev = _state.value
                val wasFocusing = prev.focusUntil > 0L || prev.focusPausedRemainingMs > 0L
                val nowFocusing = snap.until > System.currentTimeMillis() || snap.pausedRemainingMs > 0L
                val finishedTaskId = if (wasFocusing && !nowFocusing) prev.focusTaskId else ""
                _state.update {
                    it.copy(
                        focusUntil = snap.until,
                        focusPausedRemainingMs = snap.pausedRemainingMs,
                        focusTotalMs = snap.totalMs,
                        focusTaskId = if (finishedTaskId.isNotBlank()) "" else it.focusTaskId
                    )
                }
                // Timer / alarm end — cross out the task this session was for.
                if (finishedTaskId.isNotBlank()) completeFocusSessionTask(finishedTaskId)
            }
        }

        viewModelScope.launch {
            val repository = com.lumen.launcher.travel.data.TripRepository.get(application)
            kotlinx.coroutines.flow.combine(repository.activeTripFlow, repository.pastTripsFlow) { active, past ->
                (listOfNotNull(active) + past).distinctBy { it.id }
            }.collect { trips ->
                _state.update { it.copy(searchTrips = trips) }
                if (_state.value.sheet == Sheet.Search) onQueryChange(_state.value.query)
            }
        }

        launcherApps.registerCallback(packageCallback)
        runCatching { application.contentResolver.registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, calendarObserver) }
        viewModelScope.launch {
            // Keep Trip Mode MediaStore observer alive across spaces after process start.
            runCatching { com.lumen.launcher.travel.data.TripRepository.get(application).restoreIfNeeded() }
        }
        viewModelScope.launch {
            socialCreate.creations.collect { list ->
                _state.update { it.copy(socialCreations = list) }
            }
        }
        viewModelScope.launch {
            InboxHub.conversations.collect { conversations ->
                _state.update { it.copy(recentConversations = conversations) }
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
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(60_000L)
                    _state.update { applyContext(it) }
                }
            }
            preferences.state.collect { stored ->
                val previousState = _state.value
                homePreferencesLoaded = true
                _state.update { current ->
                    current.copy(
                        homePreferencesReady = true,
                        favorites = stored.favorites,
                        recents = stored.recents,
                        launchTimes = stored.launchTimes,
                        hidden = stored.hidden,
                        iconSizeDp = stored.iconSizeDp,
                        iconGlassStrength = stored.iconGlassStrength,
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
                        voiceLanguage = stored.voiceLanguage,
                        geminiApiKey = stored.geminiApiKey,
                        aiInboxSummary = stored.aiInboxSummary,
                        aiContactNames = stored.aiContactNames,
                        flowEnabled = stored.flowEnabled,
                        flowOrder = stored.flowOrder,
                        folders = stored.folders,
                        workCalendars = stored.workCalendars,
                        personalCalendars = stored.personalCalendars,
                        storedWhatsAppMissed = stored.whatsAppMissed,
                        launchHours = stored.launchHours,
                        modeApps = stored.modeApps,
                        travelDestination = stored.travelDestination,
                        travelTicket = stored.travelTicket,
                        travelAttachments = stored.travelAttachments,
                        travelStops = stored.travelStops,
                        lastTravelStopId = stored.lastTravelStopId,
                        spaceDocks = stored.spaceDocks,
                        notes = stored.notes,
                        later = stored.later,
                        focusUntil = stored.focusUntil,
                        focusTaskId = stored.focusTaskId,
                        focusPausedRemainingMs = com.lumen.launcher.focus.FocusSession.pausedRemaining(getApplication()),
                        focusTotalMs = com.lumen.launcher.focus.FocusSession.totalMs(getApplication()),
                        focusPins = stored.focusPins,
                        spaceWallpapers = stored.spaceWallpapers,
                        notificationBadges = stored.notificationBadges
                    )
                }
                _state.update { applyContext(it) }
                if (previousState.hidden != stored.hidden || previousState.privateApps != stored.privateApps || previousState.aiInboxSummary != stored.aiInboxSummary) refreshInboxDigest(_state.value.inbox)
                if (_state.value.apps.any { it.packageName in stored.hidden }) {
                    preferences.recoverHiddenApps(_state.value.apps)
                }
                initializeHomeGrid()
                if (lastInterests != stored.newsInterests) {
                    lastInterests = stored.newsInterests
                    loadNews()
                }
                val calKey = "${stored.spaceOverride}|${stored.workCalendars}|${stored.personalCalendars}"
                if (lastCalendarKey != calKey) {
                    lastCalendarKey = calKey
                    refreshCalendar()
                }

            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        launcherApps.unregisterCallback(packageCallback)
        getApplication<Application>().contentResolver.unregisterContentObserver(calendarObserver)
        eventChannel.close()
    }

    private var homePreferencesLoaded = false

    private suspend fun initializeHomeGrid() {
        if (!homePreferencesLoaded) return
        val current = _state.value
        if (current.apps.isEmpty()) return
        val allowed = com.lumen.launcher.data.HomeUsage.allowed(getApplication())
        _state.update { it.copy(homeUsageAllowed = allowed) }
    }

    fun refreshApps(force: Boolean = false) {
        appsRefresh.run(force) {
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
            preferences.recoverHiddenApps(apps)
            initializeHomeGrid()
        }
    }

    fun setWorkspaceOpen(open: Boolean) { _state.update { it.copy(workspaceOpen = open) } }

    fun onHomeVisible() {
        viewModelScope.launch { initializeHomeGrid() }
        resumeFocusSetup()

        viewModelScope.launch { com.lumen.launcher.focus.FocusSession.recover(getApplication()) }
        lockPrivate()
        _state.update {
            it.copy(
                isDefaultHome = isDefaultHome(),
                query = "",
                sheet = Sheet.None,
                privatePageActive = false,
                workspaceOpen = false,
                homePulse = it.homePulse + 1
            )
        }
        refreshWeather()
        _state.update { applyContext(it) }
        refreshInboxDigest(_state.value.inbox)
    }

    private fun refreshWeather() {
        weatherRefresh.run {
            val weather = runCatching { weatherRepository.load() }.getOrNull()
            if (isActive && weather != null) _state.update { it.copy(weather = weather) }
        }
    }

    fun refreshFlow() {
        refreshWeather()
        refreshCalendar()
        refreshMissedCalls()
        val inboxAccess = InboxHub.hasAccess(getApplication())
        val rebindNow = android.os.SystemClock.elapsedRealtime()
        if (inboxAccess && rebindNow - lastRebindAt > 60_000L) {
            lastRebindAt = rebindNow
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
        showPermissionGuide(
            PermissionGuide(
                kind = PermissionGuideKind.NotificationListener,
                title = "Turn on Lumen",
                body = "We'll open Lumen's notification access page. Flip the toggle on — no scrolling through a long app list."
            )
        )
    }

    fun dismissPermissionGuide() {
        _state.update { it.copy(permissionGuide = null) }
    }

    fun openPermissionGuideSettings() {
        val guide = _state.value.permissionGuide ?: return
        val context = getApplication<Application>()
        dismissPermissionGuide()
        when (guide.kind) {
            PermissionGuideKind.DndAccess -> com.lumen.launcher.util.PermissionDeepLinks.openDndAccess(context)
            PermissionGuideKind.ExactAlarm -> com.lumen.launcher.util.PermissionDeepLinks.openExactAlarm(context)
            PermissionGuideKind.NotificationListener -> com.lumen.launcher.util.PermissionDeepLinks.openNotificationListener(context)
        }
    }

    private fun showPermissionGuide(guide: PermissionGuide) {
        _state.update { it.copy(permissionGuide = guide) }
    }

    fun openMailApp() {
        val preferred = _state.value.inbox.firstOrNull()?.packageName
        val order = listOfNotNull(preferred, "com.google.android.gm", "com.microsoft.office.outlook")
        for (pkg in order.distinct()) {
            val launch = getApplication<Application>().packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null && startIntent(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        }
    }

    fun openConversation(item: InboxItem) {
        if (!com.lumen.launcher.focus.FocusAppAccess.allows(getApplication(), Intent().setPackage(item.packageName))) {
            notifyFocusAppBlocked()
            return
        }
        emit(LauncherEvent.Conversation(item))
    }

    fun openInboxItem(item: InboxItem) {
        openConversation(item)
    }

    fun requestCalendarAccess() {
        onRequestCalendar?.invoke()
    }

    fun requestCallLogAccess() {
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
            if (_state.value.sheet == Sheet.Voice) voice.promptExactAfterSpeak = true
            else emit(LauncherEvent.Confirm("Allow exact alarms", "Allow Lumen to schedule alarms at the time you choose.") { AlarmScheduler.requestExactAccess(getApplication()) })
        }
        if (!AlarmScheduler.canFullScreen(getApplication())) {
            emit(LauncherEvent.Confirm("Show alarms on the lock screen", "Allow full-screen alarms so the ringing screen can appear when your phone is locked. Sound and notification controls still work without this access.") { AlarmScheduler.requestFullScreenAccess(getApplication()) })
        }
    }

    fun onCalendarPermission(granted: Boolean) {
        _state.update { it.copy(calendarAccess = granted) }
        if (granted) {
            runCatching { getApplication<Application>().contentResolver.registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, calendarObserver) }
            refreshCalendar(true)
        }
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

    private fun refreshCalendar(force: Boolean = false) {
        calendarRefresh.run(force, _state.value.workCalendars to _state.value.personalCalendars) {
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
        val keys = state.dockKeys
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
        return state.dockKeys
            ?: state.dock.map { it.key }
    }

    private var digestJob: kotlinx.coroutines.Job? = null
    private var flowVisible = false
    fun onFlowVisible(visible: Boolean) {
        flowVisible = visible
        if (visible) refreshInboxDigest(_state.value.inbox)
        else digestJob?.cancel()
    }
    fun setAiInboxSummary(enabled: Boolean) {
        _state.update { it.copy(aiInboxSummary = enabled) }
        viewModelScope.launch { preferences.setAiInboxSummary(enabled) }
        digestStamp = ""
        digestJob?.cancel()
        refreshInboxDigest(_state.value.inbox)
    }
    fun setAiContactNames(enabled: Boolean) {
        _state.update { it.copy(aiContactNames = enabled) }
        viewModelScope.launch { preferences.setAiContactNames(enabled) }
    }
    private fun refreshInboxDigest(items: List<InboxItem>) {
        digestJob?.cancel()
        val state = _state.value
        val excluded = state.hidden + state.apps.filter { it.key in state.privateApps }.map { it.packageName }
        val safe = com.lumen.launcher.inbox.AiInboxPrivacy.filter(items, excluded)
        _state.update { it.copy(inboxDigest = InboxDigest.local(safe)) }
        if (!state.aiInboxSummary || !flowVisible || state.geminiApiKey.isBlank()) return
        val stamp = InboxDigest.fingerprint(safe)
        if (stamp == digestStamp) return
        digestJob = viewModelScope.launch {
            kotlinx.coroutines.delay(45_000)
            val text = withContext(Dispatchers.IO) { InboxDigest.summarize(state.geminiApiKey, safe) }
            if (!kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive) return@launch
            if (_state.value.aiInboxSummary && flowVisible) {
                digestStamp = stamp
                _state.update { it.copy(inboxDigest = text) }
            }
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

    private fun refreshMissedCalls(force: Boolean = false) {
        missedRefresh.run(force) {
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
            if (!com.lumen.launcher.focus.FocusAppAccess.allows(getApplication(), Intent().setPackage("com.whatsapp"))) {
                notifyFocusAppBlocked()
                return
            }
            emit(LauncherEvent.Missed(missed) {
                val phone = missed.number.ifBlank { contacts.matches(missed.name, 1).firstOrNull()?.phone.orEmpty() }
                openWhatsApp(phone, "")
            })
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
        val trimmed = text.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
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
                if (markingDone) com.lumen.launcher.data.TodoCompletion.complete(item, System.currentTimeMillis())
                else item.copy(done = false, completedAt = null)
            }
        }
        persistTodos(next)
    }

    fun restoreTodo(item: TodoItem) {
        val current = _state.value.todos
        persistTodos(if (current.any { it.id == item.id }) current.map { if (it.id == item.id) item else it } else current + item)
    }

    fun renameTodo(id: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) persistTodos(_state.value.todos.map { if (it.id == id) it.copy(text = trimmed) else it })
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
        val alarm = alarmModel.add(hour, minute, daily, label)
        ensureAlarmNotifications()
        return alarm
    }
    fun toggleAlarm(id: String) { alarmModel.toggle(id); ensureAlarmNotifications() }
    fun deleteAlarm(id: String) = alarmModel.delete(id)
    fun cycleAlarmTone() = alarmModel.cycleTone()
    private fun cancelAlarms(hour: Int?, minute: Int?) = alarmModel.cancel(hour, minute)
    private fun upcomingAlarms() = alarmModel.upcoming()

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
        newsRefresh.run(key = _state.value.newsInterests) {
            val topics = _state.value.selectedNewsTopics
            if (topics.isEmpty()) {
                _state.update { it.copy(news = emptyList(), newsLoading = false) }
                return@run
            }
            _state.update { it.copy(newsLoading = true) }
            val items = runCatching { newsRepository.load(topics) }.getOrDefault(emptyList())
            if (isActive) _state.update { it.copy(news = items, newsLoading = false) }
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

    fun toggleHomeApp(app: AppInfo) {
        val current = _state.value
        if (current.visibleApps.none { it.key == app.key } || current.dock.any { it.packageName == app.packageName }) return
        val home = current.copy(spaceOverride = SpaceKind.Home).homeApps.map { it.key }
        val next = if (app.key in home) home - app.key
            else if (home.size < com.lumen.launcher.data.ModeAppsResolver.MAX_APPS) home + app.key else return
        saveModeApps(SpaceKind.Home, next)
    }

    fun saveModeApps(space: SpaceKind, keys: List<String>) {
        val next = _state.value.modeApps + (space.name to keys.distinct().take(com.lumen.launcher.data.ModeAppsResolver.MAX_APPS))
        _state.update { it.copy(modeApps = next) }
        viewModelScope.launch { preferences.setModeApps(next) }
    }

    fun addTravelAttachments(items: List<com.lumen.launcher.data.TravelAttachment>) {
        viewModelScope.launch { preferences.updateTravelAttachments(add = items) }
    }

    fun renameTravelAttachment(item: com.lumen.launcher.data.TravelAttachment, name: String) {
        viewModelScope.launch { preferences.updateTravelAttachments(add = listOf(item.copy(title = name)), remove = item) }
    }

    fun removeTravelAttachment(item: com.lumen.launcher.data.TravelAttachment) {
        viewModelScope.launch { preferences.updateTravelAttachments(remove = item) }
    }

    fun saveTravel(destination: String, ticket: String) {
        _state.update { it.copy(travelDestination = destination.trim(), travelTicket = ticket) }
        viewModelScope.launch { preferences.setTravel(destination.trim(), ticket) }
    }

    fun runModeAction() {
        when (_state.value.activeSpace) {
            SpaceKind.Home -> _state.value.upNext?.let(::openUpNext) ?: openCapture(CaptureKind.Reminder)
            SpaceKind.Work -> openCapture(CaptureKind.Task)
            SpaceKind.Personal -> setRecentsOpen(true)
            SpaceKind.Focus -> {
                if (_state.value.focusing) selectSpace(SpaceKind.Focus)
                else startFocus(
                    com.lumen.launcher.focus.FocusAllowedPeopleRepository.get(getApplication())
                        .settingsNow().lastDurationMinutes
                )
            }
            SpaceKind.Travel -> openSearch()
            SpaceKind.Private -> openPrivateSpace()
        }
    }

    fun selectSpace(space: SpaceKind?) {
        val next = space.takeUnless { it == SpaceKind.Private }
        _state.update { it.copy(spaceOverride = next) }
        viewModelScope.launch { preferences.setSpaceOverride(next?.name) }
    }

    fun togglePrivate(app: AppInfo, onChanged: () -> Unit = {}) {
        onAuthenticate?.invoke({ viewModelScope.launch {
            val next = _state.value.privateApps.toMutableSet()
            if (app.key in next) next.remove(app.key) else next.add(app.key)
            preferences.setPrivateApps(next)
            lockPrivate()
            dismissAppActions()
            onChanged()
        } }, {})
    }

    fun removeFromPrivate(app: AppInfo) {
        val restore = {
            viewModelScope.launch {
                val keys = (_state.value.apps.filter { it.packageName == app.packageName }.map { it.key } + app.key).toSet()
                preferences.removeFromPrivate(keys, app.packageName)
                _state.update { it.copy(privateApps = it.privateApps - keys, hidden = it.hidden - app.packageName) }
                dismissAppActions()
            }
            Unit
        }
        if (_state.value.privateUnlocked) restore()
        else onAuthenticate?.invoke(restore, {})
    }

    fun moveToPrivate(app: AppInfo) {
        if (app.key in _state.value.privateApps) return
        togglePrivate(app)
    }

    fun openSearch() {
        _state.update { it.copy(sheet = Sheet.Search, query = "", hits = emptyList(), modeSearch = true) }
        onQueryChange("")
    }

    fun openModeSearch() {
        openSearch()
    }

    fun searchPlace(query: String) {
        val place = query.trim()
        if (place.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { getApplication<Application>().startActivity(intent) }.isSuccess) closeSheet()
        else runHit(SearchHit.Web("maps $place"))
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

    private data class FocusSuggestionKey(val task: String, val apps: List<String>, val credential: String)
    private data class FocusSuggestionEntry(val keys: List<String>, val expires: Long)
    private val focusSuggestionCache = linkedMapOf<FocusSuggestionKey, FocusSuggestionEntry>()

    private fun focusHistory(): Map<String, Map<String, Int>> {
        val saved = getApplication<Application>().getSharedPreferences("focus_learning", 0)
        return runCatching {
            val root = org.json.JSONObject(saved.getString("history", "{}").orEmpty())
            root.keys().asSequence().associateWith { task ->
                val counts = root.getJSONObject(task)
                counts.keys().asSequence().associateWith { counts.optInt(it) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun learnFocusLaunch(app: AppInfo) {
        val s = _state.value
        val task = s.focusTask ?: return
        if (!s.focusRunning || s.activeSpace != SpaceKind.Focus || app.key in s.privateApps) return
        val history = focusHistory().toMutableMap()
        val counts = history.remove(task.text).orEmpty().toMutableMap()
        counts[app.key] = ((counts[app.key] ?: 0) + 1).coerceAtMost(10)
        history[task.text] = counts
        val root = org.json.JSONObject()
        history.entries.toList().takeLast(100).forEach { (text, apps) -> root.put(text, org.json.JSONObject(apps)) }
        getApplication<Application>().getSharedPreferences("focus_learning", 0).edit().putString("history", root.toString()).apply()
    }

    suspend fun suggestFocusApps(task: String, apps: List<AppInfo>): List<String> {
        val credential = _state.value.geminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        val learned = com.lumen.launcher.data.FocusLearning.rank(task, focusHistory(), apps.map { it.key }.toSet())
        val local = com.lumen.launcher.data.NeedNowResolver.matchTaskApps(TodoItem("", task), apps).map { it.key }
        val cacheKey = FocusSuggestionKey(task.trim(), apps.map { "${it.key}:${it.label}" }.sorted(), credential)
        val cached = focusSuggestionCache[cacheKey]?.takeIf { it.expires > System.currentTimeMillis() }
        val ai = if ((learned + local).distinct().size >= 2 || credential.isBlank()) emptyList() else if (cached != null) cached.keys else {
            val result = withContext(Dispatchers.IO) { com.lumen.launcher.data.FocusAiSuggestions.suggest(credential, task, apps) }
            focusSuggestionCache[cacheKey] = FocusSuggestionEntry(result.orEmpty(),
                if (result == null) System.currentTimeMillis() + 60_000 else Long.MAX_VALUE)
            if (focusSuggestionCache.size > 64) focusSuggestionCache.remove(focusSuggestionCache.keys.first())
            result.orEmpty()
        }
        val people = withContext(Dispatchers.IO) { PeopleActions.hits(contacts, task).filter { it.phone.isNotBlank() } }
        val ranked = (learned + ai + local).distinct().take(4)
        if (_state.value.focusTask?.text == task) _state.update {
            it.copy(focusSuggestionTask = task, focusSuggestedKeys = ranked, focusPeopleActions = people)
        }
        return ranked
    }

    fun excludeFocusApp(app: AppInfo, scope: String) {
        val name = "Exclude:$scope"
        val excluded = _state.value.focusPins[name].orEmpty()
        val next = if (app.key in excluded) excluded - app.key else (excluded + app.key).takeLast(100)
        persistFocusPins(_state.value.focusPins + (name to next) +
            (scope to _state.value.focusPins[scope].orEmpty().filterNot { it == app.key }))
    }

    fun toggleFocusPin(app: AppInfo, scope: String = _state.value.activeSpace.name) {
        val space = scope
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

    fun selectFocusTask(taskId: String) {
        if (_state.value.todos.none { it.id == taskId && !it.done }) return
        _state.update { it.copy(focusTaskId = taskId) }
        viewModelScope.launch { preferences.setFocus(_state.value.focusUntil, taskId) }
    }

    fun joinWorkspaceMeeting(event: CalendarEvent) {
        val uri = Uri.parse(event.location.trim())
        if (uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) {
            if (startIntent(Intent(Intent.ACTION_VIEW, uri))) return
        }
        openCalendarEvent(event)
    }

    private val focusSetup get() = getApplication<Application>().getSharedPreferences("focus_setup", 0)

    fun onFocusNotificationPermission(granted: Boolean) {
        if (granted) resumeFocusSetup(notificationResult = true)
    }

    fun resumeFocusSetup(notificationResult: Boolean = false) {
        val saved = focusSetup
        if (!saved.contains("minutes")) return
        val context = getApplication<Application>()
        val ready = when (saved.getString("step", "")) {
            "dnd" -> com.lumen.launcher.focus.FocusSession.hasAccess(context)
            "alarm" -> AlarmScheduler.canExact(context)
            "notification" -> notificationResult || android.os.Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> false
        }
        if (ready) startFocus(saved.getInt("minutes", 25), saved.getString("task", null), saved.getBoolean("open_space", true))
    }

    fun startFocus(minutes: Int = 30, taskId: String? = null, openSpace: Boolean = true) {
        focusSetup.edit().putInt("minutes", minutes).putString("task", taskId.orEmpty()).putBoolean("open_space", openSpace).apply()
        val context = getApplication<Application>()
        fun explain(message: String) = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        if (!com.lumen.launcher.focus.FocusSession.hasAccess(context)) {
            focusSetup.edit().putString("step", "dnd").apply()
            showPermissionGuide(
                PermissionGuide(
                    kind = PermissionGuideKind.DndAccess,
                    title = "Turn on Lumen",
                    body = "Focus needs Do Not Disturb access. We'll open Lumen's toggle directly — flip it on, then come back."
                )
            )
            return
        }
        if (!AlarmScheduler.canExact(context)) {
            focusSetup.edit().putString("step", "alarm").apply()
            showPermissionGuide(
                PermissionGuide(
                    kind = PermissionGuideKind.ExactAlarm,
                    title = "Allow exact alarms",
                    body = "We'll open Lumen's alarm permission so Focus can end on time. Enable the toggle, then return."
                )
            )
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            focusSetup.edit().putString("step", "notification").apply()
            explain("Allow notifications for your completion alert. Focus will then start.")
            onRequestNotifications?.invoke()
            return
        }
        focusSetup.edit().clear().apply()
        viewModelScope.launch {
            val resolvedTask = (taskId?.takeIf { it.isNotBlank() }
                ?: _state.value.focusTaskId.takeIf { id -> _state.value.todos.any { it.id == id && !it.done } }
                ?: "").orEmpty()
            val manager = com.lumen.launcher.focus.FocusSessionManager.get(context)
            if (manager.start(minutes.coerceIn(1, 24 * 60))) {
                val snap = manager.snapshotNow()
                preferences.setFocus(snap.until, resolvedTask)
                _state.update {
                    it.copy(
                        focusUntil = snap.until,
                        focusPausedRemainingMs = 0L,
                        focusTotalMs = snap.totalMs,
                        focusTaskId = resolvedTask
                    )
                }
                // Stay on / open Focus Space — switching away later must not end the session.
                if (openSpace) {
                    selectSpace(SpaceKind.Focus)
                    goToPage(1)
                }
            } else explain("Focus could not enable quiet mode. Check Lumen's Do Not Disturb access.")
        }
    }

    fun endFocus() {
        viewModelScope.launch {
            val taskId = _state.value.focusTaskId
            val restored = com.lumen.launcher.focus.FocusSession.finish(getApplication())
            if (taskId.isNotBlank()) completeFocusSessionTask(taskId)
            _state.update { it.copy(focusUntil = 0L, focusTaskId = "", focusPausedRemainingMs = 0L, focusTotalMs = 0L) }
            if (!restored) {
                showPermissionGuide(
                    PermissionGuide(
                        kind = PermissionGuideKind.DndAccess,
                        title = "Turn off Lumen Focus",
                        body = "We'll open Lumen's Do Not Disturb page so you can turn the Focus rule off."
                    )
                )
            }
        }
    }

    /** Mark the Focus task done and park it in the Focus "Task done" list. */
    private fun completeFocusSessionTask(taskId: String) {
        val task = _state.value.todos.find { it.id == taskId } ?: return
        com.lumen.launcher.focus.FocusDoneStore.record(getApplication(), task.id, task.text)
        if (!task.done) toggleTodo(taskId, done = true)
        _state.update { it.copy(focusDonePulse = it.focusDonePulse + 1) }
    }

    fun pauseFocus() { viewModelScope.launch { com.lumen.launcher.focus.FocusSessionManager.get(getApplication()).pause() } }
    fun resumeFocus() { viewModelScope.launch { com.lumen.launcher.focus.FocusSessionManager.get(getApplication()).resume() } }
    fun extendFocus(minutes: Int = 15) { viewModelScope.launch { com.lumen.launcher.focus.FocusSessionManager.get(getApplication()).extend(minutes) } }

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
        voice.cancelSession(stopListening = _state.value.sheet == Sheet.Voice)
        _state.update { it.copy(sheet = Sheet.None, query = "", hits = emptyList(), activeApp = null, pickerKind = "", activeFolderId = null, folderSeedAppKey = null) }
    }

    fun openMenu() {
        if (_state.value.sheet == Sheet.Drawer || _state.value.sheet == Sheet.Search || _state.value.sheet == Sheet.Voice) return
        _state.update { it.copy(sheet = Sheet.Menu) }
    }

    fun openSettings() {
        _state.update { it.copy(sheet = Sheet.Settings) }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        _state.update { current ->
            val next = current.copy(query = query)
            next.copy(hits = com.lumen.launcher.search.SpaceSearch.results(next))
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            val (people, device) = withContext(Dispatchers.IO) {
                val explicit = PeopleActions.hits(contacts, query)
                val people = if (explicit.isNotEmpty() || query.isBlank()) explicit else contacts.matches(query).flatMap { person ->
                    listOf(
                        SearchHit.Action("call", person.name, "Call · ${person.phone}", query, phone = person.phone),
                        SearchHit.Action("whatsapp", "WhatsApp ${person.name}", "Open contact chat", query, phone = person.phone)
                    )
                }
                people to deviceContentSearch.results(query)
            }
            if (query != _state.value.query) return@launch
            _state.update { it.copy(hits = com.lumen.launcher.search.SpaceSearch.results(it, people, device)) }
        }
    }

    fun onPhotoSearchPermission(granted: Boolean) {
        if (granted) onQueryChange(_state.value.query)
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
        val first = hits.firstOrNull { it !is SearchHit.Math }
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
        if (kind == "double" && spec.isBlank()) {
            runModeAction()
            return
        }
        if (spec == GestureAction.VOICE) {
            voice.openVoice()
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

    fun setHeyLumen(enabled: Boolean) {
        viewModelScope.launch { preferences.setHeyLumen(enabled) }
        _state.update { it.copy(heyLumen = enabled) }
        if (enabled) onRequestMicQuiet?.invoke()
    }

    fun setVoiceLanguage(language: String) {
        val selected = if (language == "en-US") language else ""
        viewModelScope.launch { preferences.setVoiceLanguage(selected) }
        _state.update { it.copy(voiceLanguage = selected) }
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

    fun openTodoList() {
        goToPage(2)
    }

    fun openWidgets() {
        closeSheet()
        if (_state.value.activeSpace == SpaceKind.Focus) selectSpace(SpaceKind.Home)
        _state.update { it.copy(privatePageActive = false) }
        goToPage(3)
    }

    fun openWidgetDestination(destination: String, item: String) {
        closeSheet()
        _state.update { it.copy(privatePageActive = false) }
        when (destination) {
            "Tasks", "Social", "Trip", "Hush", "Focus", "Photo", "TripToggle" ->
                _state.update { it.copy(sheet = Sheet.WidgetTool, widgetTool = destination, widgetItem = item) }
            "TasksOverview", "TasksFilters", "TasksNext", "TasksManager" ->
                _state.update { it.copy(sheet = Sheet.WidgetTool, widgetTool = "Tasks", widgetItem = item) }
            "TaskList" -> openTodoList()
            "AddTask" -> openCapture(CaptureKind.Task)
            "CompleteTask" -> {
                val task = com.lumen.launcher.data.TaskAgenda.today(_state.value.todos, System.currentTimeMillis())
                    .firstOrNull { com.lumen.launcher.widgets.WidgetTaskTarget.matches(it, item) && it.space != SpaceKind.Private }
                if (task != null) toggleTodo(task.id, true)
            }
            "Scribble" -> openSocialTool(SocialCreateTool.Scribble)
            "Quote" -> openSocialTool(SocialCreateTool.Quote)
            "Photos" -> openSocialTool(SocialCreateTool.Photo)
            "TripPlan" -> { selectSpace(SpaceKind.Travel); _state.update { it.copy(workspaceOpen = true) } }
            "TripCamera" -> startIntent(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "TripNote" -> openCapture(CaptureKind.Note)
            "TripShare" -> viewModelScope.launch {
                val tripId = item.toLongOrNull()
                val photos = if (tripId != null) com.lumen.launcher.travel.data.TravelDatabase.get(getApplication()).tripDao().getTripPhotos(tripId) else emptyList()
                val uris = photos.filter { it.contentUri.isNotBlank() }.map { Uri.parse(it.contentUri) }
                if (uris.isNotEmpty()) com.lumen.launcher.travel.share.TripShareManager.shareImages(getApplication(), uris)
                else _state.update { it.copy(sheet = Sheet.TripAlbums, searchTripId = tripId) }
            }
            "TripUpcoming", "TripCompleted", "TripCompact", "TripSmall", "TripActions", "TripCollage" ->
                _state.update { it.copy(sheet = Sheet.TripAlbums) }
            "TripAlbums" -> _state.update { it.copy(sheet = Sheet.TripAlbums, searchTripId = item.toLongOrNull()) }
            "TravelDocs" -> _state.update { it.copy(sheet = Sheet.TravelDocs, searchDocumentUri = null, searchTravelCategory = null) }
            "StartFocus", "FocusSetup" -> {
                _state.update { it.copy(sheet = Sheet.WidgetTool, widgetTool = "Focus", widgetItem = "start") }
            }
            "UsageAccess" -> startIntent(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "Battery" -> startIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            "Contacts" -> startIntent(Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI))
            "Person" -> item.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
                viewModelScope.launch {
                    val number = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            getApplication<Application>().contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?", arrayOf(id.toString()),
                                "${android.provider.ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC")?.use {
                                    if (it.moveToFirst()) it.getString(0) else null
                                }
                        }.getOrNull()
                    }
                    if (!number.isNullOrBlank()) startIntent(Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number, null)))
                    else startIntent(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id)))
                }
            }
        }
    }

    private fun goToPage(page: Int, recents: Boolean = false) {
        if (getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
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

    private fun findApp(
        name: String,
        includeHidden: Boolean = false,
        includePrivate: Boolean = _state.value.privateUnlocked,
        minScore: Int = 500,
        voice: Boolean = false
    ): AppInfo? {
        val state = _state.value
        val pool = when {
            includeHidden && includePrivate && state.privateUnlocked -> state.apps
            includePrivate && state.privateUnlocked -> (state.visibleApps + state.privateAppList).distinctBy { it.key }
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

    fun reorderDock(fromKey: String, toKey: String) {
        if (fromKey == toKey || fromKey.isBlank() || toKey.isBlank()) return
        viewModelScope.launch {
            val list = liveDockKeys().toMutableList()
            val from = list.indexOfFirst { it == fromKey }
            val to = list.indexOfFirst { it == toKey }
            if (from < 0 || to < 0) return@launch
            list[from] = toKey
            list[to] = fromKey
            preferences.setDockKeys(list, _state.value.activeSpace.name)
        }
    }

    fun placeOnDock(app: AppInfo, slotIndex: Int) {
        if (app.key.isBlank()) return
        viewModelScope.launch {
            val state = _state.value
            // Default dock shortcuts use aliases/non-launcher activities. Home needs launcher keys.
            val resolvedKeys = state.dock.map { dock ->
                state.apps.find { it.key == dock.key }?.key
                    ?: state.apps.firstOrNull { it.packageName == dock.packageName }?.key
                    ?: dock.key
            }
            val result = com.lumen.launcher.data.DockPlacement.place(
                resolvedKeys, state.homeApps.map { it.key }, app.key, slotIndex, state.dockCapacity.coerceIn(3, 6))
            val modes = state.modeApps + (state.activeSpace.name to result.home)
            _state.update { it.copy(modeApps = modes) }
            preferences.saveDockExchange(state.activeSpace.name, result.home, result.dock)
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

    private fun launchNow(app: AppInfo): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        if (!startIntent(intent)) return false
        learnFocusLaunch(app)
        val typedSearch = _state.value.query.trim()
        viewModelScope.launch {
            preferences.recordLaunch(app.key)
            if (typedSearch.isNotEmpty()) preferences.noteSpokenOpen(typedSearch, app.key, app.label)
        }
        closeSheet()
        return true
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

    @Volatile private var lastFocusBlockToastAt = 0L

    private fun notifyFocusAppBlocked() {
        // Dock / multi-intent launchers retry several intents — only toast once per tap.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFocusBlockToastAt <= 2_500L) return
        lastFocusBlockToastAt = now
        android.widget.Toast.makeText(
            getApplication(),
            "This app isn't selected for Focus. Pause or end Focus to open it.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun startIntent(intent: Intent): Boolean {
        if (!com.lumen.launcher.focus.FocusAppAccess.allows(getApplication(), intent)) {
            notifyFocusAppBlocked()
            return false
        }
        val ready = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (getApplication<Application>().packageManager.resolveActivity(ready, 0) == null) return false
        emit(LauncherEvent.Open(ready))
        return true
    }

    private fun runLocalSearchHit(hit: SearchHit.Action): Boolean {
        val s = _state.value
        when {
            hit.id == "permission:contacts" -> onRequestContacts()
            hit.id == "permission:photos" -> onRequestMediaImages()
            hit.id.startsWith("contact:") -> {
                val id = hit.id.removePrefix("contact:").toLongOrNull() ?: return true
                val uri = android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id)
                if (startIntent(Intent(Intent.ACTION_VIEW, uri))) closeSheet()
            }
            hit.id.startsWith("photo:") -> {
                val uri = Uri.parse(hit.id.removePrefix("photo:"))
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (startIntent(intent)) closeSheet()
            }
            hit.id.startsWith("task:") -> {
                val task = s.todos.find { it.id == hit.id.removePrefix("task:") } ?: return true
                closeSheet()
                selectSpace(task.space)
                openTodoList()
            }
            hit.id.startsWith("note:") -> {
                val note = s.notes.find { it.id == hit.id.removePrefix("note:") } ?: return true
                _state.update { it.copy(sheet = Sheet.SearchDetail, searchDetail = note.text) }
            }
            hit.id.startsWith("event:") -> {
                s.upcomingEvents.find { it.id.toString() == hit.id.removePrefix("event:") }?.let { closeSheet(); openCalendarEvent(it) }
            }
            hit.id.startsWith("inbox:") -> {
                s.inbox.find { it.key == hit.id.removePrefix("inbox:") }?.let { closeSheet(); openInboxItem(it) }
            }
            hit.id.startsWith("create:") -> {
                val tool = SocialCreateTool.entries.find { it.name == hit.id.removePrefix("create:") } ?: return true
                closeSheet()
                openSocialTool(tool)
            }
            hit.id.startsWith("document:") -> _state.update { it.copy(sheet = Sheet.TravelDocs, searchTravelCategory = null, searchDocumentUri = hit.id.removePrefix("document:")) }
            hit.id.startsWith("travel:") -> _state.update { it.copy(sheet = Sheet.TravelDocs, searchTravelCategory = com.lumen.launcher.data.TravelCategory.entries.find { c -> c.name == hit.id.removePrefix("travel:") }, searchDocumentUri = null) }
            hit.id.startsWith("trip:") || hit.id == "trip_albums" -> _state.update { it.copy(sheet = Sheet.TripAlbums, searchTripId = hit.id.removePrefix("trip:").toLongOrNull()) }
            hit.id == "tasks" -> { closeSheet(); openTodoList() }
            hit.id == "notes" -> openCapture(CaptureKind.Note)
            hit.id == "calendar" -> { closeSheet(); openCalendarApp() }
            hit.id == "files" -> {
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "vnd.android.document/root").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { getApplication<Application>().startActivity(intent) }.onFailure {
                    runCatching { getApplication<Application>().startActivity(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ) }
                }
                closeSheet()
            }
            hit.id == "weather" -> runHit(SearchHit.Web("weather"))
            hit.id == "places" -> searchPlace(hit.query)
            else -> return false
        }
        return true
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
                if (runLocalSearchHit(hit)) return
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
            val list = current.homeApps.map { it.key }
                .toMutableList()
            val from = list.indexOf(fromKey)
            val to = list.indexOf(toKey)
            if (from < 0 || to < 0) return@launch
            list.removeAt(from)
            list.add(to, fromKey)
            saveModeApps(current.activeSpace, list)
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
        onAuthenticate?.invoke({ viewModelScope.launch {
            val current = _state.value.hidden.toMutableSet()
            if (app.packageName in current) current.remove(app.packageName) else current.add(app.packageName)
            preferences.setHidden(current)
            dismissAppActions()
        } }, {})
    }

    /** Explicit hide drop: persist silently; unlocking or revealing apps still requires authentication. */
    fun hideAppFromSearch(app: AppInfo) {
        viewModelScope.launch {
            val keys = (_state.value.apps.filter { it.packageName == app.packageName }.map { it.key } + app.key).toSet()
            preferences.hideInPrivate(keys, setOf(app.packageName))
            _state.update { it.copy(privateApps = it.privateApps + keys, hidden = it.hidden - app.packageName) }
            if (_state.value.sheet == Sheet.Search) onQueryChange(_state.value.query)
        }
    }

    fun unhide(packageName: String) {
        onAuthenticate?.invoke({ viewModelScope.launch {
            preferences.setHidden(_state.value.hidden - packageName)
        } }, {})
    }

    fun setIconGlassStrength(value: Float) {
        viewModelScope.launch { preferences.setIconGlassStrength(value) }
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

    fun addAppToFolder(app: AppInfo, folderId: String) {
        val folders = _state.value.folders.map { folder ->
            if (folder.id == folderId) folder.copy(appKeys = (folder.appKeys + app.key).distinct()) else folder
        }
        _state.update { it.copy(folders = folders) }
        viewModelScope.launch { preferences.setFolders(folders) }
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
            if (index >= 0) folders[index] = folders[index].copy(name = trimmed, appKeys = keys, space = _state.value.activeSpace.name)
        } else {
            folders += HomeFolder(id = java.util.UUID.randomUUID().toString(), name = trimmed, appKeys = keys, space = _state.value.activeSpace.name)
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
        if (com.lumen.launcher.lock.ScreenLockService.lock()) return
        val context = getApplication<Application>()
        val admin = ComponentName(context, LockAdminReceiver::class.java)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) {
            emit(LauncherEvent.Confirm("Enable the screen lock shortcut",
                "Lumen uses an optional accessibility service only to lock the screen on your request. It does not read screen content or monitor your actions. Enable Lumen screen lock in Accessibility settings. This preserves biometric unlock.") {
                startIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        } else if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            emit(LauncherEvent.Confirm("Enable legacy screen lock",
                "This Android version requires Device Administrator to lock the screen. After each lock, Android requires your PIN or password instead of biometrics. Lumen uses this access only for locking.") {
                startIntent(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lock the screen on your request; PIN/password will be required afterwards."))
            })
        }
    }

    fun setupLegacyScreenLock() {
        val context = getApplication<Application>()
        val admin = ComponentName(context, LockAdminReceiver::class.java)
        emit(LauncherEvent.Confirm("Legacy screen lock", "Device Administrator requires your PIN or password after locking and must be disabled before uninstalling Lumen. Use the accessibility shortcut to preserve biometrics when available.") {
            startIntent(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lock only on your request; PIN/password required afterwards."))
        })
    }

    private fun openWhatsApp(phone: String, message: String) {
        val context = getApplication<Application>()
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) {
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
                if (startIntent(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
            }
            android.widget.Toast.makeText(context, "WhatsApp is not available on this phone.", android.widget.Toast.LENGTH_SHORT).show()
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

enum class Sheet { WidgetTool, SearchDetail, TravelDocs, TripAlbums, None, Search, Drawer, Menu, Settings, AppActions, AppPicker, Voice, Folder, FolderEditor, Capture }

enum class DrawerFilter { Az, MostUsed, Categories }

enum class PermissionGuideKind { DndAccess, ExactAlarm, NotificationListener }

data class PermissionGuide(
    val kind: PermissionGuideKind,
    val title: String,
    val body: String
)

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val dock: List<DockApp> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val launchTimes: Map<String, Long> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val iconSizeDp: Float = 60f,
    val iconGlassStrength: Float = 0.5f,
    val gridColumns: Int = 4,
    val drawerColumns: Int = 4,
    val dockCapacity: Int = 4,
    val showLabels: Boolean = true,
    val iconSkin: IconSkin = IconSkin.MatchSpace,
    val theme: LumenThemeMode = LumenThemeMode.Violet,
    val glassDepth: GlassDepth = GlassDepth.Balanced,
    val smartCluster: Boolean = true,
    val query: String = "",
    val modeSearch: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val searchTrips: List<com.lumen.launcher.travel.model.Trip> = emptyList(),
    val searchTripId: Long? = null,
    val searchTravelCategory: com.lumen.launcher.data.TravelCategory? = null,
    val searchDocumentUri: String? = null,
    val searchDetail: String = "",
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
    val workspaceOpen: Boolean = false,
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
    val tasks: List<String> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val heyLumen: Boolean = false,
    val smartVoice: Boolean = true,
    val voiceLanguage: String = "",
    val focusSuggestionTask: String = "",
    val focusSuggestedKeys: List<String> = emptyList(),
    val focusPeopleActions: List<SearchHit.Action> = emptyList(),
    val geminiApiKey: String = "",
    val aiInboxSummary: Boolean = false,
    val aiContactNames: Boolean = false,
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
    val recentConversations: List<InboxItem> = emptyList(),
    val inboxAccess: Boolean = false,
    val calendarAccess: Boolean = false,
    val missedCalls: List<MissedCall> = emptyList(),
    val callLogAccess: Boolean = false,
    val storedWhatsAppMissed: List<MissedCall> = emptyList(),
    val inferredSpace: SpaceKind = SpaceKind.Home,
    val homePreferencesReady: Boolean = false,
    val homeUsageAllowed: Boolean = true,
    val launchHours: List<SpaceSense.LaunchHour> = emptyList(),
    val modeApps: Map<String, List<String>> = emptyMap(),
    val travelDestination: String = "",
    val travelTicket: String = "",
    val travelAttachments: List<com.lumen.launcher.data.TravelAttachment> = emptyList(),
    val travelStops: List<com.lumen.launcher.data.TripStop> = emptyList(),
    val lastTravelStopId: String? = null,
    val spaceDocks: Map<String, List<String>> = emptyMap(),
    val upNext: UpNext? = null,
    val inboxDigest: String = "",
    val firstName: String? = null,
    val pagerPage: Int = 1,
    val pagerPulse: Int = 0,
    val recentsOpen: Boolean = false,
    val socialCreations: List<CreationItem> = emptyList(),
    val socialCreateTool: SocialCreateTool? = null,
    val widgetTool: String = "",
    val widgetItem: String = "",
    val personalizeFlow: Boolean = false,
    val notes: List<CaptureNote> = emptyList(),
    val later: List<LaterItem> = emptyList(),
    val focusUntil: Long = 0L,
    val focusTaskId: String = "",
    val focusPausedRemainingMs: Long = 0L,
    val focusTotalMs: Long = 0L,
    /** Bumps when a Focus session completes a task so the Task done list refreshes. */
    val focusDonePulse: Int = 0,
    val focusPins: Map<String, List<String>> = emptyMap(),
    /** Animated coach before opening a deep-linked Settings page (DND toggle, etc.). */
    val permissionGuide: PermissionGuide? = null,
    val captureKind: CaptureKind = CaptureKind.Task,
    val duePickerTodoId: String = "",
    val spaceWallpapers: Map<String, String> = emptyMap(),
    val wallpaperPickPulse: Int = 0,
    val notificationBadges: NotificationBadgeMode = NotificationBadgeMode.Number
) {
    override fun toString(): String = "LauncherUiState(space=$activeSpace, page=$pagerPage, apiKey=[redacted])"

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
        get() = folders.filter { it.space == activeSpace.name }.flatMap { it.appKeys }.toSet()

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
        get() = todos.find { it.id == focusTaskId } ?: spaceTodos.filterNot { it.done }.firstOrNull()

    val focusing: Boolean
        get() = focusUntil > System.currentTimeMillis() || focusPausedRemainingMs > 0L

    val focusRunning: Boolean
        get() = focusUntil > System.currentTimeMillis() && focusPausedRemainingMs <= 0L

    val focusPaused: Boolean
        get() = focusPausedRemainingMs > 0L

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
        get() = com.lumen.launcher.data.ModeAppsResolver.resolve(
            activeSpace, visibleApps, modeApps[activeSpace.name], favoriteApps, recentApps,
            com.lumen.launcher.data.AppUsageRanking.rank(visibleApps, launchHours),
            dock.map { it.packageName }.toSet()
        )

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

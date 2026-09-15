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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.lumen.launcher.data.AppCategory
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
import com.lumen.launcher.data.IconCache
import com.lumen.launcher.data.LauncherPreferences
import com.lumen.launcher.data.TouchpadHaptics
import com.lumen.launcher.lock.LockAdminReceiver
import com.lumen.launcher.data.ActionCard
import com.lumen.launcher.data.ContactLookup
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.search.IntentIndex
import com.lumen.launcher.search.LauncherCommand
import com.lumen.launcher.search.LauncherVoice
import com.lumen.launcher.search.PeopleActions
import com.lumen.launcher.search.Routine
import com.lumen.launcher.search.SearchHit
import com.lumen.launcher.search.SearchInterpreter
import com.lumen.launcher.search.VoiceCommands
import com.lumen.launcher.voice.WakePhrase
import com.lumen.launcher.data.NewsItem
import com.lumen.launcher.data.NewsRepository
import com.lumen.launcher.data.NewsTopic
import com.lumen.launcher.data.WeatherRepository
import com.lumen.launcher.data.WeatherSnapshot
import com.lumen.launcher.util.CompetingLaunchers
import com.lumen.launcher.util.HomeRole
import com.lumen.launcher.util.StatusBarController
import com.lumen.launcher.util.TorchController
import kotlinx.coroutines.Dispatchers
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
    var onSpeak: ((String, Boolean) -> Unit)? = null
    var onListen: (() -> Unit)? = null
    var onStopVoice: (() -> Unit)? = null
    var onRequestAssistant: (() -> Unit)? = null
    private var skipVoiceGreeting = false
    private var closeVoiceAfterSpeak = false
    private var promptExactAfterSpeak = false
    private var parkedNews: Set<String> = emptySet()
    private var voiceMisses = 0
    private var deviceEvents: List<CalendarEvent> = emptyList()
    private var noticeEvents: List<CalendarEvent> = emptyList()
    private val torch = TorchController(application)
    val icons = IconCache(application)
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
            InboxHub.items.collect { items ->
                _state.update {
                    it.copy(inbox = items, inboxAccess = InboxHub.hasAccess(getApplication()))
                }
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
            preferences.state.collect { stored ->
                _state.update { current ->
                    current.copy(
                        favorites = stored.favorites,
                        recents = stored.recents,
                        launchTimes = stored.launchTimes,
                        hidden = stored.hidden,
                        iconSizeDp = stored.iconSizeDp,
                        aliases = stored.aliases,
                        spaceOverride = stored.spaceOverride
                            ?.let { runCatching { SpaceKind.valueOf(it) }.getOrNull() }
                            ?.takeUnless { it == SpaceKind.Private },
                        privateApps = stored.privateApps,
                        newsInterests = stored.newsInterests,
                        dockKeys = stored.dockKeys,
                        dock = DockResolver.resolve(getApplication(), current.apps, stored.dockKeys),
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
                        heyLumen = stored.heyLumen,
                        workCalendars = stored.workCalendars,
                        personalCalendars = stored.personalCalendars,
                        alarms = stored.alarms,
                        alarmTone = stored.alarmTone,
                        storedWhatsAppMissed = stored.whatsAppMissed
                    )
                }
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
            val dock = DockResolver.resolve(getApplication(), apps, _state.value.dockKeys)
            _state.update {
                it.copy(
                    apps = apps,
                    dock = dock,
                    isDefaultHome = isDefaultHome(),
                    competingLaunchers = CompetingLaunchers.find(getApplication())
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
        _state.update {
            it.copy(
                inboxAccess = InboxHub.hasAccess(getApplication()),
                calendarAccess = ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED,
                firstName = DeviceFirstName.read(getApplication())
            )
        }
        if (_state.value.newsInterests.isNotEmpty()) loadNews()
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
        onRequestCallLog?.invoke()
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
            it.copy(
                upcomingEvents = merged,
                nextEvent = merged.firstOrNull()
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
        viewModelScope.launch {
            val next = _state.value.tasks.filterNot { it == item }
            preferences.setTasks(next)
            _state.update { it.copy(tasks = next) }
        }
    }

    fun addAlarm(hour: Int, minute: Int, daily: Boolean = false, label: String = ""): LumenAlarm {
        val existing = _state.value.alarms.find { it.hour == hour && it.minute == minute }
        val alarm = (existing ?: LumenAlarm(hour = hour, minute = minute, daily = daily, label = label))
            .copy(enabled = true, daily = daily || (existing?.daily == true), label = label.ifBlank { existing?.label.orEmpty() })
        val next = (_state.value.alarms.filterNot { it.id == alarm.id } + alarm).take(8)
        viewModelScope.launch { preferences.setAlarms(next) }
        _state.update { it.copy(alarms = next) }
        AlarmScheduler.schedule(getApplication(), alarm, _state.value.alarmTone)
        if (!AlarmScheduler.canExact(getApplication())) {
            promptExactAfterSpeak = true
        }
        return alarm
    }

    fun toggleAlarm(id: String) {
        val next = _state.value.alarms.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        viewModelScope.launch { preferences.setAlarms(next) }
        _state.update { it.copy(alarms = next) }
        next.find { it.id == id }?.let { AlarmScheduler.schedule(getApplication(), it, _state.value.alarmTone) }
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
        if (_state.value.sheet == Sheet.Voice) onStopVoice?.invoke()
        _state.update { it.copy(sheet = Sheet.None, query = "", hits = emptyList(), activeApp = null, pickerKind = "", voiceHeard = "", voiceHint = "", voiceListening = false) }
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
        _state.update { it.copy(activeApp = app) }
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
        _state.update {
            it.copy(
                sheet = Sheet.Voice,
                voiceHint = if (fromWake) "I'm listening." else "Hi, I'm Lumen.",
                voiceHeard = "",
                voiceListening = false
            )
        }
        onRequestMic?.invoke()
    }

    fun onWakeWord(remainder: String) {
        voiceMisses = 0
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
                voiceListening = false
            )
        }
        handleVoice(remainder)
    }

    fun setHeyLumen(enabled: Boolean) {
        viewModelScope.launch { preferences.setHeyLumen(enabled) }
        _state.update { it.copy(heyLumen = enabled) }
        if (enabled) onRequestMicQuiet?.invoke()
    }

    fun requestDigitalAssistant() {
        onRequestAssistant?.invoke()
    }

    fun onMicPermission(granted: Boolean) {
        if (_state.value.sheet != Sheet.Voice) return
        if (!granted) {
            skipVoiceGreeting = false
            _state.update { it.copy(voiceHint = "Microphone access is needed so I can hear you.", voiceListening = false) }
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

    fun onVoiceListening() {
        _state.update { it.copy(voiceListening = true, voiceHint = "I'm listening.") }
    }

    fun onVoicePartial(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(voiceHeard = text, voiceListening = true) }
    }

    fun onVoiceResult(text: String) {
        val wake = WakePhrase.detect(text)
        val heard = wake?.remainder ?: text.trim()
        if (wake != null && heard.isBlank()) {
            voiceMisses = 0
            _state.update { it.copy(voiceHint = "I'm listening.", voiceHeard = "", voiceListening = false) }
            onListen?.invoke()
            return
        }
        if (heard.isBlank()) {
            onVoiceNoSpeech()
            return
        }
        voiceMisses = 0
        _state.update { it.copy(voiceHeard = heard, voiceListening = false, voiceHint = "On it.") }
        handleVoice(heard)
    }

    fun onVoiceNoSpeech() {
        if (_state.value.sheet != Sheet.Voice) return
        voiceMisses += 1
        if (voiceMisses >= 3) {
            finishVoice("I'll be here if you need me.", continueTalking = false)
            return
        }
        _state.update { it.copy(voiceListening = false, voiceHint = "I'm listening.", voiceHeard = "") }
        viewModelScope.launch {
            kotlinx.coroutines.delay(80)
            if (_state.value.sheet == Sheet.Voice) onListen?.invoke()
        }
    }

    fun onVoiceFailed() {
        onVoiceNoSpeech()
    }

    private fun handleVoice(text: String) {
        LauncherVoice.parse(text)?.let { command ->
            runLauncherCommand(command)
            return
        }
        VoiceCommands.alarmCommand(text)?.let { command ->
            when (command) {
                is VoiceCommands.AlarmCommand.Set -> {
                    val alarm = addAlarm(command.hour, command.minute, command.daily)
                    val spoken = buildString {
                        append("Alarm for ${formatHour(alarm.hour, alarm.minute)}")
                        if (command.daily) append(", every day")
                        append(".")
                    }
                    if (!AlarmScheduler.canExact(getApplication())) {
                        promptExactAfterSpeak = true
                    }
                    finishVoice(spoken)
                }
                is VoiceCommands.AlarmCommand.Cancel -> {
                    val removed = cancelAlarms(command.hour, command.minute)
                    finishVoice(
                        if (removed == 0) "You don't have an alarm to cancel."
                        else if (removed == 1) "Okay, alarm off."
                        else "Okay, I cancelled $removed alarms."
                    )
                }
                VoiceCommands.AlarmCommand.List -> {
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
            }
            return
        }
        if (VoiceCommands.showTasks(text)) {
            val tasks = _state.value.tasks
            val spoken = if (tasks.isEmpty()) {
                "Your list is empty."
            } else {
                "You have ${tasks.size}. " + tasks.take(6).joinToString(". ")
            }
            finishVoice(spoken)
            return
        }
        VoiceCommands.task(text)?.let { item ->
            val next = (_state.value.tasks + item).distinct()
            viewModelScope.launch { preferences.setTasks(next) }
            _state.update { it.copy(tasks = next) }
            finishVoice("Added. $item.")
            return
        }
        viewModelScope.launch {
            val people = withContext(Dispatchers.IO) { PeopleActions.hits(contacts, text) }
            val person = people.firstOrNull { it.phone.isNotBlank() }
            if (person != null) {
                runHit(person)
                onSpeak?.invoke(person.title, false)
                return@launch
            }
            if (openNamedApp(text)) return@launch
            val hits = SearchInterpreter.interpret(
                query = text,
                apps = _state.value.visibleApps,
                recents = _state.value.recents,
                aliases = _state.value.aliases
            )
            val first = hits.filterIsInstance<SearchHit.App>().firstOrNull { it.score >= 500 }
                ?: hits.firstOrNull { it !is SearchHit.Math && it !is SearchHit.Web && it !is SearchHit.Action }
            if (first is SearchHit.App) {
                launch(first.app)
                onSpeak?.invoke("Opening ${first.app.label}.", false)
            } else {
                finishVoice("I didn't catch that. Say an app name, or say thanks to stop.")
            }
        }
    }

    private fun finishVoice(spoken: String, continueTalking: Boolean = true) {
        closeVoiceAfterSpeak = !continueTalking
        _state.update { it.copy(voiceHint = spoken, voiceListening = false) }
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
        onStopVoice?.invoke()
        _state.update {
            if (it.sheet == Sheet.Voice) {
                it.copy(sheet = Sheet.None, voiceHeard = "", voiceHint = "", voiceListening = false)
            } else {
                it.copy(voiceHeard = "", voiceHint = "", voiceListening = false)
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
                finishVoice("Private Space needs your unlock.", continueTalking = false)
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
            is LauncherCommand.Pin -> voiceHomeApp(command.name, HomeVoice.Pin)
            is LauncherCommand.Unpin -> voiceHomeApp(command.name, HomeVoice.Unpin)
            is LauncherCommand.Dock -> voiceHomeApp(command.name, HomeVoice.Dock)
            is LauncherCommand.Undock -> voiceHomeApp(command.name, HomeVoice.Undock)
            is LauncherCommand.HideApp -> voiceHomeApp(command.name, HomeVoice.Hide)
            is LauncherCommand.UnhideApp -> voiceHomeApp(command.name, HomeVoice.Unhide)
            LauncherCommand.HideNews -> {
                val current = _state.value.newsInterests
                if (current.isNotEmpty()) parkedNews = current
                setNewsInterests(emptySet())
                goToPage(0)
                finishVoice("News is hidden from Flow.")
            }
            LauncherCommand.ShowNews -> {
                val next = parkedNews.ifEmpty { setOf(NewsTopic.Technology.name, NewsTopic.World.name) }
                setNewsInterests(next)
                goToPage(0)
                finishVoice("News is back on Flow.")
            }
            LauncherCommand.NeedNow -> {
                val apps = _state.value.likelyNext.take(4)
                if (apps.isEmpty()) {
                    finishVoice("I don't have a routine for this hour yet.")
                } else {
                    goToPage(0)
                    finishVoice("Right now you usually open " + apps.joinToString(", ") { it.label } + ".")
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
                val event = _state.value.nextEvent
                if (event == null) {
                    goToPage(0)
                    finishVoice("No calendar event on this phone right now. Flow still works without it.")
                } else {
                    goToPage(0)
                    finishVoice("${event.title} is next.")
                }
            }
            LauncherCommand.Help -> finishVoice(
                "I can open Flow, show recents, change Home, pin apps, switch Work or Personal, and tell you what you usually open. What do you need?"
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

    private enum class HomeVoice { Pin, Unpin, Dock, Undock, Hide, Unhide }

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
        _state.update { if (it.recentsOpen == open) it else it.copy(recentsOpen = open) }
    }

    fun closePersonalize() {
        _state.update { it.copy(personalizeFlow = false) }
    }

    private fun voiceHomeApp(name: String, action: HomeVoice) {
        val app = findApp(name, includeHidden = action == HomeVoice.Unhide)
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
                    current.size >= DockResolver.MAX -> finishVoice("The dock is full.")
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
        val name = raw.lowercase()
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .removePrefix("run ")
            .removePrefix("go to ")
            .trim()
        val app = findApp(name, minScore = 360) ?: return false
        launch(app)
        onSpeak?.invoke("Opening ${app.label}.", false)
        return true
    }

    private fun findApp(name: String, includeHidden: Boolean = false, minScore: Int = 500): AppInfo? {
        val pool = if (includeHidden) _state.value.apps else _state.value.visibleApps
        val cleaned = name.removePrefix("the ")
            .removeSuffix(" app")
            .replace("what's app", "whatsapp")
            .replace("whats app", "whatsapp")
            .replace("what sap", "whatsapp")
            .trim()
        if (cleaned.isBlank()) return null
        val compact = FuzzySearch.normalize(cleaned).replace(" ", "")
        pool.find { FuzzySearch.normalize(it.label).replace(" ", "") == compact }?.let { return it }
        if (compact.length >= 4) {
            pool.find { it.packageName.contains(compact, ignoreCase = true) }?.let { return it }
        }
        return pool.maxByOrNull { FuzzySearch.score(cleaned, it.label) }
            ?.takeIf { FuzzySearch.score(cleaned, it.label) >= minScore }
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
            val list = (_state.value.dockKeys ?: _state.value.dock.map { it.key }).toMutableList()
            val from = list.indexOfFirst { it == fromKey }
            val to = list.indexOfFirst { it == toKey }
            if (from < 0 || to < 0) return@launch
            list.removeAt(from)
            list.add(to, fromKey)
            preferences.setDockKeys(list)
        }
    }

    fun toggleDock(app: AppInfo) {
        viewModelScope.launch {
            val current = (_state.value.dockKeys ?: _state.value.dock.map { it.key }).toMutableList()
            if (app.key in current) {
                current.remove(app.key)
            } else if (current.size < DockResolver.MAX) {
                current.add(app.key)
            } else {
                closeSheet()
                return@launch
            }
            preferences.setDockKeys(current)
            dismissAppActions()
        }
    }

    fun removeFromDock(app: DockApp) {
        viewModelScope.launch {
            val current = (_state.value.dockKeys ?: _state.value.dock.map { it.key }).toMutableList()
            current.removeAll { it == app.key || it == app.id }
            preferences.setDockKeys(current)
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
            val q = _state.value.query.trim()
            if (q.isNotEmpty()) preferences.rememberAlias(q, app.key)
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

    fun expandNotifications() {
        StatusBarController.expandNotifications(getApplication())
    }

    fun expandQuickSettings() {
        StatusBarController.expandQuickSettings(getApplication())
    }

    fun openWallpaperPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
        closeSheet()
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

enum class Sheet { None, Search, Drawer, Menu, Settings, AppActions, AppPicker, Voice }

enum class DrawerFilter { Az, MostUsed, Categories }

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val dock: List<DockApp> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val launchTimes: Map<String, Long> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val iconSizeDp: Float = 60f,
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
    val tasks: List<String> = emptyList(),
    val heyLumen: Boolean = true,
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
    val firstName: String? = null,
    val pagerPage: Int = 1,
    val pagerPulse: Int = 0,
    val recentsOpen: Boolean = false,
    val personalizeFlow: Boolean = false
) {
    val selectedNewsTopics: List<NewsTopic>
        get() = NewsTopic.entries.filter { it.name in newsInterests }

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
        get() = spaceOverride ?: SpaceKind.infer(java.time.LocalTime.now().hour)

    val spaceAutomatic: Boolean
        get() = spaceOverride == null

    val likelyNext: List<AppInfo>
        get() = Routine.likelyNext(visibleApps, recents, activeSpace)

    val actionCards: List<ActionCard>
        get() = Routine.actionCards(visibleApps, recents, activeSpace)

    val homeApps: List<AppInfo>
        get() = favoriteApps.ifEmpty { (recentApps + visibleApps).distinctBy { it.key } }.take(24)

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
            DrawerFilter.Az -> visibleApps.groupBy { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
                .toSortedMap()
                .map { it.key to it.value }
            DrawerFilter.MostUsed -> listOf("Recently used" to recentApps) +
                listOf("All apps" to visibleApps.filterNot { app -> recentApps.any { it.key == app.key } })
            DrawerFilter.Categories -> visibleApps.groupBy { it.category.label }
                .toSortedMap()
                .map { it.key to it.value }
        }
}

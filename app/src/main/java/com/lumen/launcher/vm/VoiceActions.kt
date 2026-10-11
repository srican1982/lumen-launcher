package com.lumen.launcher.vm

import android.content.Intent
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.CalendarEvent
import com.lumen.launcher.inbox.InboxItem
import com.lumen.launcher.alarm.LumenAlarm
import com.lumen.launcher.data.DockApp
import com.lumen.launcher.data.HomeFolder
import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.data.DailyReview
import com.lumen.launcher.data.NeedNowHint
import com.lumen.launcher.data.TodoItem
import com.lumen.launcher.data.UpNext
import com.lumen.launcher.data.WeatherSnapshot
import com.lumen.launcher.flow.FlowModule

data class VoiceHomeSnapshot(
    val activeSpace: SpaceKind,
    val aiContactNames: Boolean,
    val aiInboxSummary: Boolean,
    val aliases: Map<String, String>,
    val apps: List<AppInfo>,
    val calendarAccess: Boolean,
    val dailyReview: DailyReview,
    val dock: List<DockApp>,
    val dockCapacity: Int,
    val dockKeys: List<String>?,
    val favorites: List<String>,
    val focusTask: TodoItem?,
    val folders: List<HomeFolder>,
    val geminiApiKey: String,
    val hidden: Set<String>,
    val iconSizeDp: Float,
    val inbox: List<InboxItem>,
    val inboxDigest: String,
    val launchTimes: Map<String, Long>,
    val needNowHints: List<NeedNowHint>,
    val newsInterests: Set<String>,
    val nextEvent: CalendarEvent?,
    val privateApps: Set<String>,
    val privateUnlocked: Boolean,
    val sheet: Sheet,
    val smartVoice: Boolean,
    val spaceTodos: List<TodoItem>,
    val upNext: UpNext?,
    val upcomingEvents: List<CalendarEvent>,
    val visibleApps: List<AppInfo>,
    val weather: WeatherSnapshot?
) {
    override fun toString() = "VoiceHomeSnapshot(apiKey=[redacted])"
}

/** Narrow feature ports. The owning ViewModels retain persistence and UI side effects. */
interface HomeActions {
    fun snapshot(): VoiceHomeSnapshot
    fun emit(event: LauncherEvent)
    fun setSheet(sheet: Sheet)
    fun resolveApp(name: String, includeHidden: Boolean = false, includePrivate: Boolean = snapshot().privateUnlocked, minScore: Int = 500, voice: Boolean = false): AppInfo?
    fun launchApp(app: AppInfo): Boolean
    fun startIntent(intent: Intent): Boolean
    fun goToPage(page: Int, recents: Boolean = false)
    fun openDrawer()
    fun openSearch()
    fun openSettings()
    fun openPrivateSpace()
    fun openFolderCreator()
    fun selectSpace(space: SpaceKind?)
    fun setIconSize(size: Float)
    fun setShowLabels(show: Boolean)
    fun setGridColumns(columns: Int)
    fun setDockCapacity(count: Int)
    fun setFolders(folders: List<HomeFolder>)
    fun rememberAlias(alias: String, key: String)
    fun setPrivateApps(keys: Set<String>)
    fun lockPrivate()
    fun setFavorites(keys: List<String>)
    fun setDockKeys(keys: List<String>)
    fun setHidden(packages: Set<String>)
    fun setNewsInterests(topics: Set<String>)
    fun setFlowModule(module: FlowModule, enabled: Boolean)
    fun moveFlowModuleTo(module: FlowModule, before: FlowModule?)
    fun refreshWeather()
    fun personalizeFlow()
    fun showPurposeResults(title: String, apps: List<AppInfo>)
    fun authenticate(success: () -> Unit, failure: () -> Unit)
}
interface TaskActions {
    fun addTodo(text: String, due: Long, repeat: String, priority: Boolean)
    fun addNote(text: String)
    fun saveForLater()
    fun startFocus(minutes: Int)
    fun endFocus()
    fun matchTodo(text: String, includeDone: Boolean = true): TodoItem?
    fun toggleTodo(id: String, done: Boolean)
    fun deleteTodo(id: String)
}
interface AlarmActions {
    fun addAlarm(hour: Int, minute: Int, daily: Boolean = false): LumenAlarm
    fun addAt(hour: Int, minute: Int, deadline: Long): LumenAlarm
    fun addTimerSeconds(seconds: Int): LumenAlarm
    fun cancelAlarms(hour: Int?, minute: Int?): Int
    fun upcomingAlarms(): List<LumenAlarm>
    fun ensureNotifications()
}

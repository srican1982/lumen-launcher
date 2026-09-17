package com.lumen.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumen.launcher.alarm.AlarmTones
import com.lumen.launcher.alarm.LumenAlarm
import com.lumen.launcher.flow.defaultFlowEnabled
import com.lumen.launcher.flow.defaultFlowNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherStore by preferencesDataStore("lumen_launcher")

class LauncherPreferences(private val context: Context) {

    val state: Flow<LauncherState> = context.launcherStore.data.map { prefs ->
        LauncherState(
            favorites = decodeList(prefs[FAVORITES].orEmpty()),
            recents = decodeList(prefs[RECENTS].orEmpty()),
            launchTimes = decodeLaunchTimes(prefs[LAUNCH_TIMES].orEmpty()),
            hidden = prefs[HIDDEN].orEmpty(),
            iconSizeDp = prefs[ICON_SIZE] ?: 60f,
            gridColumns = (prefs[GRID_COLUMNS] ?: 4).coerceIn(3, 6),
            drawerColumns = (prefs[DRAWER_COLUMNS] ?: 4).coerceIn(3, 6),
            dockCapacity = (prefs[DOCK_CAPACITY] ?: 4).coerceIn(3, 6),
            showLabels = prefs[SHOW_LABELS] ?: true,
            aliases = decodeAliases(prefs[ALIASES].orEmpty()),
            spaceOverride = prefs[SPACE]?.takeIf { it.isNotBlank() },
            privateApps = prefs[PRIVATE].orEmpty(),
            newsInterests = prefs[NEWS_INTERESTS].orEmpty(),
            dockKeys = if (prefs.contains(DOCK)) decodeList(prefs[DOCK].orEmpty()) else null,
            tapAction = prefs[TAP_ACTION] ?: GestureAction.VOICE,
            doubleAction = prefs[DOUBLE_ACTION].orEmpty(),
            tripleAction = prefs[TRIPLE_ACTION].orEmpty(),
            longAction = prefs[LONG_ACTION] ?: GestureAction.PRIVATE,
            swipeUpAction = prefs[SWIPE_UP].orEmpty(),
            swipeDownAction = prefs[SWIPE_DOWN].orEmpty(),
            swipeLeftAction = prefs[SWIPE_LEFT].orEmpty(),
            swipeRightAction = prefs[SWIPE_RIGHT].orEmpty(),
            touchpadHaptics = prefs[HAPTICS] ?: TouchpadHaptics.STANDARD,
            tasks = decodeList(prefs[TASKS].orEmpty()),
            heyLumen = prefs[HEY_LUMEN] ?: false,
            smartVoice = prefs[SMART_VOICE] ?: true,
            geminiApiKey = prefs[GEMINI_API_KEY].orEmpty(),
            flowEnabled = if (prefs.contains(FLOW_ENABLED)) prefs[FLOW_ENABLED].orEmpty() else defaultFlowEnabled(),
            flowOrder = if (prefs.contains(FLOW_ORDER)) decodeList(prefs[FLOW_ORDER].orEmpty()) else defaultFlowNames(),
            folders = decodeFolders(prefs[FOLDERS].orEmpty()),
            workCalendars = prefs[WORK_CALENDARS].orEmpty(),
            personalCalendars = prefs[PERSONAL_CALENDARS].orEmpty(),
            alarms = decodeAlarms(prefs[ALARMS].orEmpty()),
            alarmTone = prefs[ALARM_TONE] ?: AlarmTones.AURA,
            whatsAppMissed = decodeWhatsAppMissed(prefs[WHATSAPP_MISSED].orEmpty())
        )
    }

    suspend fun setFavorites(keys: List<String>) {
        context.launcherStore.edit { it[FAVORITES] = encodeList(keys.distinct()) }
    }

    suspend fun setHidden(packages: Set<String>) {
        context.launcherStore.edit { it[HIDDEN] = packages }
    }

    suspend fun setIconSize(sizeDp: Float) {
        context.launcherStore.edit { it[ICON_SIZE] = sizeDp.coerceIn(44f, 76f) }
    }

    suspend fun setGridColumns(columns: Int) {
        context.launcherStore.edit { it[GRID_COLUMNS] = columns.coerceIn(3, 6) }
    }

    suspend fun setDrawerColumns(columns: Int) {
        context.launcherStore.edit { it[DRAWER_COLUMNS] = columns.coerceIn(3, 6) }
    }

    suspend fun setDockCapacity(count: Int) {
        context.launcherStore.edit { prefs ->
            val cap = count.coerceIn(3, 6)
            prefs[DOCK_CAPACITY] = cap
            if (prefs.contains(DOCK)) {
                prefs[DOCK] = encodeList(decodeList(prefs[DOCK].orEmpty()).take(cap))
            }
        }
    }

    suspend fun setShowLabels(show: Boolean) {
        context.launcherStore.edit { it[SHOW_LABELS] = show }
    }

    suspend fun setFolders(items: List<HomeFolder>) {
        context.launcherStore.edit { it[FOLDERS] = encodeFolders(items.take(24)) }
    }

    suspend fun recordLaunch(key: String) {
        context.launcherStore.edit { prefs ->
            val next = (listOf(key) + decodeList(prefs[RECENTS].orEmpty()))
                .distinct()
                .take(16)
            prefs[RECENTS] = encodeList(next)
            val times = decodeLaunchTimes(prefs[LAUNCH_TIMES].orEmpty()).toMutableMap()
            times[key] = System.currentTimeMillis()
            prefs[LAUNCH_TIMES] = encodeLaunchTimes(times)
        }
    }

    suspend fun rememberAlias(query: String, appKey: String) {
        val normalized = query.trim().lowercase()
        if (normalized.length !in 2..8) return
        context.launcherStore.edit { prefs ->
            val map = decodeAliases(prefs[ALIASES].orEmpty()).toMutableMap()
            map[normalized] = appKey
            prefs[ALIASES] = encodeAliases(map.entries.take(40).associate { it.toPair() })
        }
    }

    suspend fun setPrivateApps(keys: Set<String>) {
        context.launcherStore.edit { it[PRIVATE] = keys }
    }

    suspend fun setNewsInterests(topics: Set<String>) {
        context.launcherStore.edit { it[NEWS_INTERESTS] = topics }
    }

    suspend fun setDockKeys(keys: List<String>) {
        context.launcherStore.edit { prefs ->
            val cap = (prefs[DOCK_CAPACITY] ?: 4).coerceIn(3, 6)
            prefs[DOCK] = encodeList(keys.distinct().take(cap))
        }
    }

    suspend fun setTapAction(value: String) {
        context.launcherStore.edit { it[TAP_ACTION] = value }
    }

    suspend fun setDoubleAction(value: String) {
        context.launcherStore.edit { it[DOUBLE_ACTION] = value }
    }

    suspend fun setTripleAction(value: String) {
        context.launcherStore.edit { it[TRIPLE_ACTION] = value }
    }

    suspend fun setLongAction(value: String) {
        context.launcherStore.edit { it[LONG_ACTION] = value }
    }

    suspend fun setSwipeUpAction(value: String) {
        context.launcherStore.edit { it[SWIPE_UP] = value }
    }

    suspend fun setSwipeDownAction(value: String) {
        context.launcherStore.edit { it[SWIPE_DOWN] = value }
    }

    suspend fun setSwipeLeftAction(value: String) {
        context.launcherStore.edit { it[SWIPE_LEFT] = value }
    }

    suspend fun setSwipeRightAction(value: String) {
        context.launcherStore.edit { it[SWIPE_RIGHT] = value }
    }

    suspend fun setTouchpadHaptics(value: String) {
        context.launcherStore.edit { it[HAPTICS] = value }
    }

    suspend fun setTasks(items: List<String>) {
        context.launcherStore.edit { it[TASKS] = encodeList(items.distinct().take(40)) }
    }

    suspend fun setHeyLumen(enabled: Boolean) {
        context.launcherStore.edit { it[HEY_LUMEN] = enabled }
    }

    suspend fun setSmartVoice(enabled: Boolean) {
        context.launcherStore.edit { it[SMART_VOICE] = enabled }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.launcherStore.edit { it[GEMINI_API_KEY] = key.trim() }
    }

    suspend fun setFlowEnabled(names: Set<String>) {
        context.launcherStore.edit { it[FLOW_ENABLED] = names }
    }

    suspend fun setFlowOrder(names: List<String>) {
        context.launcherStore.edit { it[FLOW_ORDER] = encodeList(names.distinct()) }
    }

    suspend fun setCalendarRoles(work: Set<String>, personal: Set<String>) {
        context.launcherStore.edit {
            it[WORK_CALENDARS] = work
            it[PERSONAL_CALENDARS] = personal
        }
    }

    suspend fun setAlarms(items: List<LumenAlarm>) {
        context.launcherStore.edit { it[ALARMS] = encodeAlarms(items.take(8)) }
    }

    suspend fun setAlarmTone(tone: String) {
        context.launcherStore.edit { it[ALARM_TONE] = tone }
    }

    suspend fun setWhatsAppMissed(items: List<MissedCall>) {
        context.launcherStore.edit { it[WHATSAPP_MISSED] = encodeWhatsAppMissed(items.take(12)) }
    }

    suspend fun setSpaceOverride(name: String?) {
        context.launcherStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(SPACE) else prefs[SPACE] = name
        }
    }

    private fun encodeFolders(items: List<HomeFolder>): String =
        items.joinToString("\u001e") { folder ->
            listOf(
                folder.id,
                folder.name.replace('\u001f', ' ').replace('\u001e', ' '),
                folder.appKeys.joinToString(",")
            ).joinToString("\u001f")
        }

    private fun decodeFolders(raw: String): List<HomeFolder> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size < 2) return@mapNotNull null
            val id = parts[0].ifBlank { return@mapNotNull null }
            val name = parts[1].ifBlank { "Folder" }
            val keys = parts.getOrElse(2) { "" }.split(',').map { it.trim() }.filter { it.isNotBlank() }
            HomeFolder(id, name, keys)
        }
    }

    private fun encodeList(values: List<String>): String = values.joinToString("\u001f")

    private fun decodeList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001f').filter { it.isNotBlank() }
    }

    private fun encodeLaunchTimes(map: Map<String, Long>): String =
        map.entries.joinToString("\u001e") { "${it.key}\u001f${it.value}" }

    private fun decodeLaunchTimes(raw: String): Map<String, Long> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            val time = parts.getOrNull(1)?.toLongOrNull()
            if (parts.size == 2 && time != null) parts[0] to time else null
        }.toMap()
    }

    private fun encodeAliases(map: Map<String, String>): String =
        map.entries.joinToString("\u001e") { "${it.key}\u001f${it.value}" }

    private fun decodeAliases(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    private fun encodeAlarms(items: List<LumenAlarm>): String =
        items.joinToString("\u001e") { alarm ->
            listOf(
                alarm.id,
                alarm.hour.toString(),
                alarm.minute.toString(),
                if (alarm.enabled) "1" else "0",
                if (alarm.daily) "1" else "0",
                alarm.label.replace('\u001f', ' ').replace('\u001e', ' ')
            ).joinToString("\u001f")
        }

    private fun decodeAlarms(raw: String): List<LumenAlarm> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size < 5) return@mapNotNull null
            val hour = parts[1].toIntOrNull() ?: return@mapNotNull null
            val minute = parts[2].toIntOrNull() ?: return@mapNotNull null
            LumenAlarm(
                id = parts[0].ifBlank { return@mapNotNull null },
                hour = hour,
                minute = minute,
                enabled = parts[3] == "1",
                daily = parts[4] == "1",
                label = parts.getOrElse(5) { "" }
            )
        }
    }

    private fun encodeWhatsAppMissed(items: List<MissedCall>): String =
        items.joinToString("\u001e") { call ->
            listOf(
                call.name.replace('\u001f', ' ').replace('\u001e', ' '),
                call.number.replace('\u001f', ' ').replace('\u001e', ' '),
                call.at.toString(),
                call.count.toString()
            ).joinToString("\u001f")
        }

    private fun decodeWhatsAppMissed(raw: String): List<MissedCall> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size < 3) return@mapNotNull null
            val at = parts[2].toLongOrNull() ?: return@mapNotNull null
            MissedCall(
                number = parts.getOrElse(1) { "" },
                name = parts[0],
                at = at,
                count = parts.getOrElse(3) { "1" }.toIntOrNull() ?: 1,
                whatsapp = true
            )
        }
    }

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val RECENTS = stringPreferencesKey("recents")
        val LAUNCH_TIMES = stringPreferencesKey("launch_times")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val ICON_SIZE = floatPreferencesKey("icon_size")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val DOCK_CAPACITY = intPreferencesKey("dock_capacity")
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val FOLDERS = stringPreferencesKey("home_folders")
        val ALIASES = stringPreferencesKey("aliases")
        val SPACE = stringPreferencesKey("space_override")
        val PRIVATE = stringSetPreferencesKey("private_apps")
        val NEWS_INTERESTS = stringSetPreferencesKey("news_interests")
        val DOCK = stringPreferencesKey("dock_keys")
        val TAP_ACTION = stringPreferencesKey("tap_action")
        val DOUBLE_ACTION = stringPreferencesKey("double_action")
        val TRIPLE_ACTION = stringPreferencesKey("triple_action")
        val LONG_ACTION = stringPreferencesKey("long_action")
        val SWIPE_UP = stringPreferencesKey("swipe_up_action")
        val SWIPE_DOWN = stringPreferencesKey("swipe_down_action")
        val SWIPE_LEFT = stringPreferencesKey("swipe_left_action")
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right_action")
        val HAPTICS = stringPreferencesKey("touchpad_haptics")
        val TASKS = stringPreferencesKey("lumen_tasks")
        val HEY_LUMEN = booleanPreferencesKey("hey_lumen")
        val SMART_VOICE = booleanPreferencesKey("smart_voice")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val FLOW_ENABLED = stringSetPreferencesKey("flow_enabled")
        val FLOW_ORDER = stringPreferencesKey("flow_order")
        val WORK_CALENDARS = stringSetPreferencesKey("work_calendars")
        val PERSONAL_CALENDARS = stringSetPreferencesKey("personal_calendars")
        val ALARMS = stringPreferencesKey("lumen_alarms")
        val ALARM_TONE = stringPreferencesKey("alarm_tone")
        val WHATSAPP_MISSED = stringPreferencesKey("whatsapp_missed")
    }
}

data class LauncherState(
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val launchTimes: Map<String, Long> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val iconSizeDp: Float = 60f,
    val gridColumns: Int = 4,
    val drawerColumns: Int = 4,
    val dockCapacity: Int = 4,
    val showLabels: Boolean = true,
    val aliases: Map<String, String> = emptyMap(),
    val spaceOverride: String? = null,
    val privateApps: Set<String> = emptySet(),
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
    val touchpadHaptics: String = TouchpadHaptics.STANDARD,
    val tasks: List<String> = emptyList(),
    val heyLumen: Boolean = false,
    val smartVoice: Boolean = true,
    val geminiApiKey: String = "",
    val flowEnabled: Set<String> = defaultFlowEnabled(),
    val flowOrder: List<String> = defaultFlowNames(),
    val folders: List<HomeFolder> = emptyList(),
    val workCalendars: Set<String> = emptySet(),
    val personalCalendars: Set<String> = emptySet(),
    val alarms: List<LumenAlarm> = emptyList(),
    val alarmTone: String = AlarmTones.AURA,
    val whatsAppMissed: List<MissedCall> = emptyList()
)

object TouchpadHaptics {
    const val OFF = "off"
    const val LIGHT = "light"
    const val STANDARD = "standard"

    fun label(value: String): String = when (value) {
        OFF -> "Off"
        LIGHT -> "Light"
        else -> "Standard"
    }

    fun next(value: String): String = when (value) {
        OFF -> LIGHT
        LIGHT -> STANDARD
        else -> OFF
    }
}

object GestureAction {
    const val NONE = ""
    const val OFF = "action:none"
    const val LOCK = "action:lock"
    const val DRAWER = "action:drawer"
    const val SEARCH = "action:search"
    const val VOICE = "action:voice"
    const val PRIVATE = "action:private"
    const val NOTIFICATIONS = "action:notifications"
    const val QUICK_SETTINGS = "action:qs"

    fun app(key: String) = "app:$key"
    fun isApp(value: String) = value.startsWith("app:")
    fun appKey(value: String) = value.removePrefix("app:")
    fun isUnassigned(value: String) = value.isEmpty()
    fun isOff(value: String) = value == NONE || value == OFF

    fun label(value: String, apps: List<AppInfo>): String = when (value) {
        NONE -> "Choose…"
        OFF -> "Not set"
        LOCK -> "Lock screen"
        DRAWER -> "App drawer"
        SEARCH -> "Search"
        VOICE -> "Lumen Voice"
        PRIVATE -> "Locked Space"
        NOTIFICATIONS -> "Notifications"
        QUICK_SETTINGS -> "Quick Settings"
        else -> apps.find { it.key == appKey(value) }?.label ?: "App"
    }

    fun pickerTitle(kind: String): String = when (kind) {
        "double" -> "Double tap"
        "triple" -> "Triple tap"
        "long" -> "Long press"
        "swipeUp" -> "Swipe up"
        "swipeDown" -> "Swipe down"
        "swipeLeft" -> "Swipe left"
        "swipeRight" -> "Swipe right"
        else -> "Tap"
    }
}

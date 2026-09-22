package com.lumen.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
            iconSkin = prefs[ICON_SKIN].orEmpty(),
            glassDepth = prefs[GLASS_DEPTH].orEmpty(),
            smartCluster = prefs[SMART_CLUSTER] ?: true,
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
            todos = decodeTodos(prefs[TODOS].orEmpty()).ifEmpty {
                decodeList(prefs[TASKS].orEmpty()).map { text ->
                    TodoItem(id = text, text = text, done = false, space = SpaceKind.Home)
                }
            },
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
            whatsAppMissed = decodeWhatsAppMissed(prefs[WHATSAPP_MISSED].orEmpty()),
            launchHours = decodeList(prefs[LAUNCH_HOURS].orEmpty())
                .mapNotNull { SpaceSense.parseLaunchHour(it) },
            spaceDocks = decodeSpaceDocks(prefs[SPACE_DOCKS].orEmpty()),
            aliasHints = decodeAliasHints(prefs[ALIAS_HINTS].orEmpty()),
            notes = decodeNotes(prefs[NOTES].orEmpty()),
            later = decodeLater(prefs[LATER].orEmpty()),
            focusUntil = prefs[FOCUS_UNTIL] ?: 0L,
            focusTaskId = prefs[FOCUS_TASK].orEmpty(),
            focusPins = decodeSpaceDocks(prefs[FOCUS_PINS].orEmpty()),
            spaceWallpapers = decodeAliases(prefs[SPACE_WALLS].orEmpty())
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
            if (prefs.contains(SPACE_DOCKS)) {
                val docks = decodeSpaceDocks(prefs[SPACE_DOCKS].orEmpty()).mapValues { it.value.take(cap) }
                prefs[SPACE_DOCKS] = encodeSpaceDocks(docks)
            }
        }
    }

    suspend fun setShowLabels(show: Boolean) {
        context.launcherStore.edit { it[SHOW_LABELS] = show }
    }

    suspend fun setIconSkin(skin: String) {
        context.launcherStore.edit { it[ICON_SKIN] = skin }
    }

    suspend fun setGlassDepth(depth: String) {
        context.launcherStore.edit { it[GLASS_DEPTH] = depth }
    }

    suspend fun setSmartCluster(enabled: Boolean) {
        context.launcherStore.edit { it[SMART_CLUSTER] = enabled }
    }

    suspend fun setFolders(items: List<HomeFolder>) {
        context.launcherStore.edit { it[FOLDERS] = encodeFolders(items.take(24)) }
    }

    suspend fun recordLaunch(key: String, hour: Int = java.time.LocalTime.now().hour) {
        context.launcherStore.edit { prefs ->
            val next = (listOf(key) + decodeList(prefs[RECENTS].orEmpty()))
                .distinct()
                .take(16)
            prefs[RECENTS] = encodeList(next)
            val times = decodeLaunchTimes(prefs[LAUNCH_TIMES].orEmpty()).toMutableMap()
            times[key] = System.currentTimeMillis()
            prefs[LAUNCH_TIMES] = encodeLaunchTimes(times)
            val stamp = "$key@${hour.coerceIn(0, 23)}"
            val hours = (listOf(stamp) + decodeList(prefs[LAUNCH_HOURS].orEmpty())).take(240)
            prefs[LAUNCH_HOURS] = encodeList(hours)
        }
    }

    suspend fun rememberAlias(query: String, appKey: String) {
        val normalized = com.lumen.launcher.search.AliasHints.aliasKey(query)
        if (normalized.length !in 2..40) return
        if (normalized.split(' ').size > 6) return
        context.launcherStore.edit { prefs ->
            val map = decodeAliases(prefs[ALIASES].orEmpty()).toMutableMap()
            map[normalized] = appKey
            prefs[ALIASES] = encodeAliases(map.entries.take(80).associate { it.toPair() })
        }
    }

    suspend fun noteSpokenOpen(spoken: String, appKey: String, label: String) {
        if (!com.lumen.launcher.search.AliasHints.shouldTrack(spoken, label)) return
        val key = com.lumen.launcher.search.AliasHints.aliasKey(spoken)
        context.launcherStore.edit { prefs ->
            val hints = decodeAliasHints(prefs[ALIAS_HINTS].orEmpty()).toMutableMap()
            val next = (hints[key] ?: 0) + 1
            hints[key] = next
            prefs[ALIAS_HINTS] = encodeAliasHints(hints.entries.take(80).associate { it.toPair() })
            if (com.lumen.launcher.search.AliasHints.shouldPromote(next)) {
                val aliases = decodeAliases(prefs[ALIASES].orEmpty()).toMutableMap()
                aliases[key] = appKey
                prefs[ALIASES] = encodeAliases(aliases.entries.take(80).associate { it.toPair() })
            }
        }
    }

    suspend fun setPrivateApps(keys: Set<String>) {
        context.launcherStore.edit { it[PRIVATE] = keys }
    }

    suspend fun setNewsInterests(topics: Set<String>) {
        context.launcherStore.edit { it[NEWS_INTERESTS] = topics }
    }

    suspend fun setDockKeys(keys: List<String>, spaceName: String? = null) {
        context.launcherStore.edit { prefs ->
            val cap = (prefs[DOCK_CAPACITY] ?: 4).coerceIn(3, 6)
            val trimmed = keys.distinct().take(cap)
            prefs[DOCK] = encodeList(trimmed)
            if (!spaceName.isNullOrBlank()) {
                val docks = decodeSpaceDocks(prefs[SPACE_DOCKS].orEmpty()).toMutableMap()
                docks[spaceName] = trimmed
                prefs[SPACE_DOCKS] = encodeSpaceDocks(docks)
            }
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

    suspend fun setTodos(items: List<TodoItem>) {
        context.launcherStore.edit { it[TODOS] = encodeTodos(items.take(80)) }
    }

    suspend fun setNotes(items: List<CaptureNote>) {
        context.launcherStore.edit { it[NOTES] = encodeNotes(items.take(40)) }
    }

    suspend fun setLater(items: List<LaterItem>) {
        context.launcherStore.edit { it[LATER] = encodeLater(items.take(24)) }
    }

    suspend fun setFocus(until: Long, taskId: String) {
        context.launcherStore.edit {
            it[FOCUS_UNTIL] = until
            it[FOCUS_TASK] = taskId
        }
    }

    suspend fun setFocusPins(pins: Map<String, List<String>>) {
        context.launcherStore.edit {
            it[FOCUS_PINS] = encodeSpaceDocks(pins.mapValues { entry -> entry.value.distinct().take(4) })
        }
    }

    suspend fun setSpaceWallpapers(paths: Map<String, String>) {
        context.launcherStore.edit { it[SPACE_WALLS] = encodeAliases(paths) }
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

    private fun encodeNotes(items: List<CaptureNote>): String =
        items.joinToString("\u001e") { item ->
            listOf(
                item.id.replace('\u001f', ' '),
                item.text.replace('\u001f', ' ').replace('\u001e', ' '),
                item.space.name,
                item.createdAt.toString()
            ).joinToString("\u001f")
        }

    private fun decodeNotes(raw: String): List<CaptureNote> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            val text = parts.getOrNull(1)?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val space = runCatching { SpaceKind.valueOf(parts.getOrElse(2) { SpaceKind.Home.name }) }
                .getOrDefault(SpaceKind.Home)
            CaptureNote(
                id = parts.getOrNull(0).orEmpty().ifBlank { text },
                text = text,
                space = space.takeUnless { it == SpaceKind.Private } ?: SpaceKind.Home,
                createdAt = parts.getOrNull(3)?.toLongOrNull() ?: 0L
            )
        }
    }

    private fun encodeLater(items: List<LaterItem>): String =
        items.joinToString("\u001e") { item ->
            listOf(
                item.id.replace('\u001f', ' '),
                item.text.replace('\u001f', ' ').replace('\u001e', ' '),
                item.kind,
                item.appKey,
                item.createdAt.toString()
            ).joinToString("\u001f")
        }

    private fun decodeLater(raw: String): List<LaterItem> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            val text = parts.getOrNull(1)?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null
            LaterItem(
                id = parts.getOrNull(0).orEmpty().ifBlank { text },
                text = text,
                kind = parts.getOrNull(2).orEmpty().ifBlank { "note" },
                appKey = parts.getOrNull(3).orEmpty(),
                createdAt = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            )
        }
    }

    private fun encodeTodos(items: List<TodoItem>): String =
        items.joinToString("\u001e") { item ->
            listOf(
                item.id.replace('\u001f', ' ').replace('\u001e', ' '),
                item.text.replace('\u001f', ' ').replace('\u001e', ' '),
                if (item.done) "1" else "0",
                item.space.name,
                item.createdAt.toString(),
                item.dueAt?.toString().orEmpty(),
                if (item.priority) "1" else "0",
                item.repeat
            ).joinToString("\u001f")
        }

    private fun decodeTodos(raw: String): List<TodoItem> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size < 2) return@mapNotNull null
            val text = parts[1].trim()
            if (text.isBlank()) return@mapNotNull null
            val space = runCatching { SpaceKind.valueOf(parts.getOrElse(3) { SpaceKind.Home.name }) }
                .getOrDefault(SpaceKind.Home)
                .takeUnless { it == SpaceKind.Private } ?: SpaceKind.Home
            TodoItem(
                id = parts[0].ifBlank { text },
                text = text,
                done = parts.getOrElse(2) { "0" } == "1",
                space = space,
                createdAt = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
                dueAt = parts.getOrNull(5)?.toLongOrNull(),
                priority = parts.getOrNull(6) == "1",
                repeat = parts.getOrNull(7).orEmpty()
            )
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

    private fun encodeSpaceDocks(map: Map<String, List<String>>): String =
        map.entries.joinToString("|") { "${it.key}=${it.value.joinToString(",")}" }

    private fun decodeSpaceDocks(raw: String): Map<String, List<String>> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('|').mapNotNull { line ->
            val at = line.indexOf('=')
            if (at <= 0) return@mapNotNull null
            val name = line.substring(0, at).trim()
            if (name.isBlank()) return@mapNotNull null
            val keys = line.substring(at + 1).split(',').map { it.trim() }.filter { it.isNotBlank() }
            name to keys
        }.toMap()
    }

    private fun encodeAliasHints(map: Map<String, Int>): String =
        map.entries.joinToString("\u001e") { "${it.key}\u001f${it.value}" }

    private fun decodeAliasHints(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('\u001e').mapNotNull { line ->
            val parts = line.split('\u001f')
            val count = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && count != null) parts[0] to count else null
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
        val ICON_SKIN = stringPreferencesKey("icon_skin")
        val GLASS_DEPTH = stringPreferencesKey("glass_depth")
        val SMART_CLUSTER = booleanPreferencesKey("smart_cluster")
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
        val TODOS = stringPreferencesKey("lumen_todos")
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
        val LAUNCH_HOURS = stringPreferencesKey("launch_hours")
        val SPACE_DOCKS = stringPreferencesKey("space_docks")
        val ALIAS_HINTS = stringPreferencesKey("alias_hints")
        val NOTES = stringPreferencesKey("lumen_notes")
        val LATER = stringPreferencesKey("lumen_later")
        val FOCUS_UNTIL = longPreferencesKey("focus_until")
        val FOCUS_TASK = stringPreferencesKey("focus_task")
        val FOCUS_PINS = stringPreferencesKey("focus_pins")
        val SPACE_WALLS = stringPreferencesKey("space_wallpapers")
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
    val iconSkin: String = IconSkin.MatchSpace.name,
    val glassDepth: String = GlassDepth.Balanced.name,
    val smartCluster: Boolean = true,
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
    val todos: List<TodoItem> = emptyList(),
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
    val whatsAppMissed: List<MissedCall> = emptyList(),
    val launchHours: List<SpaceSense.LaunchHour> = emptyList(),
    val spaceDocks: Map<String, List<String>> = emptyMap(),
    val aliasHints: Map<String, Int> = emptyMap(),
    val notes: List<CaptureNote> = emptyList(),
    val later: List<LaterItem> = emptyList(),
    val focusUntil: Long = 0L,
    val focusTaskId: String = "",
    val focusPins: Map<String, List<String>> = emptyMap(),
    val spaceWallpapers: Map<String, String> = emptyMap()
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

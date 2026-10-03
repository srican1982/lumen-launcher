package com.lumen.launcher.focus

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists who can reach you during Focus, policy toggles, and light group presets.
 * Not wired through LauncherViewModel DataStore — Focus owns its people store.
 */
class FocusAllowedPeopleRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _people = MutableStateFlow(loadPeople())
    val people: StateFlow<List<FocusPerson>> = _people.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<FocusPolicySettings> = _settings.asStateFlow()

    private val _groups = MutableStateFlow(loadGroups())
    val groups: StateFlow<List<FocusPeopleGroup>> = _groups.asStateFlow()

    private val _listsRevision = MutableStateFlow(0)
    val listsRevision: StateFlow<Int> = _listsRevision.asStateFlow()

    private val _selectedGroups = MutableStateFlow(prefs.getStringSet("selected_groups", emptySet()).orEmpty().toSet())
    val selectedGroups: StateFlow<Set<String>> = _selectedGroups.asStateFlow()

    init {
        if (!prefs.contains("selected_groups")) {
            if (_people.value.isNotEmpty()) {
                saveList("My people", _people.value, "my_people")
                selectGroup("my_people", true)
            } else prefs.edit().putStringSet("selected_groups", emptySet()).apply()
        }
    }

    fun selectGroup(id: String, selected: Boolean) {
        _selectedGroups.value = if (selected) _selectedGroups.value + id else _selectedGroups.value - id
        prefs.edit().putStringSet("selected_groups", _selectedGroups.value).apply()
        syncSelectedPeople()
    }

    private fun syncSelectedPeople() {
        val members = combineCallGroups(_selectedGroups.value.map { peopleForGroup(it) })
        setPeople(members.map { it.copy(reach = FocusReach.CallsOnly) })
    }

    fun peopleNow(): List<FocusPerson> = _people.value
    fun settingsNow(): FocusPolicySettings = _settings.value

    fun setPeople(next: List<FocusPerson>) {
        _people.value = next.distinctBy { it.id }
        prefs.edit().putString(KEY_PEOPLE, encodePeople(_people.value)).apply()
    }

    fun upsertPerson(person: FocusPerson) {
        val without = _people.value.filterNot { it.id == person.id || samePhone(it.phone, person.phone) }
        setPeople(without + person)
    }

    fun removePerson(id: String) {
        setPeople(_people.value.filterNot { it.id == id })
    }

    fun updateReach(id: String, reach: FocusReach) {
        setPeople(_people.value.map { if (it.id == id) it.copy(reach = reach) else it })
    }

    fun setSettings(next: FocusPolicySettings) {
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_CALLS, next.allowCallsFromSelected)
            .putBoolean(KEY_MESSAGES, next.allowMessagesFromSelected)
            .putBoolean(KEY_REPEAT, next.allowRepeatedCallers)
            .putBoolean(KEY_ALARMS, next.allowAlarms)
            .putBoolean(KEY_REMINDERS, next.allowCalendarReminders)
            .putBoolean(KEY_SILENCE, next.silenceEveryoneElse)
            .putInt(KEY_DURATION, next.lastDurationMinutes.coerceIn(1, 24 * 60))
            .apply()
    }

    fun setLastDuration(minutes: Int) {
        setSettings(_settings.value.copy(lastDurationMinutes = minutes.coerceIn(1, 24 * 60)))
    }

    /** Seed architecture for one-tap groups; ids only until a full group manager exists. */
    fun setGroup(group: FocusPeopleGroup) {
        val next = _groups.value.filterNot { it.id == group.id } + group
        _groups.value = next
        prefs.edit().putString(KEY_GROUPS, encodeGroups(next)).apply()
    }

    fun saveList(title: String, members: List<FocusPerson>, id: String = java.util.UUID.randomUUID().toString()) {
        val clean = title.trim().take(40)
        if (clean.isBlank()) return
        prefs.edit().putString("list_members_$id", encodePeople(members)).apply()
        setGroup(FocusPeopleGroup(id, clean, members.map { it.id }))
        _listsRevision.value += 1
        if (id in _selectedGroups.value) syncSelectedPeople()
    }

    fun deleteList(id: String) {
        // Presets are re-seeded on load — only user-created groups can be removed.
        if (FocusPeopleGroup.presets.any { it.id == id }) return
        val next = _groups.value.filterNot { it.id == id }
        prefs.edit().remove("list_members_$id").putString(KEY_GROUPS, encodeGroups(next)).apply()
        _groups.value = next
        _listsRevision.value += 1
        selectGroup(id, false)
    }

    fun isPresetGroup(id: String): Boolean = FocusPeopleGroup.presets.any { it.id == id }

    fun peopleForGroup(groupId: String): List<FocusPerson> {
        prefs.getString("list_members_$groupId", null)?.let { raw ->
            return runCatching { decodePeople(raw) }.getOrDefault(emptyList())
        }
        val ids = _groups.value.find { it.id == groupId }?.personIds.orEmpty().toSet()
        if (ids.isEmpty()) return emptyList()
        return _people.value.filter { it.id in ids }
    }

    private fun loadPeople(): List<FocusPerson> =
        runCatching { decodePeople(prefs.getString(KEY_PEOPLE, null).orEmpty()) }.getOrDefault(emptyList())

    private fun loadSettings(): FocusPolicySettings = FocusPolicySettings(
        allowCallsFromSelected = prefs.getBoolean(KEY_CALLS, true),
        allowMessagesFromSelected = prefs.getBoolean(KEY_MESSAGES, true),
        allowRepeatedCallers = prefs.getBoolean(KEY_REPEAT, true),
        allowAlarms = prefs.getBoolean(KEY_ALARMS, true),
        allowCalendarReminders = prefs.getBoolean(KEY_REMINDERS, false),
        silenceEveryoneElse = prefs.getBoolean(KEY_SILENCE, true),
        lastDurationMinutes = prefs.getInt(KEY_DURATION, 60)
    )

    private fun loadGroups(): List<FocusPeopleGroup> {
        val raw = prefs.getString(KEY_GROUPS, null)
        if (raw.isNullOrBlank()) return FocusPeopleGroup.presets
        val stored = runCatching { decodeGroups(raw) }.getOrDefault(FocusPeopleGroup.presets)
        // Keep preset order; append any user-created groups; seed missing presets (e.g. VIP).
        val byId = stored.associateBy { it.id }
        val presets = FocusPeopleGroup.presets.map { preset ->
            val existing = byId[preset.id] ?: return@map preset
            if (preset.id == "emergency" && existing.title == "Emergency only") existing.copy(title = "Emergency") else existing
        }
        val extras = stored.filter { g -> FocusPeopleGroup.presets.none { it.id == g.id } }
        return presets + extras
    }

    private fun samePhone(a: String, b: String): Boolean {
        val da = a.filter(Char::isDigit)
        val db = b.filter(Char::isDigit)
        return da.isNotBlank() && db.isNotBlank() && (da == db || da.endsWith(db) || db.endsWith(da))
    }

    companion object {
        private const val PREFS = "focus_people"
        private const val KEY_PEOPLE = "people_json"
        private const val KEY_GROUPS = "groups_json"
        private const val KEY_CALLS = "allow_calls"
        private const val KEY_MESSAGES = "allow_messages"
        private const val KEY_REPEAT = "allow_repeat"
        private const val KEY_ALARMS = "allow_alarms"
        private const val KEY_REMINDERS = "allow_reminders"
        private const val KEY_SILENCE = "silence_else"
        private const val KEY_DURATION = "last_duration"

        @Volatile private var instance: FocusAllowedPeopleRepository? = null
        fun get(context: Context): FocusAllowedPeopleRepository =
            instance ?: synchronized(this) {
                instance ?: FocusAllowedPeopleRepository(context).also { instance = it }
            }

        private fun encodePeople(list: List<FocusPerson>): String {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("phone", p.phone)
                    .put("lookup", p.contactLookupKey)
                    .put("reach", p.reach.name)
                    .put("color", p.avatarColor).put("photo", p.photoUri))
            }
            return arr.toString()
        }

        private fun decodePeople(raw: String): List<FocusPerson> {
            if (raw.isBlank()) return emptyList()
            val arr = JSONArray(raw)
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        FocusPerson(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            phone = o.optString("phone"),
                            contactLookupKey = o.optString("lookup"),
                            reach = runCatching { FocusReach.valueOf(o.optString("reach")) }
                                .getOrDefault(FocusReach.CallsAndMessages),
                            photoUri = o.optString("photo"),
                            avatarColor = o.optLong("color", 0xFF34D399)
                        )
                    )
                }
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }

        private fun encodeGroups(list: List<FocusPeopleGroup>): String {
            val arr = JSONArray()
            list.forEach { g ->
                arr.put(JSONObject()
                    .put("id", g.id)
                    .put("title", g.title)
                    .put("ids", JSONArray(g.personIds)))
            }
            return arr.toString()
        }

        private fun decodeGroups(raw: String): List<FocusPeopleGroup> {
            val arr = JSONArray(raw)
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ids = o.optJSONArray("ids") ?: JSONArray()
                    val personIds = buildList {
                        for (j in 0 until ids.length()) add(ids.getString(j))
                    }
                    add(FocusPeopleGroup(o.optString("id"), o.optString("title"), personIds))
                }
            }
        }
    }
}

internal fun combineCallGroups(groups: List<List<FocusPerson>>): List<FocusPerson> =
    groups.flatten().filter { it.reach.allowsCalls }.distinctBy { it.id }

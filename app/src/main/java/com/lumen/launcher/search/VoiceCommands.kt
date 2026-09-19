package com.lumen.launcher.search

import com.lumen.launcher.data.TodoRepeat
import com.lumen.launcher.data.TodoTime

object VoiceCommands {

    private val WEEKDAYS = listOf(
        "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
    )

    data class Alarm(val hour: Int, val minute: Int, val daily: Boolean = false)

    sealed class AlarmCommand {
        data class Set(val hour: Int, val minute: Int, val daily: Boolean) : AlarmCommand()
        data class Cancel(val hour: Int? = null, val minute: Int? = null) : AlarmCommand()
        data object List : AlarmCommand()
    }

    fun alarmCommand(text: String): AlarmCommand? {
        val q = text.lowercase().trim()
        if (listAlarms(q)) return AlarmCommand.List
        if (q.contains("cancel") || q.contains("delete") || q.contains("turn off") || q.contains("remove")) {
            if (q.contains("alarm") || q.contains("wake")) {
                val time = parseClock(q)
                return AlarmCommand.Cancel(time?.first, time?.second)
            }
        }
        relativeMinutes(q)?.let { mins ->
            val alarmish = q.contains("alarm") || q.contains("wake") || q.contains("remind")
            if (alarmish) {
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, mins) }
                return AlarmCommand.Set(
                    hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                    minute = cal.get(java.util.Calendar.MINUTE),
                    daily = false
                )
            }
        }
        val time = parseClock(q) ?: return null
        val alarmish = q.contains("alarm") || q.contains("wake") || q.startsWith("remind me at")
        if (!alarmish) return null
        val daily = q.contains("every day") || q.contains("everyday") || q.contains("daily")
        return AlarmCommand.Set(time.first, time.second, daily)
    }

    fun alarm(text: String): Alarm? {
        val command = alarmCommand(text) as? AlarmCommand.Set ?: return null
        return Alarm(command.hour, command.minute, command.daily)
    }

    fun task(text: String): String? {
        val q = text.trim()
        Regex(
            """^add\s+(.+?)\s+to\s+(?:my\s+)?(?:list|tasks|to-?dos?)$""",
            RegexOption.IGNORE_CASE
        ).find(q)?.let { match ->
            val item = match.groupValues[1].trim()
            if (item.isBlank() || looksLikeLayout(item)) return@let
            return taskLabel(item)
        }
        val match = Regex(
            """^(?:add|create|make|new)\s+(?:a\s+)?(?:task|to-?do|reminder)(?:\s+list)?(?:\s+to|\s+for|:)?\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(q) ?: Regex(
            """^(?:remind me to|remember to)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(q)
        return match?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() && it.lowercase() != "list" }
            ?.let(::taskLabel)
    }

    fun taskPriority(text: String): Boolean {
        val q = text.lowercase()
        return Regex("""\b(?:high\s+priority|priority|important|urgent)\b""").containsMatchIn(q)
    }

    fun taskRepeat(text: String): String {
        val q = text.lowercase()
        if (Regex("""\b(?:every\s+day|daily)\b""").containsMatchIn(q)) return TodoRepeat.Daily
        if (Regex("""\b(?:every\s+month|monthly)\b""").containsMatchIn(q)) return TodoRepeat.Monthly
        if (WEEKDAYS.any { q.contains("every $it") || q.contains("each $it") }) return TodoRepeat.Weekly
        if (Regex("""\b(?:every\s+week|weekly)\b""").containsMatchIn(q)) return TodoRepeat.Weekly
        return ""
    }

    fun taskDue(text: String, now: Long = System.currentTimeMillis()): Long {
        val q = text.lowercase()
        val weekday = WEEKDAYS.firstOrNull { q.contains("every $it") || q.contains("each $it") }
        val day = when {
            weekday != null -> nextWeekday(weekday, now)
            q.contains("tomorrow") -> TodoTime.tomorrowStart(now)
            else -> TodoTime.startOfDay(now)
        }
        if (Regex("""\banytime\b""").containsMatchIn(q)) {
            return TodoTime.startOfDay(day)
        }
        val clock = Regex(
            """\b(?:at|by)\s+(\d{1,2})(?:[:\s](\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\b""",
            RegexOption.IGNORE_CASE
        ).find(q) ?: return day
        var hour = clock.groupValues[1].toIntOrNull() ?: return day
        val minute = clock.groupValues[2].toIntOrNull() ?: 0
        val mer = clock.groupValues[3].lowercase().replace(".", "")
        if (hour !in 0..23 || minute !in 0..59) return day
        if (mer.startsWith("p") && hour in 1..11) hour += 12
        if (mer.startsWith("a") && hour == 12) hour = 0
        if (mer.isBlank() && hour in 1..12) {
            val soonest = soonestHour(hour, minute)
            hour = soonest.first
        }
        return TodoTime.withClock(day, hour, minute)
    }

    fun taskLabel(raw: String): String {
        val cleaned = raw
            .replace(Regex("""\b(?:today|tomorrow|anytime)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(
                Regex("""\b(?:at|by)\s+\d{1,2}(?:[:\s]\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)?\b""", RegexOption.IGNORE_CASE),
                " "
            )
            .replace(
                Regex(
                    """\b(?:every|each)\s+(?:day|week|month|sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(Regex("""\b(?:daily|weekly|monthly)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(?:high\s+priority|priority|important|urgent)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return cleaned.ifBlank { raw.trim() }
    }

    fun completeTask(text: String): String? {
        val q = text.trim()
        val match = Regex(
            """^(?:i(?:'?m|m| am) done with|done with|finish(?:ed)?|mark(?:\s+as)?|check off|complete)\s+(?:the\s+)?(?:task\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(q) ?: return null
        return match.groupValues[1].trim().removePrefix("task ").ifBlank { "this" }
    }

    fun deleteTask(text: String): String? {
        val q = text.trim()
        if (looksLikeLayout(q) || q.contains("alarm") || q.contains("wake")) return null
        val match = Regex(
            """^(?:delete|remove)\s+(?:the\s+)?(?:task|to-?do|reminder)?\s*(.+?)(?:\s+from\s+(?:my\s+)?(?:list|tasks|to-?dos?))?$""",
            RegexOption.IGNORE_CASE
        ).find(q) ?: return null
        val item = match.groupValues[1].trim()
        if (item.isBlank() || looksLikeLayout(item)) return null
        if (!q.contains("task") && !q.contains("list") && !q.contains("to-do") && !q.contains("todo") && !q.contains("reminder")) {
            return null
        }
        return item
    }

    fun openTasks(text: String): Boolean {
        val q = text.lowercase()
        return listOf(
            "open my list", "open my tasks", "open tasks", "open to do", "open todo",
            "to do page", "todo page", "show my to-do", "show my todo"
        ).any { q.contains(it) }
    }

    private fun looksLikeLayout(value: String): Boolean {
        val q = value.lowercase()
        return q.contains("dock") || q.contains("folder") || q.contains("home") ||
            q.contains("private") || q.contains("locked") || q.contains("label")
    }

    fun note(text: String): String? {
        val match = Regex(
            """^(?:note(?:\s+that)?|jot(?:\s+down)?|write(?:\s+down)?|capture(?:\s+a)?\s+note)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim()) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    fun saveLater(text: String): Boolean {
        val q = text.lowercase()
        return listOf("save this for later", "save for later", "pin this for later", "pin for later", "later this")
            .any { q.contains(it) }
    }

    fun dailyReview(text: String): Boolean {
        val q = text.lowercase()
        return listOf("daily review", "what's left today", "whats left today", "review my day", "end of day")
            .any { q.contains(it) }
    }

    fun focusMinutes(text: String): Int? {
        val q = text.lowercase().trim()
        if (q in setOf("end focus", "stop focus", "leave focus", "cancel focus")) return 0
        if (q in setOf("start focus", "focus session", "focus now", "begin focus")) return 30
        Regex("""(?:focus|focus for)\s+(\d+)\s*(?:min|mins|minutes)?""").find(q)?.let {
            return it.groupValues[1].toIntOrNull()?.coerceIn(5, 120)
        }
        if (q.contains("focus 30") || q == "focus thirty") return 30
        return null
    }

    fun showTasks(text: String): Boolean {
        val q = text.lowercase()
        return listOf(
            "show tasks", "show my tasks", "show my list", "my tasks", "task list",
            "to do list", "todo list", "what's on my list", "whats on my list"
        ).any { q.contains(it) } || openTasks(q)
    }

    private fun listAlarms(q: String): Boolean {
        if (!q.contains("alarm")) return false
        return listOf("show", "list", "what", "my alarms", "next alarm", "do i have", "any alarm")
            .any { q.contains(it) }
    }

    private fun relativeMinutes(q: String): Int? {
        Regex("""(?:in|after)\s+(an|a)\s+(hour|minute|min)\b""").find(q)?.let { match ->
            return if (match.groupValues[2].startsWith("hour")) 60 else 1
        }
        val match = Regex("""(?:in|after)\s+(\d+)\s*(minutes?|mins?|hours?|hrs?)\b""").find(q) ?: return null
        val n = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        val mins = if (unit.startsWith("hour") || unit.startsWith("hr")) n * 60 else n
        return mins.takeIf { it in 1..(24 * 60) }
    }

    private fun parseClock(text: String): Pair<Int, Int>? {
        val match = Regex(
            """\b(\d{1,2})(?:[:\s](\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\b""",
            RegexOption.IGNORE_CASE
        ).findAll(text).lastOrNull() ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val mer = match.groupValues[3].lowercase().replace(".", "")
        if (hour !in 0..23 || minute !in 0..59) return null
        if (mer.startsWith("p") && hour in 1..11) hour += 12
        if (mer.startsWith("a") && hour == 12) hour = 0
        if (hour == 24) hour = 0
        if (mer.isBlank() && hour in 1..12) {
            return soonestHour(hour, minute)
        }
        return hour to minute
    }

    private fun soonestHour(hour12: Int, minute: Int): Pair<Int, Int> {
        val now = java.util.Calendar.getInstance()
        val nowMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val a = (if (hour12 == 12) 12 else hour12) * 60 + minute
        val b = (if (hour12 == 12) 0 else hour12 + 12) * 60 + minute
        val next = listOf(a, b).minBy { mins ->
            val delta = (mins - nowMins + 24 * 60) % (24 * 60)
            if (delta == 0) 24 * 60 else delta
        }
        return (next / 60) to (next % 60)
    }

    private fun nextWeekday(name: String, now: Long): Long {
        val want = WEEKDAYS.indexOf(name).takeIf { it >= 0 } ?: return TodoTime.startOfDay(now)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = TodoTime.startOfDay(now) }
        val today = cal.get(java.util.Calendar.DAY_OF_WEEK) // Sunday = 1
        val add = (want + 1 - today + 7) % 7
        cal.add(java.util.Calendar.DAY_OF_YEAR, add)
        return cal.timeInMillis
    }
}

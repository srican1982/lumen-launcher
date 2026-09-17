package com.lumen.launcher.search

object VoiceCommands {

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
        val match = Regex(
            """^(?:add|create|make|new)\s+(?:a\s+)?(?:task|to-?do|reminder)(?:\s+list)?(?:\s+to|\s+for|:)?\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(q) ?: Regex(
            """^(?:remind me to|remember to)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(q)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && it.lowercase() != "list" }
    }

    fun showTasks(text: String): Boolean {
        val q = text.lowercase()
        return listOf(
            "show tasks", "show my tasks", "show my list", "my tasks", "task list",
            "to do list", "what's on my list", "whats on my list"
        ).any { q.contains(it) }
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
}

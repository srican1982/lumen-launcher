package com.lumen.launcher.data

import java.util.Calendar
import java.util.TimeZone

data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val space: SpaceKind = SpaceKind.Home,
    val createdAt: Long = 0L,
    val dueAt: Long? = null,
    val priority: Boolean = false,
    val repeat: String = ""
)

object TodoTime {
    private const val DayMs = 24 * 60 * 60 * 1000L

    fun startOfDay(ms: Long = System.currentTimeMillis()): Long =
        calendar(ms).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun endOfDay(ms: Long = System.currentTimeMillis()): Long = startOfDay(ms) + DayMs - 1

    fun tomorrowStart(now: Long = System.currentTimeMillis()): Long = startOfDay(now) + DayMs

    fun hourOf(ms: Long): Int = calendar(ms).get(Calendar.HOUR_OF_DAY)

    fun minuteOf(ms: Long): Int = calendar(ms).get(Calendar.MINUTE)

    fun hasClock(ms: Long): Boolean = hourOf(ms) != 0 || minuteOf(ms) != 0

    fun withClock(dayMs: Long, hour: Int, minute: Int): Long =
        calendar(startOfDay(dayMs)).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis

    fun keepClock(current: Long, newDay: Long): Long =
        if (hasClock(current)) withClock(newDay, hourOf(current), minuteOf(current)) else startOfDay(newDay)

    fun fromPickerUtc(utcMidnight: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, utc.get(Calendar.YEAR))
            set(Calendar.MONTH, utc.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun toPickerUtc(localMs: Long): Long {
        val local = calendar(startOfDay(localMs))
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, local.get(Calendar.YEAR))
            set(Calendar.MONTH, local.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun isUpcoming(item: TodoItem, now: Long = System.currentTimeMillis()): Boolean {
        if (item.done) return false
        val due = item.dueAt ?: return false
        return due > endOfDay(now)
    }

    fun isToday(item: TodoItem, now: Long = System.currentTimeMillis()): Boolean {
        if (item.done) return false
        return !isUpcoming(item, now)
    }

    fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfDay(now)
        val day = startOfDay(ms)
        return when (day) {
            today -> "Today"
            today + DayMs -> "Tomorrow"
            else -> android.text.format.DateFormat.format("EEE, MMM d", ms).toString()
        }
    }

    fun timeLabel(ms: Long): String =
        android.text.format.DateFormat.format("h:mm a", ms).toString()
            .replace("am", "AM")
            .replace("pm", "PM")

    fun chipLabel(item: TodoItem, now: Long = System.currentTimeMillis()): String {
        if (item.done) return relative(item.createdAt.takeIf { it > 0L } ?: now, now)
        val due = item.dueAt ?: return "Today · Anytime"
        val day = when {
            due < startOfDay(now) -> "Overdue"
            else -> dayLabel(due, now)
        }
        val time = if (hasClock(due)) timeLabel(due) else "Anytime"
        return "$day · $time"
    }

    fun draftLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
        val time = if (hasClock(ms)) timeLabel(ms) else "Anytime"
        return "${dayLabel(ms, now)} · $time"
    }

    fun relative(ms: Long, now: Long = System.currentTimeMillis()): String {
        val days = ((startOfDay(now) - startOfDay(ms)) / DayMs).toInt()
        return when {
            days <= 0 -> "Today"
            days == 1 -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> android.text.format.DateFormat.format("MMM d", ms).toString()
        }
    }

    private fun calendar(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }
}

object TodoRepeat {
    const val Daily = "daily"
    const val Weekly = "weekly"
    const val Monthly = "monthly"

    fun chip(repeat: String): String = when (repeat) {
        Daily -> "Daily"
        Weekly -> "Weekly"
        Monthly -> "Monthly"
        else -> "Repeat"
    }

    fun detail(repeat: String, dueAt: Long): String = when (repeat) {
        Daily -> "Every day"
        Weekly -> "Every ${weekday(dueAt)}"
        Monthly -> "Monthly on the ${monthDay(dueAt)}"
        else -> "Does not repeat"
    }

    fun nextDue(dueAt: Long, repeat: String, now: Long = System.currentTimeMillis()): Long {
        if (repeat.isBlank()) return dueAt
        var next = step(dueAt, repeat)
        val floor = TodoTime.startOfDay(now)
        var guard = 0
        while (TodoTime.startOfDay(next) < floor && guard++ < 40) {
            next = step(next, repeat)
        }
        return next
    }

    fun weekday(ms: Long): String =
        android.text.format.DateFormat.format("EEEE", ms).toString()

    private fun monthDay(ms: Long): String {
        val day = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_MONTH)
        return "$day${ordinal(day)}"
    }

    private fun step(dueAt: Long, repeat: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        when (repeat) {
            Daily -> cal.add(Calendar.DAY_OF_YEAR, 1)
            Weekly -> cal.add(Calendar.DAY_OF_YEAR, 7)
            Monthly -> cal.add(Calendar.MONTH, 1)
            else -> return dueAt
        }
        return cal.timeInMillis
    }

    private fun ordinal(day: Int): String = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}

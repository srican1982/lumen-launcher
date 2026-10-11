package com.lumen.launcher.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class LumenAlarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val daily: Boolean = false,
    val label: String = "",
    /** Fixed deadline for a countdown; ordinary alarms keep their local wall-clock time. */
    val triggerAtMs: Long? = null
) {
    fun nextTriggerMs(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        triggerAtMs?.let { return it }
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var at = date.atTime(hour, minute).atZone(zone)
        if (at.toInstant().toEpochMilli() <= now + 15_000L) at = date.plusDays(1).atTime(hour, minute).atZone(zone)
        return at.toInstant().toEpochMilli()
    }

    fun displayTime(): String {
        val t = LocalTime.of(hour, minute)
        return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(t)
    }

    fun whenLabel(now: Long = System.currentTimeMillis()): String {
        if (daily) return "Every day"
        val zone = ZoneId.systemDefault()
        val trigger = Instant.ofEpochMilli(nextTriggerMs(now)).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (trigger) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> DateTimeFormatter.ofPattern("EEE").format(trigger)
        }
    }
}

object AlarmTones {
    const val AURA = "aura"
    const val PULSE = "pulse"
    const val DAWN = "dawn"
    const val BELL = "bell"

    val all = listOf(AURA, PULSE, DAWN, BELL)

    fun label(id: String): String = when (id) {
        PULSE -> "Pulse"
        DAWN -> "Dawn"
        BELL -> "Bell"
        else -> "Aura"
    }

    fun hint(id: String): String = when (id) {
        PULSE -> "Two-note wake"
        DAWN -> "Bright arpeggio"
        BELL -> "Clear chime"
        else -> "Soft rise"
    }

    fun next(id: String): String {
        val i = all.indexOf(id).let { if (it < 0) 0 else it }
        return all[(i + 1) % all.size]
    }
}

object AlarmIntents {
    const val ACTION_FIRE = "com.lumen.launcher.alarm.FIRE"
    const val ACTION_DISMISS = "com.lumen.launcher.alarm.DISMISS"
    const val ACTION_SNOOZE = "com.lumen.launcher.alarm.SNOOZE"
    const val EXTRA_ID = "alarm_id"
    const val EXTRA_HOUR = "alarm_hour"
    const val EXTRA_MINUTE = "alarm_minute"
    const val EXTRA_LABEL = "alarm_label"
    const val EXTRA_DAILY = "alarm_daily"
    const val EXTRA_TONE = "alarm_tone"
}

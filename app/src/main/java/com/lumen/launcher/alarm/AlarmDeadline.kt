package com.lumen.launcher.alarm

import java.time.Instant
import java.time.ZoneId

object AlarmDeadline {
    fun timer(seconds: Int, now: Long = System.currentTimeMillis()): Long {
        require(seconds in 1..86400)
        return Math.addExact(now, seconds * 1_000L)
    }

    fun forRequest(hour: Int, minute: Int, text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
            .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }
}

package com.lumen.launcher.inbox

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.lumen.launcher.data.CalendarEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object MeetingParser {

    private val outlook = setOf("com.microsoft.office.outlook")
    private val teams = setOf("com.microsoft.teams", "com.microsoft.skype.teams")

    fun isMeetingPackage(packageName: String) = packageName in outlook || packageName in teams

    fun parse(sbn: StatusBarNotification): CalendarEvent? {
        if (!isMeetingPackage(sbn.packageName)) return null
        if (sbn.isOngoing) return null
        val extras = sbn.notification.extras
        val messaging = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
        if (!messaging?.messages.isNullOrEmpty()) return null
        val title = extras.char(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.char(Notification.EXTRA_TITLE)
            ?: return null
        if (title.equals("Outlook", true) || title.equals("Teams", true) ||
            title.equals("Microsoft Teams", true)
        ) return null
        val preview = listOfNotNull(
            extras.char(Notification.EXTRA_TEXT),
            extras.char(Notification.EXTRA_BIG_TEXT),
            extras.char(Notification.EXTRA_SUB_TEXT),
            extras.char(Notification.EXTRA_INFO_TEXT)
        ).joinToString(" ")
        val category = sbn.notification.category.orEmpty()
        val blob = "$title $preview $category".lowercase()
        if (!looksLikeMeeting(sbn.packageName, category, blob, preview)) return null
        val begin = parseBegin(preview, title, sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime)
            ?: return null
        val end = parseEnd(preview, begin) ?: (begin + 30L * 60 * 1000)
        if (end < System.currentTimeMillis() - 5 * 60 * 1000L) return null
        val teams = sbn.packageName in teams || blob.contains("teams")
        return CalendarEvent(
            id = sbn.key.hashCode().toLong() and 0x7fffffff,
            title = title.trim(),
            begin = begin,
            end = end,
            location = if (teams) "Microsoft Teams" else "",
            calendarName = if (sbn.packageName in outlook) "Outlook" else "Teams",
            teams = teams,
            noticeKey = sbn.key
        )
    }

    private fun looksLikeMeeting(packageName: String, category: String, blob: String, preview: String): Boolean {
        if (category == Notification.CATEGORY_EVENT || category == Notification.CATEGORY_REMINDER) return true
        if (packageName in teams) {
            return blob.contains("meeting") || blob.contains("join") || blob.contains("starting") ||
                blob.contains("webinar") || blob.contains("calendar")
        }
        val short = preview.length <= 96
        return short && (
            blob.contains("microsoft teams meeting") ||
                blob.contains("teams meeting") ||
                blob.contains("starting now") ||
                blob.contains("starts in") ||
                blob.contains("starting in") ||
                Regex("""\bin\s+\d+\s+(min|mins|minute|minutes|hr|hrs|hour|hours)\b""").containsMatchIn(blob) ||
                Regex("""\b\d{1,2}:\d{2}\s*[ap]m\b""").containsMatchIn(blob) ||
                blob.contains("calendar reminder") ||
                blob.contains("event reminder")
            )
    }

    private fun parseBegin(preview: String, title: String, postedAt: Long): Long? {
        val blob = "$title $preview"
        val now = System.currentTimeMillis()
        val lower = blob.lowercase()
        if (lower.contains("starting now") || lower.contains("starts now") ||
            Regex("""^\s*now\s*$""", RegexOption.IGNORE_CASE).containsMatchIn(preview)
        ) return now
        Regex("""\bin\s+(\d+)\s+(minutes?|mins?)\b""", RegexOption.IGNORE_CASE).find(lower)?.let {
            return now + it.groupValues[1].toLong() * 60_000L
        }
        Regex("""\bin\s+(\d+)\s+(hours?|hrs?)\b""", RegexOption.IGNORE_CASE).find(lower)?.let {
            return now + it.groupValues[1].toLong() * 60 * 60_000L
        }
        if (lower.contains("starting") && lower.contains("meeting")) return now
        val time = Regex(
            """\b(\d{1,2}):(\d{2})\s*([ap]m)\b""",
            RegexOption.IGNORE_CASE
        ).findAll(blob).firstOrNull() ?: return postedAt.takeIf { looksTimed(preview) }
        val hourRaw = time.groupValues[1].toInt()
        val minute = time.groupValues[2].toInt()
        val ampm = time.groupValues[3].lowercase()
        var hour = hourRaw % 12
        if (ampm.startsWith("p")) hour += 12
        val zone = ZoneId.systemDefault()
        var stamp = LocalDate.now(zone).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        if (lower.contains("tomorrow")) stamp += 24L * 60 * 60 * 1000
        if (stamp + 5 * 60 * 1000 < now) stamp += 24L * 60 * 60 * 1000
        return stamp
    }

    private fun parseEnd(preview: String, begin: Long): Long? {
        val times = Regex(
            """\b(\d{1,2}):(\d{2})\s*([ap]m)\b""",
            RegexOption.IGNORE_CASE
        ).findAll(preview).toList()
        if (times.size < 2) return null
        val end = times[1]
        val hourRaw = end.groupValues[1].toInt()
        val minute = end.groupValues[2].toInt()
        val ampm = end.groupValues[3].lowercase()
        var hour = hourRaw % 12
        if (ampm.startsWith("p")) hour += 12
        val zone = ZoneId.systemDefault()
        val startDate = java.time.Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
        var stamp = startDate.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        if (stamp <= begin) stamp += 24L * 60 * 60 * 1000
        return stamp
    }

    private fun looksTimed(preview: String): Boolean {
        val lower = preview.lowercase()
        return lower.contains("now") || lower.contains("minute") || lower.contains("min")
    }

    private fun android.os.Bundle.char(key: String): String? =
        getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}

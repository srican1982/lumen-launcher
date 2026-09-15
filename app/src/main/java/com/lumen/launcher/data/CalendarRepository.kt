package com.lumen.launcher.data

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val calendarName: String = "",
    val teams: Boolean = false,
    val noticeKey: String = ""
)

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String
) {
    val roleGuess: CalendarRole
        get() = CalendarRole.guess(accountType, account, name)

    val displayName: String
        get() = name.ifBlank { account }
}

data class PhoneAccount(
    val name: String,
    val type: String
) {
    val label: String
        get() {
            val hay = "$type $name".lowercase()
            return when {
                hay.contains("outlook") || hay.contains("microsoft") || hay.contains("exchange") ||
                    hay.contains("office") || hay.contains("workaccount") || hay.contains("eas") ->
                    "Outlook"
                else -> "Google"
            }
        }
}

enum class CalendarRole {
    Work, Personal, Off;

    companion object {
        fun guess(accountType: String, account: String, name: String): CalendarRole {
            val hay = "$accountType $account $name".lowercase()
            return if (
                hay.contains("exchange") ||
                hay.contains("outlook") ||
                hay.contains("microsoft") ||
                hay.contains("office365") ||
                hay.contains("office 365") ||
                hay.contains("onmicrosoft") ||
                hay.contains("workaccount") ||
                hay.contains("eas") ||
                hay.contains("teams")
            ) Work else Personal
        }
    }
}

class CalendarRepository(private val context: Context) {

    fun listCalendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE
            ),
            null,
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC"
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val account = cursor.getString(2).orEmpty()
                    val name = cursor.getString(1).orEmpty().ifBlank { account }
                    if (name.isBlank()) continue
                    add(
                        DeviceCalendar(
                            id = cursor.getLong(0),
                            name = name,
                            account = account,
                            accountType = cursor.getString(3).orEmpty()
                        )
                    )
                }
            }.distinctBy { it.id }
        }.orEmpty()
    }

    fun listPhoneAccounts(): List<PhoneAccount> {
        val fromCalendars = listCalendars().map { PhoneAccount(it.account, it.accountType) }
        val manager = AccountManager.get(context)
        val fromDevice = runCatching { manager.accounts.toList() }.getOrDefault(emptyList())
            .filter { account ->
                val hay = "${account.type} ${account.name}".lowercase()
                if (hay.contains("com.microsoft.teams") || hay.contains("skype.teams")) return@filter false
                hay.contains("google") || hay.contains("microsoft") || hay.contains("outlook") ||
                    hay.contains("exchange") || hay.contains("office") || hay.contains("workaccount")
            }
            .map { PhoneAccount(it.name, it.type) }
        val fromApps = listOf(
            "com.microsoft.office.outlook" to "Outlook",
            "com.google.android.gm" to "Gmail"
        ).mapNotNull { (pkg, label) ->
            val installed = runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
            if (installed) PhoneAccount(label, pkg) else null
        }
        return (fromCalendars + fromDevice + fromApps)
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.lowercase() }
    }

    fun nextEvent(calendarIds: Set<Long>? = null): CalendarEvent? = upcoming(calendarIds).firstOrNull()

    fun upcoming(calendarIds: Set<Long>? = null, limit: Int = 3): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        if (calendarIds != null && calendarIds.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val until = now + 14L * 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { builder ->
            ContentUris.appendId(builder, now)
            ContentUris.appendId(builder, until)
        }.build()
        val filters = mutableListOf("${CalendarContract.Instances.END}>?")
        val args = mutableListOf(now.toString())
        if (!calendarIds.isNullOrEmpty()) {
            filters += "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",")})"
        }
        val selection = filters.joinToString(" AND ")
        val order = "${CalendarContract.Instances.BEGIN} ASC"
        val full = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY
        )
        val slim = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY
        )
        return readUpcoming(uri, full, selection, args, order, hasDescription = true, limit)
            ?: readUpcoming(uri, slim, selection, args, order, hasDescription = false, limit)
            ?: emptyList()
    }

    private fun readUpcoming(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String,
        args: List<String>,
        order: String,
        hasDescription: Boolean,
        limit: Int
    ): List<CalendarEvent>? {
        val found = ArrayList<CalendarEvent>()
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, selection, args.toTypedArray(), order)
        }.getOrNull() ?: return null
        cursor.use { rows ->
            while (rows.moveToNext()) {
                if (rows.getInt(6) == CalendarContract.Events.STATUS_CANCELED) continue
                val title = rows.getString(1).orEmpty()
                if (title.isBlank()) continue
                val calendarName = rows.getString(5).orEmpty()
                if (isNoiseCalendar(calendarName)) continue
                val id = rows.getLong(0)
                val description = if (hasDescription) rows.getString(7).orEmpty() else ""
                val calendarIdIndex = if (hasDescription) 8 else 7
                val allDayIndex = calendarIdIndex + 1
                val allDay = rows.getInt(allDayIndex) == 1
                val locationRaw = rows.getString(4).orEmpty()
                val teams = looksLikeTeams("$title $locationRaw $description $calendarName") ||
                    looksLikeTeams(eventExtras(id))
                if (allDay && !teams) continue
                val location = when {
                    teams && (locationRaw.isBlank() || locationRaw.contains("http", true) ||
                        locationRaw.contains("msteams:", true)) -> "Microsoft Teams"
                    teams && looksLikeTeams(locationRaw) -> locationRaw
                    else -> locationRaw
                }
                found += CalendarEvent(
                    id = id,
                    title = title,
                    begin = rows.getLong(2),
                    end = rows.getLong(3),
                    location = location,
                    calendarName = calendarName,
                    teams = teams
                )
                if (found.size >= 12) break
            }
        }
        return found
            .distinctBy { "${it.begin}|${it.title.trim().lowercase()}" }
            .take(limit)
    }

    private fun eventExtras(eventId: Long): String {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.CUSTOM_APP_PACKAGE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) ""
                else "${cursor.getString(0).orEmpty()} ${cursor.getString(1).orEmpty()}"
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun looksLikeTeams(text: String): Boolean {
        val hay = text.lowercase()
        return hay.contains("teams.microsoft.com") ||
            hay.contains("teams.live.com") ||
            hay.contains("teams.office.com") ||
            hay.contains("microsoft teams") ||
            hay.contains("teams meeting") ||
            hay.contains("msteams:") ||
            hay.contains("join the meeting") ||
            hay.contains("com.microsoft.teams")
    }

    private fun isNoiseCalendar(name: String): Boolean {
        val hay = name.lowercase()
        return hay.contains("birthday") ||
            hay.contains("holiday") ||
            hay.contains("holidays") ||
            hay.contains("weather")
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}

package com.lumen.launcher.data

object WorkCardEvent {
    fun eligible(event: CalendarEvent, work: Set<String>, personal: Set<String>): Boolean {
        if (event.calendarId?.toString() in work) return true
        if (event.calendarId?.toString() in personal) return false
        val meeting = event.teams || event.location in setOf("Zoom", "Google Meet", "Microsoft Teams") ||
            Regex("https?://(?:[a-z0-9-]+\\.)?(?:zoom\\.us|meet\\.google\\.com|teams\\.microsoft\\.com|teams\\.live\\.com)/", RegexOption.IGNORE_CASE).containsMatchIn(event.location)
        return meeting || Regex("outlook|exchange|work|office 365|teams", RegexOption.IGNORE_CASE).containsMatchIn(event.calendarName)
    }
    fun whenText(event: CalendarEvent, now: Long): String = EventPresentation.whenText(event, now)
}

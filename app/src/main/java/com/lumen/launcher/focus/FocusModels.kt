package com.lumen.launcher.focus

/** How a person may interrupt Focus. */
enum class FocusReach {
    CallsOnly,
    MessagesOnly,
    CallsAndMessages;

    val allowsCalls: Boolean get() = this == CallsOnly || this == CallsAndMessages
    val allowsMessages: Boolean get() = this == MessagesOnly || this == CallsAndMessages

    fun label(): String = when (this) {
        CallsOnly -> "Calls only"
        MessagesOnly -> "Messages only"
        CallsAndMessages -> "Calls + Messages"
    }
}

data class FocusPerson(
    val id: String,
    val name: String,
    val phone: String = "",
    val contactLookupKey: String = "",
    val reach: FocusReach = FocusReach.CallsAndMessages,
    val photoUri: String = "",
    val avatarColor: Long = 0xFF34D399
)

/** Named presets for one-tap start later (Family / Work VIPs / Emergency). */
data class FocusPeopleGroup(
    val id: String,
    val title: String,
    val personIds: List<String> = emptyList()
) {
    companion object {
        val Family = FocusPeopleGroup("family", "Family")
        val WorkVips = FocusPeopleGroup("work_vips", "Work VIPs")
        val Emergency = FocusPeopleGroup("emergency", "Emergency only")
        val presets = listOf(Family, WorkVips, Emergency)
    }
}

data class FocusPolicySettings(
    val allowCallsFromSelected: Boolean = true,
    val allowMessagesFromSelected: Boolean = true,
    val allowRepeatedCallers: Boolean = true,
    val allowAlarms: Boolean = true,
    val allowCalendarReminders: Boolean = false,
    val silenceEveryoneElse: Boolean = true,
    val lastDurationMinutes: Int = 60
)

data class FocusSessionSnapshot(
    val until: Long = 0L,
    val startedAt: Long = 0L,
    val totalMs: Long = 0L,
    val pausedRemainingMs: Long = 0L,
    val error: String = "",
    val active: Boolean = false
) {
    val paused: Boolean get() = pausedRemainingMs > 0L
    val running: Boolean get() = until > System.currentTimeMillis() && pausedRemainingMs <= 0L
    val focusing: Boolean get() = running || paused

    fun remainingMs(now: Long = System.currentTimeMillis()): Long = when {
        paused -> pausedRemainingMs
        running -> (until - now).coerceAtLeast(0L)
        else -> 0L
    }
}

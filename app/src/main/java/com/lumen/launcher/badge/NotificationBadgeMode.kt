package com.lumen.launcher.badge

enum class NotificationBadgeMode {
    Off,
    Dot,
    Number;

    val title: String
        get() = when (this) {
            Off -> "Off"
            Dot -> "Dot"
            Number -> "Number"
        }

    fun next(): NotificationBadgeMode = when (this) {
        Off -> Dot
        Dot -> Number
        Number -> Off
    }

    companion object {
        fun parse(raw: String?): NotificationBadgeMode =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: Number
    }
}

data class BadgeState(
    val countsByPackage: Map<String, Int> = emptyMap(),
    val listenerConnected: Boolean = false
)

package com.lumen.launcher.data

enum class SpaceKind(val title: String, val kicker: String) {
    Home("Home", "Home space"),
    Work("Work", "Work space"),
    Personal("Social", "Social space"),
    Focus("Focus", "Focus space"),
    Travel("Travel", "Travel space"),
    Private("Locked", "Locked space");

    companion object {
        /** Maps spoken or typed aliases to a space. Internal enum name stays [Personal] for persisted data. */
        fun fromSpeechAlias(raw: String): SpaceKind? {
            val v = raw.trim().lowercase().replace(" space", "").replace(" layout", "").replace(" mode", "")
            return when (v) {
                "home" -> Home
                "work" -> Work
                "social", "personal" -> Personal
                "focus" -> Focus
                "travel" -> Travel
                else -> entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            }
        }

        fun infer(hour: Int): SpaceKind = when (hour) {
            in 5..8 -> Home
            in 9..16 -> Work
            in 17..20 -> Personal
            else -> Focus
        }
    }
}

data class SpaceContext(
    val space: SpaceKind,
    val greeting: String,
    val prompt: String,
    val automatic: Boolean
)

object SpaceCopy {
    fun greetingFor(hour: Int, name: String? = null): String {
        val base = when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
        val first = name?.trim()?.takeIf { it.isNotBlank() }
        return if (first != null) "$base, $first" else base
    }

    fun context(space: SpaceKind, hour: Int, automatic: Boolean): SpaceContext {
        val prompt = when (space) {
            SpaceKind.Work -> "Ask or open anything"
            SpaceKind.Private -> "Unlock to open"
            else -> "What do you want to do?"
        }
        return SpaceContext(space, greetingFor(hour), prompt, automatic)
    }
}

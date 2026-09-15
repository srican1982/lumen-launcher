package com.lumen.launcher.search

import com.lumen.launcher.data.SpaceKind

sealed class LauncherCommand {
    data object OpenFlow : LauncherCommand()
    data object OpenHome : LauncherCommand()
    data object OpenDrawer : LauncherCommand()
    data object OpenSearch : LauncherCommand()
    data object OpenRecents : LauncherCommand()
    data object OpenSettings : LauncherCommand()
    data object OpenPersonalize : LauncherCommand()
    data object OpenPrivate : LauncherCommand()
    data class SwitchSpace(val space: SpaceKind?) : LauncherCommand()
    data class IconSize(val sizeDp: Float? = null, val delta: Float? = null) : LauncherCommand()
    data class Pin(val name: String) : LauncherCommand()
    data class Unpin(val name: String) : LauncherCommand()
    data class Dock(val name: String) : LauncherCommand()
    data class Undock(val name: String) : LauncherCommand()
    data class HideApp(val name: String) : LauncherCommand()
    data class UnhideApp(val name: String) : LauncherCommand()
    data object HideNews : LauncherCommand()
    data object ShowNews : LauncherCommand()
    data object NeedNow : LauncherCommand()
    data object UsedYesterday : LauncherCommand()
    data object Weather : LauncherCommand()
    data object NextEvent : LauncherCommand()
    data object Help : LauncherCommand()
    data object EndTalk : LauncherCommand()
    data class OpenApp(val name: String) : LauncherCommand()
    data class Purpose(val query: String) : LauncherCommand()
}

object LauncherVoice {

    fun parse(text: String): LauncherCommand? {
        val q = text.lowercase().trim().replace(Regex("\\s+"), " ")
        if (q.isBlank()) return null
        help(q)?.let { return it }
        farewell(q)?.let { return it }
        navigation(q)?.let { return it }
        space(q)?.let { return it }
        flow(q)?.let { return it }
        layout(q)?.let { return it }
        homeApp(q)?.let { return it }
        usage(q)?.let { return it }
        purpose(q)?.let { return it }
        weather(q)?.let { return it }
        calendar(q)?.let { return it }
        return null
    }

    private fun farewell(q: String): LauncherCommand? {
        val done = setOf(
            "thanks", "thank you", "thanks lumen", "thank you lumen",
            "goodbye", "good bye", "bye", "bye lumen",
            "that's all", "thats all", "that is all", "i'm done", "im done",
            "never mind", "nevermind", "stop listening", "that's it", "thats it",
            "no", "nope", "nah", "no thanks", "no thank you", "nothing", "not now"
        )
        if (q in done) return LauncherCommand.EndTalk
        return null
    }

    private fun help(q: String): LauncherCommand? {
        if (q == "help" || q == "what can you do" || q.contains("what can lumen") ||
            q == "what do you do" || q.contains("what can i say")
        ) return LauncherCommand.Help
        return null
    }

    private fun navigation(q: String): LauncherCommand? {
        if (q.contains("take me home") || q.contains("navigate home") || q.contains("directions home")) return null
        when {
            q.contains("open flow") || q.contains("go to flow") || q.contains("show flow") ||
                q == "flow" || q.contains("what's on flow") || q.contains("whats on flow") ->
                return LauncherCommand.OpenFlow
            q.contains("recents panel") || q.contains("recent panel") || q.contains("show recents") ||
                q.contains("show recent") || q.contains("recent apps") || q.contains("jump back") ->
                return LauncherCommand.OpenRecents
            q.contains("app drawer") || q.contains("all apps") || q.contains("open drawer") ||
                q.contains("show drawer") ->
                return LauncherCommand.OpenDrawer
            q == "search" || q == "open search" ->
                return LauncherCommand.OpenSearch
            q.contains("personalize flow") || q.contains("customise flow") || q.contains("customize flow") ||
                q.contains("flow settings") ->
                return LauncherCommand.OpenPersonalize
            q.contains("lumen settings") || q == "launcher settings" || q == "open settings" ->
                return LauncherCommand.OpenSettings
            q.contains("home screen") || q == "go home" || q == "open home" || q == "show home" ->
                return LauncherCommand.OpenHome
        }
        openApp(q)?.let { return it }
        return null
    }

    private fun openApp(q: String): LauncherCommand? {
        val name = after(q, listOf("open ", "launch ", "start ", "run ", "go to ")) ?: return null
        val reserved = setOf(
            "flow", "home", "search", "settings", "drawer", "recents", "private",
            "the drawer", "app drawer", "all apps", "lumen settings", "private space"
        )
        if (name in reserved) return null
        if (name.startsWith("flow") || name.startsWith("home") && name.length <= 10) return null
        return name.takeIf { it.isNotBlank() }?.let { LauncherCommand.OpenApp(it) }
    }

    private fun space(q: String): LauncherCommand? {
        if (q.contains("private space") || q.contains("open private") || q == "private") {
            return LauncherCommand.OpenPrivate
        }
        if (q.contains("work layout") || q.contains("work space") || q.contains("work mode") ||
            q.contains("switch to work")
        ) {
            return LauncherCommand.SwitchSpace(SpaceKind.Work)
        }
        if (q.contains("personal space") || q.contains("personal layout") || q.contains("switch to personal")) {
            return LauncherCommand.SwitchSpace(SpaceKind.Personal)
        }
        if (q.contains("home layout") || q.contains("home space") || q.contains("switch to home space")) {
            return LauncherCommand.SwitchSpace(SpaceKind.Home)
        }
        if (q.contains("focus space") || q.contains("focus mode") || q.contains("switch to focus")) {
            return LauncherCommand.SwitchSpace(SpaceKind.Focus)
        }
        if (q.contains("automatic space") || q.contains("auto space") || q.contains("clear space")) {
            return LauncherCommand.SwitchSpace(null)
        }
        return null
    }

    private fun flow(q: String): LauncherCommand? {
        if (q.contains("hide news") || q.contains("remove news") || q.contains("news off")) {
            return LauncherCommand.HideNews
        }
        if (q.contains("show news") || q.contains("put news") || q.contains("news on") || q.contains("bring news")) {
            return LauncherCommand.ShowNews
        }
        return null
    }

    private fun layout(q: String): LauncherCommand? {
        when {
            q.contains("icon") && (q.contains("bigger") || q.contains("larger") || q.contains("increase")) ->
                return LauncherCommand.IconSize(delta = 8f)
            q.contains("icon") && (q.contains("smaller") || q.contains("decrease") || q.contains("tinier")) ->
                return LauncherCommand.IconSize(delta = -8f)
            q.contains("large icons") || q.contains("biggest icons") ->
                return LauncherCommand.IconSize(sizeDp = 72f)
            q.contains("small icons") || q.contains("smallest icons") ->
                return LauncherCommand.IconSize(sizeDp = 44f)
            q.contains("default icons") || q.contains("normal icons") ->
                return LauncherCommand.IconSize(sizeDp = 60f)
        }
        return null
    }

    private fun homeApp(q: String): LauncherCommand? {
        after(q, listOf("unhide ", "show hidden ", "show the hidden "))?.let {
            return LauncherCommand.UnhideApp(it)
        }
        after(q, listOf("unpin ", "remove from home ", "take off home "))?.let {
            return LauncherCommand.Unpin(it)
        }
        after(q, listOf("remove from dock ", "undock ", "take off the dock "))?.let {
            return LauncherCommand.Undock(it)
        }
        after(q, listOf("add to dock ", "put on the dock ", "dock "))?.let { name ->
            if (name.isNotBlank() && name != "the") return LauncherCommand.Dock(name)
        }
        after(q, listOf("pin ", "add to home ", "put on home ", "put on my home "))?.let {
            return LauncherCommand.Pin(it)
        }
        after(q, listOf("hide app ", "hide the ", "hide "))?.let { name ->
            if (name.contains("news") || name.contains("flow")) return null
            if (name.isNotBlank()) return LauncherCommand.HideApp(name.removeSuffix(" app").trim())
        }
        return null
    }

    private fun usage(q: String): LauncherCommand? {
        if (q.contains("yesterday")) return LauncherCommand.UsedYesterday
        if (q.contains("normally use") || q.contains("usually use") || q.contains("use now") ||
            q.contains("likely next") || q.contains("need now") || q.contains("should i open") ||
            q.contains("what do i open") || q.contains("predict")
        ) {
            return LauncherCommand.NeedNow
        }
        return null
    }

    private fun purpose(q: String): LauncherCommand? {
        val phrases = listOf(
            "banking apps", "bank apps", "finance apps", "finance folder",
            "food apps", "music apps", "work apps", "photo editor", "editing photos"
        )
        if (phrases.any { q.contains(it) } || q.contains("create a finance") || q.contains("create finance")) {
            return LauncherCommand.Purpose(q)
        }
        return null
    }

    private fun weather(q: String): LauncherCommand? {
        if (q.contains("weather") || q.contains("temperature") || q.contains("how hot") || q.contains("how cold")) {
            return LauncherCommand.Weather
        }
        return null
    }

    private fun calendar(q: String): LauncherCommand? {
        if (q.contains("next meeting") || q.contains("what's next") || q.contains("whats next") ||
            q.contains("my calendar") || q.contains("on my calendar") || q.contains("next event")
        ) {
            return LauncherCommand.NextEvent
        }
        return null
    }

    private fun after(q: String, prefixes: List<String>): String? {
        for (prefix in prefixes) {
            if (q.startsWith(prefix)) {
                return q.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}

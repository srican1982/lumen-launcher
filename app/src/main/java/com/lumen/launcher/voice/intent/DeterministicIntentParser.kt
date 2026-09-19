package com.lumen.launcher.voice.intent

import com.lumen.launcher.data.SpaceKind
import com.lumen.launcher.flow.FlowModule
import com.lumen.launcher.flow.flowModuleFromSpeech
import com.lumen.launcher.search.VoiceCommands

internal object DeterministicIntentParser {

    private val reservedOpen = setOf(
        "flow", "home", "search", "settings", "drawer", "recents", "private",
        "the drawer", "app drawer", "all apps", "lumen settings", "private space",
        "locked space", "work", "personal", "focus", "work space", "personal space",
        "focus space", "home space", "work mode", "focus mode", "the work space",
        "the personal space", "the focus space", "the home space",
        "the work", "the personal", "the focus", "up settings", "up search",
        "up home", "up flow", "up drawer"
    )

    fun parse(text: String): List<VoiceIntent> {
        val q = text.lowercase().trim().replace(Regex("\\s+"), " ")
        if (q.isBlank()) return emptyList()
        val hits = mutableListOf<VoiceIntent>()
        fun add(intent: VoiceIntent?) { if (intent != null) hits += intent }

        add(math(q, text))
        add(help(q, text))
        add(farewell(q, text))
        add(navigation(q, text))
        add(openApp(q, text))
        add(space(q, text))
        add(labels(q, text))
        add(grid(q, text))
        add(dockSize(q, text))
        add(icons(q, text))
        add(folder(q, text))
        add(homeApp(q, text))
        add(privateApp(q, text))
        add(whereApp(q, text))
        add(alias(q, text))
        add(flow(q, text))
        add(usage(q, text))
        add(purpose(q, text))
        add(weather(q, text))
        add(calendar(q, text))
        add(alarm(q, text))
        add(productivity(q, text))
        add(tasks(q, text))
        return hits
    }

    private fun productivity(q: String, original: String): VoiceIntent? {
        VoiceCommands.note(original)?.let {
            return VoiceIntent(VoiceAction.SAVE_NOTE, 0.97f, original, textValue = it)
        }
        if (VoiceCommands.saveLater(q)) return VoiceIntent(VoiceAction.SAVE_LATER, 0.97f, original)
        if (VoiceCommands.dailyReview(q)) return VoiceIntent(VoiceAction.DAILY_REVIEW, 0.97f, original)
        VoiceCommands.focusMinutes(q)?.let { mins ->
            return if (mins == 0) VoiceIntent(VoiceAction.END_FOCUS, 0.97f, original)
            else VoiceIntent(VoiceAction.START_FOCUS, 0.97f, original, intValue = mins)
        }
        return null
    }

    private fun math(q: String, original: String): VoiceIntent? {
        val spoken = VoiceMath.interpret(q) ?: return null
        return VoiceIntent(
            action = VoiceAction.CALCULATE,
            confidence = 0.99f,
            originalText = original,
            textValue = spoken
        )
    }

    private fun help(q: String, original: String): VoiceIntent? {
        val exact = setOf(
            "help", "help me", "show help", "what can you do", "what do you do",
            "what can i say", "what can i say to you", "what can lumen do"
        )
        if (q in exact) {
            return VoiceIntent(VoiceAction.HELP, 1.00f, original)
        }
        return null
    }

    private fun farewell(q: String, original: String): VoiceIntent? {
        val done = setOf(
            "thanks", "thank you", "thanks lumen", "thank you lumen",
            "goodbye", "good bye", "bye", "bye lumen",
            "that's all", "thats all", "that is all", "i'm done", "im done",
            "never mind", "nevermind", "stop listening", "that's it", "thats it",
            "no", "nope", "nah", "no thanks", "no thank you", "nothing", "not now"
        )
        if (q in done) return VoiceIntent(VoiceAction.END_TALK, 1.00f, original)
        return null
    }

    private fun navigation(q: String, original: String): VoiceIntent? {
        if (q == "take me home" || q == "navigate home" || q == "directions home" || q == "go home navigation") {
            return null
        }
        return when {
            q in setOf(
                "flow", "open flow", "go to flow", "show flow", "open up flow",
                "what's on flow", "whats on flow", "what s on flow", "whats happening"
            ) -> VoiceIntent(VoiceAction.OPEN_FLOW, 1.00f, original)
            q in setOf(
                "recents", "show recents", "show recent", "recent apps",
                "recents panel", "recent panel", "show recents panel", "jump back",
                "open recents"
            ) -> VoiceIntent(VoiceAction.OPEN_RECENTS, 0.99f, original)
            q in setOf(
                "app drawer", "all apps", "open drawer", "show drawer", "drawer",
                "show all apps", "open the drawer", "open app drawer", "open all apps"
            ) -> VoiceIntent(VoiceAction.OPEN_DRAWER, 0.99f, original)
            q in setOf("search", "open search", "show search", "open up search") ->
                VoiceIntent(VoiceAction.OPEN_SEARCH, 1.00f, original)
            q in setOf("personalize flow", "customise flow", "customize flow", "flow settings") ->
                VoiceIntent(VoiceAction.OPEN_PERSONALIZE, 0.99f, original)
            q in setOf(
                "lumen settings", "launcher settings", "open settings",
                "open lumen settings", "show settings"
            ) -> VoiceIntent(VoiceAction.OPEN_SETTINGS, 0.99f, original)
            q in setOf(
                "home", "go home", "open home", "show home", "home screen", "go to home"
            ) -> VoiceIntent(VoiceAction.OPEN_HOME, 1.00f, original)
            else -> null
        }
    }

    private fun openApp(q: String, original: String): VoiceIntent? {
        val match = Regex("""^(?:open up|open|launch|start|run|go to)\s+(.+)$""").find(q) ?: return null
        val name = match.groupValues[1].trim()
            .replace(Regex("""^(?:an?\s+)?app\s+(?:called|named)\s+"""), "")
            .replace(Regex("""^(?:called|named)\s+"""), "")
            .trim()
        if (name in reservedOpen) return null
        if (name.startsWith("flow")) return null
        if (name.startsWith("home") && name.length <= 10) return null
        return VoiceIntent(VoiceAction.OPEN_APP, 0.97f, original, appName = name)
    }

    private fun space(q: String, original: String): VoiceIntent? {
        val normalized = q
            .replaceFirst(Regex("""^(?:open|launch|go to|switch to)\s+the\s+"""), "open ")
            .replaceFirst(Regex("""^(?:launch|switch to)\s+"""), "open ")
        when (normalized) {
            "private", "private space", "locked space", "open private", "open locked",
            "open private space", "go to private", "go to private space" ->
                return VoiceIntent(VoiceAction.OPEN_PRIVATE, 0.99f, original)
            "work", "open work", "open work space", "open work mode", "open work layout",
            "go to work", "go to work space", "work space", "work layout", "work mode",
            "switch to work", "switch to work layout", "switch to work mode" ->
                return VoiceIntent(VoiceAction.SET_SPACE, 0.99f, original, space = SpaceKind.Work)
            "personal", "open personal", "open personal space", "open personal mode",
            "open personal layout", "go to personal", "go to personal space",
            "personal space", "personal layout", "switch to personal", "switch to personal space" ->
                return VoiceIntent(VoiceAction.SET_SPACE, 0.99f, original, space = SpaceKind.Personal)
            "home layout", "home space", "switch to home space", "open home space",
            "go to home space" ->
                return VoiceIntent(VoiceAction.SET_SPACE, 0.99f, original, space = SpaceKind.Home)
            "focus", "open focus", "open focus space", "open focus mode", "go to focus",
            "go to focus space", "focus space", "focus mode", "switch to focus",
            "switch to focus mode" ->
                return VoiceIntent(VoiceAction.SET_SPACE, 0.99f, original, space = SpaceKind.Focus)
            "automatic space", "auto space", "auto mode", "automatic", "clear space" ->
                return VoiceIntent(VoiceAction.SET_SPACE, 0.99f, original, space = null)
        }
        if (q == "put me in work mode" || q == "switch me into work") {
            return VoiceIntent(VoiceAction.SET_SPACE, 0.99f, original, space = SpaceKind.Work)
        }
        return null
    }

    private fun labels(q: String, original: String): VoiceIntent? {
        val label = Regex("""\b(?:app\s+)?(?:label|labels|names)\b""").containsMatchIn(q)
        if (!label && "labels off" !in q && "label off" !in q && "labels on" !in q && "label on" !in q) return null
        val hide = Regex("""\b(?:hide|remove|turn off|get rid of)\b""").containsMatchIn(q) ||
            q.endsWith(" off") || q.contains("labels off") || q.contains("label off")
        val show = Regex("""\b(?:show|unhide|turn on|display)\b""").containsMatchIn(q) ||
            q.contains("labels on") || q.contains("label on")
        if (hide && !show) return VoiceIntent(VoiceAction.SET_LABELS, 0.98f, original, enabled = false)
        if (show && !hide) return VoiceIntent(VoiceAction.SET_LABELS, 0.98f, original, enabled = true)
        return null
    }

    private fun grid(q: String, original: String): VoiceIntent? {
        val match = Regex("""(?:use|make|set|change to|fit)?\s*([3-6])\s*(?:column|columns|per row|on each row)""").find(q)
            ?: Regex("""^([3-6])\s+columns$""").find(q)
            ?: return null
        return VoiceIntent(VoiceAction.SET_GRID, 0.97f, original, intValue = match.groupValues[1].toInt())
    }

    private fun dockSize(q: String, original: String): VoiceIntent? {
        val match = Regex("""(?:dock size|dock capacity|dock)\s*(?:to|of|at)?\s*([3-6])\b""").find(q) ?: return null
        if (q.startsWith("dock ") && !q.contains("size") && !q.contains("capacity") && match.groupValues[0].startsWith("dock ")) {
            val rest = q.removePrefix("dock ").trim()
            if (rest.toIntOrNull() == null) return null
        }
        return VoiceIntent(VoiceAction.SET_DOCK_CAPACITY, 0.97f, original, intValue = match.groupValues[1].toInt())
    }

    private fun icons(q: String, original: String): VoiceIntent? {
        if (!q.contains("icon")) return null
        return when {
            q.contains("bigger") || q.contains("larger") || q.contains("increase") ->
                VoiceIntent(VoiceAction.SET_ICON_SIZE, 0.97f, original, floatValue = 8f)
            q.contains("smaller") || q.contains("decrease") || q.contains("tinier") ->
                VoiceIntent(VoiceAction.SET_ICON_SIZE, 0.97f, original, floatValue = -8f)
            q.contains("large icons") || q.contains("biggest icons") ->
                VoiceIntent(VoiceAction.SET_ICON_SIZE, 0.98f, original, floatValue = 72f, textValue = "absolute")
            q.contains("small icons") || q.contains("smallest icons") ->
                VoiceIntent(VoiceAction.SET_ICON_SIZE, 0.98f, original, floatValue = 44f, textValue = "absolute")
            q.contains("default icons") || q.contains("normal icons") ->
                VoiceIntent(VoiceAction.SET_ICON_SIZE, 0.98f, original, floatValue = 60f, textValue = "absolute")
            else -> null
        }
    }

    private fun folder(q: String, original: String): VoiceIntent? {
        Regex("""^(?:create|make)(?:\s+a)?\s+(.+?)\s+folder(?:\s+and)?\s+(?:add|put|with)\s+(.+)$""").find(q)?.let { match ->
            val apps = splitAppNames(match.groupValues[2])
            if (apps.isEmpty()) return@let
            return VoiceIntent(
                VoiceAction.CREATE_FOLDER, 0.98f, original,
                folderName = match.groupValues[1].trim(),
                appName = apps.first(),
                textValue = apps.drop(1).joinToString(",").ifBlank { null }
            )
        }
        Regex("""^(?:put|add)\s+(.+?)\s+(?:in|into|to)\s+(?:the\s+)?(.+?)\s+folder$""").find(q)?.let { match ->
            val apps = splitAppNames(match.groupValues[1])
            if (apps.isEmpty()) return@let
            return VoiceIntent(
                VoiceAction.ADD_TO_FOLDER, 0.98f, original,
                appName = apps.first(),
                folderName = match.groupValues[2].trim(),
                textValue = apps.drop(1).joinToString(",").ifBlank { null }
            )
        }
        Regex("""^(?:create|make)(?:\s+a)?\s+folder(?:\s+(?:called|named))\s+(.+)$""").find(q)?.let { match ->
            return VoiceIntent(VoiceAction.CREATE_FOLDER, 0.98f, original, folderName = match.groupValues[1].trim())
        }
        Regex("""^(?:create|make)(?:\s+a)?\s+folder\s+([a-z0-9]+)$""").find(q)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name != "a" && name != "new") {
                return VoiceIntent(VoiceAction.CREATE_FOLDER, 0.97f, original, folderName = name)
            }
        }
        Regex("""^(?:create|make)(?:\s+a)?\s+(.+?)\s+folder$""").find(q)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name == "a" || name == "new") {
                return VoiceIntent(VoiceAction.CREATE_FOLDER, 0.97f, original)
            }
            return VoiceIntent(VoiceAction.CREATE_FOLDER, 0.97f, original, folderName = name)
        }
        if (q in setOf("create folder", "new folder", "make a folder")) {
            return VoiceIntent(VoiceAction.CREATE_FOLDER, 1.00f, original)
        }
        return null
    }

    private fun homeApp(q: String, original: String): VoiceIntent? {
        Regex("""^hide\s+(.+?)\s+from\s+(?:my\s+)?home$""").find(q)?.let {
            val app = it.groupValues[1].trim()
            val remove = VoiceIntent(
                VoiceAction.UNPIN_APP, 0.90f, original,
                appName = app, destructive = true
            )
            val hide = VoiceIntent(
                VoiceAction.HIDE_APP, 0.90f, original,
                appName = app, destructive = true
            )
            return VoiceIntent(
                VoiceAction.CLARIFY, 0.90f, original,
                clarify = listOf("remove $app from Home", "hide $app from Lumen"),
                alternatives = listOf(remove, hide)
            )
        }
        Regex("""^remove\s+(.+?)\s+from\s+(?:my\s+)?home$""").find(q)?.let {
            return VoiceIntent(
                VoiceAction.UNPIN_APP, 0.97f, original,
                appName = it.groupValues[1].trim(), destructive = true
            )
        }
        starts(q, listOf("unhide ", "show hidden ", "show the hidden "))?.let {
            return VoiceIntent(VoiceAction.UNHIDE_APP, 0.97f, original, appName = it)
        }
        starts(q, listOf("unpin ", "remove from home ", "take off home "))?.let {
            return VoiceIntent(VoiceAction.UNPIN_APP, 0.97f, original, appName = it, destructive = true)
        }
        starts(q, listOf("remove from dock ", "undock ", "take off the dock "))?.let {
            return VoiceIntent(VoiceAction.REMOVE_FROM_DOCK, 0.97f, original, appName = it, destructive = true)
        }
        Regex("""^(?:add|put|move)\s+(.+?)\s+(?:to|on|onto)\s+(?:the\s+)?(?:my\s+)?dock$""").find(q)?.let {
            return VoiceIntent(VoiceAction.ADD_TO_DOCK, 0.97f, original, appName = it.groupValues[1].trim())
        }
        starts(q, listOf("add to dock ", "put on the dock ", "dock "))?.let { name ->
            if (name.startsWith("size") || name.startsWith("capacity")) return null
            if (name.isNotBlank() && name != "the" && name.toIntOrNull() == null) {
                return VoiceIntent(VoiceAction.ADD_TO_DOCK, 0.96f, original, appName = name)
            }
        }
        starts(q, listOf("pin ", "add to home ", "put on home ", "put on my home "))?.let { raw ->
            val name = raw
                .removeSuffix(" to home")
                .removeSuffix(" on home")
                .removeSuffix(" to my home")
                .trim()
            if (name.isNotBlank()) {
                return VoiceIntent(VoiceAction.PIN_APP, 0.97f, original, appName = name)
            }
        }
        starts(q, listOf("hide app ", "hide the "))?.let { name ->
            if (name.contains("news") || name.contains("label") || name.contains("flow")) return null
            return VoiceIntent(
                VoiceAction.HIDE_APP, 0.90f, original,
                appName = name.removeSuffix(" app").trim(),
                destructive = true
            )
        }
        if (Regex("""^hide\s+(?!news\b|labels?\b|app labels\b).+""").containsMatchIn(q)) {
            val name = q.removePrefix("hide ").removeSuffix(" app").trim()
            if (name.isNotBlank() && !name.contains("label") && name != "news") {
                return VoiceIntent(VoiceAction.HIDE_APP, 0.82f, original, appName = name, destructive = true)
            }
        }
        return null
    }

    private fun flow(q: String, original: String): VoiceIntent? {
        if (
            q.contains("my alarm") || q.contains("list alarm") || q.contains("next alarm") ||
            q.contains("any alarm") || q.contains("my task") || q.contains("task list") ||
            q.contains("my list") || q.contains("to do") || q.contains("remind me")
        ) {
            return null
        }
        if (Regex("""^(?:hide|remove)\s+news(?:\s+from\s+flow)?$""").containsMatchIn(q) || q == "news off") {
            return VoiceIntent(VoiceAction.SET_FLOW_MODULE, 0.98f, original, module = FlowModule.News, enabled = false)
        }
        if (Regex("""^(?:show|put|bring)\s+news(?:\s+on\s+flow)?$""").containsMatchIn(q) || q == "news on") {
            return VoiceIntent(VoiceAction.SET_FLOW_MODULE, 0.98f, original, module = FlowModule.News, enabled = true)
        }
        Regex("""^move\s+(.+?)\s+(?:above|before|over)\s+(.+)$""").find(q)?.let { match ->
            val moving = flowModuleFromSpeech(match.groupValues[1])
            val before = flowModuleFromSpeech(match.groupValues[2])
            if (moving != null && before != null && moving != before) {
                return VoiceIntent(
                    VoiceAction.MOVE_FLOW_MODULE, 0.98f, original,
                    module = moving, beforeModule = before
                )
            }
        }
        val off = q.startsWith("hide ") || q.startsWith("remove ") || q.startsWith("turn off ") ||
            q.endsWith(" off") || q.endsWith(" off flow")
        val on = q.startsWith("show ") || q.startsWith("turn on ") || q.endsWith(" on") ||
            q.endsWith(" on flow")
        if (off || on) {
            val module = flowModuleFromSpeech(q) ?: return null
            if (q.contains("label") || q.contains("column") || q.contains("icon")) return null
            return VoiceIntent(
                VoiceAction.SET_FLOW_MODULE, 0.96f, original,
                module = module, enabled = on && !off
            )
        }
        return null
    }

    private fun usage(q: String, original: String): VoiceIntent? {
        val usedYesterday = setOf(
            "app i used yesterday", "apps i used yesterday", "what did i use yesterday",
            "show yesterday's apps", "show yesterdays apps", "used yesterday"
        )
        if (q in usedYesterday || (q.contains("used yesterday") && !q.contains("install"))) {
            return VoiceIntent(VoiceAction.USED_YESTERDAY, 0.97f, original)
        }
        if (q.contains("yesterday") && q.contains("install")) return null
        val needNow = setOf(
            "what do i normally use now", "what do i usually use now", "what do i use now",
            "need now", "likely next", "what do i open", "what should i open"
        )
        if (q in needNow || q.contains("normally use") || q.contains("usually use")) {
            return VoiceIntent(VoiceAction.NEED_NOW, 0.96f, original)
        }
        val digest = setOf(
            "what did i miss", "what have i missed", "summarize my messages",
            "summarize my inbox", "unread digest", "any messages", "any new messages",
            "catch me up", "what did i miss today"
        )
        if (q in digest || (q.contains("summarize") && (q.contains("inbox") || q.contains("message")))) {
            return VoiceIntent(VoiceAction.INBOX_DIGEST, 0.96f, original)
        }
        return null
    }

    private fun purpose(q: String, original: String): VoiceIntent? {
        val phrases = listOf(
            "banking apps", "bank apps", "finance apps", "finance folder",
            "food apps", "music apps", "work apps", "photo editor", "editing photos"
        )
        if (phrases.any { q == it || q.endsWith(" $it") }) {
            return VoiceIntent(VoiceAction.PURPOSE, 0.97f, original, textValue = q)
        }
        if (q == "create a finance folder" || q == "create finance folder") {
            return VoiceIntent(VoiceAction.PURPOSE, 0.97f, original, textValue = q)
        }
        return null
    }

    private fun weather(q: String, original: String): VoiceIntent? {
        val flowCard = Regex("""\b(?:hide|remove|move|turn off|turn on)\b""").containsMatchIn(q) ||
            q.contains("on flow") || q.contains("from flow") ||
            (q.contains("weather") && (q.endsWith(" off") || q.endsWith(" on")))
        if (flowCard) return null
        if (
            q in setOf(
                "weather", "what's the weather", "whats the weather", "what s the weather",
                "how's the weather", "hows the weather", "how is the weather",
                "what's the weather like", "whats the weather like",
                "show weather", "show the weather", "weather today",
                "how hot is it", "how cold is it"
            ) || (q.contains("weather") && !q.contains("flow"))
        ) {
            return VoiceIntent(VoiceAction.WEATHER, 0.97f, original)
        }
        if (q.contains("temperature") && q.contains("outside")) {
            return VoiceIntent(VoiceAction.WEATHER, 0.90f, original)
        }
        return null
    }

    private fun calendar(q: String, original: String): VoiceIntent? {
        if (q in setOf(
                "next meeting", "what's next", "whats next", "what s next", "next event",
                "my calendar", "what's on my calendar", "whats on my calendar",
                "what's next on my calendar", "whats next on my calendar", "on my calendar",
                "up next"
            )
        ) {
            return VoiceIntent(VoiceAction.NEXT_EVENT, 0.97f, original)
        }
        return null
    }

    private fun alarm(q: String, original: String): VoiceIntent? {
        return when (val command = VoiceCommands.alarmCommand(q)) {
            is VoiceCommands.AlarmCommand.Set -> VoiceIntent(
                VoiceAction.SET_ALARM, 0.98f, original,
                hour = command.hour, minute = command.minute, daily = command.daily
            )
            is VoiceCommands.AlarmCommand.Cancel -> VoiceIntent(
                VoiceAction.CANCEL_ALARM, 0.96f, original,
                hour = command.hour, minute = command.minute, destructive = true
            )
            VoiceCommands.AlarmCommand.List -> VoiceIntent(VoiceAction.LIST_ALARMS, 0.98f, original)
            null -> null
        }
    }

    private fun tasks(q: String, original: String): VoiceIntent? {
        if (VoiceCommands.showTasks(q)) return VoiceIntent(VoiceAction.SHOW_TASKS, 0.98f, original)
        VoiceCommands.completeTask(q)?.let {
            return VoiceIntent(VoiceAction.COMPLETE_TASK, 0.96f, original, textValue = it)
        }
        VoiceCommands.deleteTask(q)?.let {
            return VoiceIntent(VoiceAction.DELETE_TASK, 0.96f, original, textValue = it)
        }
        VoiceCommands.task(q)?.let {
            return VoiceIntent(VoiceAction.SET_REMINDER, 0.97f, original, textValue = it)
        }
        return null
    }

    private fun privateApp(q: String, original: String): VoiceIntent? {
        val space = Regex("""(?:the\s+)?(?:private|locked)\s+space""")
        Regex("""^(?:remove|take)\s+(.+?)\s+(?:from|out of|off)\s+$space$""").find(q)?.let {
            return VoiceIntent(
                VoiceAction.REMOVE_FROM_PRIVATE, 0.97f, original,
                appName = it.groupValues[1].trim()
            )
        }
        Regex("""^(?:move|put|add|hide)\s+(.+?)\s+(?:to|in|into|on)\s+$space$""").find(q)?.let {
            val name = it.groupValues[1].trim()
            if (name.contains("news") || name.contains("weather")) return null
            return VoiceIntent(
                VoiceAction.MOVE_TO_PRIVATE, 0.97f, original,
                appName = name, destructive = true
            )
        }
        return null
    }

    private fun whereApp(q: String, original: String): VoiceIntent? {
        Regex("""^(?:where(?:s| is| did i put)?)\s+(.+?)$""").find(q)?.let {
            val name = it.groupValues[1]
                .removePrefix("is ")
                .removeSuffix(" on home")
                .removeSuffix(" in lumen")
                .trim()
            if (name.isBlank() || name in reservedOpen) return null
            return VoiceIntent(VoiceAction.WHERE_APP, 0.97f, original, appName = name)
        }
        return null
    }

    private fun alias(q: String, original: String): VoiceIntent? {
        Regex("""^(?:remember|save|learn)\s+(\w{2,8})\s+(?:as|for|means)\s+(.+)$""").find(q)?.let {
            return VoiceIntent(
                VoiceAction.LEARN_ALIAS, 0.97f, original,
                appName = it.groupValues[2].trim(),
                textValue = it.groupValues[1].trim()
            )
        }
        Regex("""^(\w{2,8})\s+means\s+(.+)$""").find(q)?.let {
            return VoiceIntent(
                VoiceAction.LEARN_ALIAS, 0.96f, original,
                appName = it.groupValues[2].trim(),
                textValue = it.groupValues[1].trim()
            )
        }
        return null
    }

    private fun splitAppNames(raw: String): List<String> {
        return raw
            .split(Regex("""\s*(?:,|&| and )\s*"""))
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "and" }
    }

    private fun starts(q: String, prefixes: List<String>): String? {
        for (prefix in prefixes) {
            if (q.startsWith(prefix)) return q.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
        }
        return null
    }
}

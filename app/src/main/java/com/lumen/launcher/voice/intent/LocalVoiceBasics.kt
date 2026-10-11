package com.lumen.launcher.voice.intent

/** Offline rules. Match command boundaries; never rewrite a message's contents. */
internal object LocalVoiceBasics {
    private val polite = Regex("""^(?:(?:hey|hi|hello)(?: lumen)?|lumen|please|can you|could you|would you|will you|i want to|i need to)\s+""", RegexOption.IGNORE_CASE)
    fun commandText(raw: String): String {
        var text = raw.trim()
        while (polite.containsMatchIn(text)) text = text.replaceFirst(polite, "").trim()
        return text
    }
    fun parse(raw: String, appLabels: List<String> = emptyList()): VoiceIntent? {
        val text = commandText(raw)
        fun match(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).matchEntire(text)
        fun hit(action: VoiceAction, app: String? = null, body: String? = null, number: Int? = null, on: Boolean? = null) =
            VoiceIntent(action, 0.98f, raw, appName = app, textValue = body, intValue = number, enabled = on)
        fun contact(action: VoiceAction, who: String, body: String? = null): VoiceIntent {
            val recipient = who.trim().replace(Regex("""\s+please$""", RegexOption.IGNORE_CASE), "")
            val match = Regex("""\s+(?:on|in|via|using|through)\s+(.+)$""", RegexOption.IGNORE_CASE).find(recipient)
            fun appKey(name: String) = name.lowercase().replace(" ", "")
            val channel = match?.let { m ->
                (appLabels + listOf("WhatsApp", "Telegram", "Viber", "Messages", "SMS"))
                    .firstOrNull { appKey(it) == appKey(m.groupValues[1].trim()) }
            }
            return VoiceIntent(action, .98f, raw,
                recipientName = (if (channel != null) recipient.substring(0, match!!.range.first) else recipient)
                    .trim().replaceFirst(Regex("""^to\s+""", RegexOption.IGNORE_CASE), ""),
                messagingApp = channel, textValue = body)
        }
        val request = when (text.lowercase()) {
            "call", "phone", "dial", "call to" -> VoiceAction.CALL to VoiceSlot.CONTACT
            "text", "message", "send a message", "send a text" -> VoiceAction.SEND_MESSAGE to VoiceSlot.CONTACT
            "timer", "set timer", "set a timer", "start timer", "start a timer" -> VoiceAction.SET_TIMER to VoiceSlot.DURATION
            "alarm", "set alarm", "set an alarm", "put alarm", "put an alarm" -> VoiceAction.SET_ALARM to VoiceSlot.ALARM_TIME
            "open", "launch", "open an app" -> VoiceAction.OPEN_APP to VoiceSlot.APP
            else -> null
        }
        if (request != null) return missing(request.first, request.second, raw)
        match("""whatsapp (?!call\b)(.+)""")?.let {
            return parse("text ${it.groupValues[1]}", appLabels)?.copy(originalText = raw, messagingApp = "WhatsApp")
        }
        match("""whatsapp call (.+)""")?.let { return contact(VoiceAction.CALL, it.groupValues[1]).copy(messagingApp = "WhatsApp") }
        match("""(?:call|phone|dial)\s+(.+)""")?.let { return contact(VoiceAction.CALL, it.groupValues[1]) }
        match("""(?:text|message|send (?:a )?(?:message|text) to)\s+(.+)""")?.let {
            val rest = it.groupValues[1].trim()
            val parts = Regex("""\s+(?:saying|that says|with the message)\s+|\s*[:,]\s*""", RegexOption.IGNORE_CASE).split(rest, 2)
            if (parts.size == 2) return contact(VoiceAction.SEND_MESSAGE, parts[0], parts[1].trim())
            val bodyStart = Regex("""\s+(?=(?:I(?:['\u2019]m| am|'ll| will)?|we|you|please|see|can|could|will|meet|bring|sorry|running|the|it|thanks|thank|hello|hi|call)\b)""", RegexOption.IGNORE_CASE).find(rest)
            return if (bodyStart != null) contact(VoiceAction.SEND_MESSAGE, rest.substring(0, bodyStart.range.first), rest.substring(bodyStart.range.last + 1))
            else contact(VoiceAction.SEND_MESSAGE, rest)
        }
        match("""(?:(?:set|start)(?: a)? )?timer(?: for)?\s+(.+)""")?.let {
            val seconds = VoiceDuration.seconds(it.groupValues[1])
                ?: return missing(VoiceAction.SET_TIMER, VoiceSlot.DURATION, raw)
            return hit(VoiceAction.SET_TIMER, number = (seconds / 60).takeIf { seconds % 60 == 0 }).copy(durationSeconds = seconds)
        }
        val clock = when (text.lowercase().replace("'", "").replace("\u2019", "")) {
            "what time is it", "whats the time", "what is the time", "tell me the time" -> ClockQuery.TIME
            "whats the date today", "what is the date today", "whats todays date", "what is todays date", "what date is it" -> ClockQuery.DATE
            "what day is it", "what day is it today", "what day is today" -> ClockQuery.DAY
            else -> null
        }
        if (clock != null) return hit(VoiceAction.ANSWER).copy(clockQuery = clock)
        match("""(?:navigate(?: to)?|directions to)\s+(.+)""")?.let { return hit(VoiceAction.NAVIGATE, body = it.groupValues[1]) }
        match("""(?:(?:turn|switch)\s+(?:the\s+)?)?(on|off)\s+(?:the\s+)?(?:flashlight|torch)""")?.let { return hit(VoiceAction.TOGGLE_TORCH, on = it.groupValues[1].equals("on", true)) }
        match("""(?:(?:turn|switch)\s+(?:the\s+)?)?(?:flashlight|torch)\s+(on|off)""")?.let { return hit(VoiceAction.TOGGLE_TORCH, on = it.groupValues[1].equals("on", true)) }
        match("""(?:toggle (?:the )?)?(?:flashlight|torch)""")?.let { return hit(VoiceAction.TOGGLE_TORCH) }
        match("""play\s+(.+?)\s+(?:on|in|using)\s+(.+)""")?.let { return hit(VoiceAction.PLAY_MEDIA, app = it.groupValues[2], body = it.groupValues[1].removePrefix("some ")) }
        match("""play\s+(.+)""")?.let { return hit(VoiceAction.PLAY_MEDIA, body = it.groupValues[1]) }
        match("""(?:what(?:['\u2019]?s| is) on|show(?: me)?) (?:my |the )?calendar(?: today)?""")?.let { return hit(VoiceAction.NEXT_EVENT, body = if (text.endsWith("today", true)) "today" else null) }
        match("""(?:add|put)\s+(.+?)\s+(?:to|on|in)\s+(?:my |the |a )?(?:shopping |grocery |todo |to-do )?list""")?.let { return hit(VoiceAction.SET_REMINDER, body = it.groupValues[1]) }
        return null
    }
    fun missing(action: VoiceAction, slot: VoiceSlot, raw: String): VoiceIntent {
        val prompt = when (slot) {
            VoiceSlot.CONTACT -> if (action == VoiceAction.CALL) "Who should I call?" else "Who should I message?"
            VoiceSlot.DURATION -> "For how long?"
            VoiceSlot.ALARM_TIME -> "For what time?"
            VoiceSlot.APP -> "Which app?"
        }
        return VoiceIntent(VoiceAction.ASK_USER, .98f, raw, missingSlot = slot, textValue = prompt,
            alternatives = listOf(VoiceIntent(action, .98f, raw)))
    }
}

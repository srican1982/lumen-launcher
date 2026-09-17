package com.lumen.launcher.flow

enum class FlowModule {
    Next, Weather, Missed, Inbox, Continue, NeedNow, News, Alarms, Reminders;

    val title: String
        get() = when (this) {
            NeedNow -> "Need now"
            else -> name
        }
}

fun defaultFlowOrder(): List<FlowModule> = listOf(
    FlowModule.Next,
    FlowModule.Weather,
    FlowModule.Missed,
    FlowModule.Inbox,
    FlowModule.Continue,
    FlowModule.NeedNow,
    FlowModule.News,
    FlowModule.Alarms,
    FlowModule.Reminders
)

fun defaultFlowNames(): List<String> = defaultFlowOrder().map { it.name }

fun defaultFlowEnabled(): Set<String> = defaultFlowNames().toSet()

fun parseFlowOrder(names: List<String>): List<FlowModule> {
    val seen = LinkedHashSet<FlowModule>()
    names.forEach { name ->
        runCatching { FlowModule.valueOf(name) }.getOrNull()?.let { seen += it }
    }
    defaultFlowOrder().forEach { seen += it }
    return seen.toList()
}

fun flowModuleFromSpeech(raw: String): FlowModule? {
    val q = raw.lowercase().trim()
    return when {
        q.contains("need now") || q.contains("neednow") -> FlowModule.NeedNow
        q.contains("inbox") || q.contains("email") || q.contains("mail") -> FlowModule.Inbox
        q.contains("weather") -> FlowModule.Weather
        q.contains("missed") || q.contains("calls") -> FlowModule.Missed
        q.contains("news") -> FlowModule.News
        q.contains("alarm") -> FlowModule.Alarms
        q.contains("remind") || q.contains("task") -> FlowModule.Reminders
        q.contains("continue") || q.contains("resume") -> FlowModule.Continue
        q.contains("next") || q.contains("calendar") || q.contains("meeting") -> FlowModule.Next
        else -> null
    }
}

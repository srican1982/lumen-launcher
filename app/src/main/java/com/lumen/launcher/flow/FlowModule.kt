package com.lumen.launcher.flow

enum class FlowModule {
    Alarms, Next, Missed, Inbox, Weather, NeedNow, Continue, Reminders, News
}

fun flowOrder(): List<FlowModule> = listOf(
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

package com.lumen.launcher.inbox

import android.app.PendingIntent
import com.lumen.launcher.data.CalendarEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MeetingHub {
    private val _items = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val items: StateFlow<List<CalendarEvent>> = _items.asStateFlow()
    private val intents = LinkedHashMap<String, PendingIntent>()

    fun publish(next: List<CalendarEvent>, nextIntents: Map<String, PendingIntent>) {
        synchronized(intents) {
            intents.clear()
            intents.putAll(nextIntents)
        }
        _items.value = next.take(6)
    }

    fun contentIntent(key: String): PendingIntent? = synchronized(intents) { intents[key] }
}

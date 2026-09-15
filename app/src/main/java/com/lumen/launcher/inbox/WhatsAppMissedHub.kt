package com.lumen.launcher.inbox

import android.app.PendingIntent
import com.lumen.launcher.data.MissedCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WhatsAppMissedSnapshot(
    val missed: List<MissedCall> = emptyList(),
    val outgoing: Set<String> = emptySet()
)

object WhatsAppMissedHub {
    private val _snapshot = MutableStateFlow(WhatsAppMissedSnapshot())
    val snapshot: StateFlow<WhatsAppMissedSnapshot> = _snapshot.asStateFlow()
    private val intents = LinkedHashMap<String, PendingIntent>()

    fun publish(next: WhatsAppMissedSnapshot, nextIntents: Map<String, PendingIntent>) {
        synchronized(intents) {
            intents.clear()
            intents.putAll(nextIntents)
        }
        _snapshot.value = next
    }

    fun contentIntent(key: String): PendingIntent? = synchronized(intents) { intents[key] }
}

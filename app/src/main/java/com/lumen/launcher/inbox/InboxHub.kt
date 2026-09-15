package com.lumen.launcher.inbox

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object InboxHub {
    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()
    private val intents = LinkedHashMap<String, PendingIntent>()

    fun publish(next: List<InboxItem>, nextIntents: Map<String, PendingIntent>) {
        synchronized(intents) {
            intents.clear()
            intents.putAll(nextIntents)
        }
        _items.value = next.take(8)
    }

    fun contentIntent(key: String): PendingIntent? = synchronized(intents) { intents[key] }

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val me = ComponentName(context, LumenNotificationListener::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) || it.contains(context.packageName) }
    }
}

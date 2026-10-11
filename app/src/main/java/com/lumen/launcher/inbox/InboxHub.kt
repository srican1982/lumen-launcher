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
    private val _conversations = MutableStateFlow<List<InboxItem>>(emptyList())
    val conversations: StateFlow<List<InboxItem>> = _conversations.asStateFlow()
    private val intents = LinkedHashMap<String, PendingIntent>()

    private val shortcuts = LinkedHashMap<String, Pair<String, android.os.UserHandle>>()
    fun registerShortcut(key: String, id: String, user: android.os.UserHandle) {
        synchronized(intents) { shortcuts[key] = id to user }
    }
    private val bubbleIntents = LinkedHashMap<String, PendingIntent>()
    fun registerBubble(key: String, pending: PendingIntent) {
        synchronized(intents) { bubbleIntents[key] = pending }
    }

    /** Called only from an explicit user tap, with the visible launcher Activity. */
    fun sendFromTap(context: Context, pending: PendingIntent): Boolean = runCatching {
        val options = android.app.ActivityOptions.makeBasic()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }
        pending.send(context, 0, null, null, null, null, options.toBundle())
    }.isSuccess

    fun openConversation(context: Context, item: InboxItem): Boolean {
        val pending = contentIntent(item.key)
        // The individual notification is the most specific route to this message thread.
        if (pending != null && (android.os.Build.VERSION.SDK_INT < 31 || pending.isActivity) && sendFromTap(context, pending)) return true
        val bubble = synchronized(intents) { bubbleIntents[item.key] }
        if (bubble != null && (android.os.Build.VERSION.SDK_INT < 31 || bubble.isActivity) && sendFromTap(context, bubble)) return true
        val shortcut = synchronized(intents) { shortcuts[item.key] }
        val launcher = context.getSystemService(android.content.pm.LauncherApps::class.java)
        if (shortcut != null && launcher.hasShortcutHostPermission()) {
            if (runCatching { launcher.startShortcut(item.packageName, shortcut.first, null, null, shortcut.second) }.isSuccess) return true
        }
        // A broadcast/service PendingIntent can succeed without opening any UI.
        // Fall back to the originating app when the conversation activity is unavailable.
        return runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(item.packageName) ?: return false
            context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun publish(next: List<InboxItem>, nextIntents: Map<String, PendingIntent>) {
        val recent = ConversationRecents.latest(next + _conversations.value, System.currentTimeMillis(), 32)
        synchronized(intents) {
            intents.putAll(nextIntents)
            val keep = (next + recent).map { it.key }.toSet()
            intents.keys.retainAll(keep)
            shortcuts.keys.retainAll(keep)
            bubbleIntents.keys.retainAll(keep)
        }
        _items.value = next
        _conversations.value = recent
    }

    fun clear() {
        synchronized(intents) { intents.clear(); shortcuts.clear(); bubbleIntents.clear() }
        _items.value = emptyList()
        _conversations.value = emptyList()
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

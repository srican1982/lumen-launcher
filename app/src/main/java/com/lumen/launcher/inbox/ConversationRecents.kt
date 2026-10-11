package com.lumen.launcher.inbox

object ConversationRecents {
    fun isSystemNotice(title: String): Boolean {
        val text = title.trim().lowercase()
        return listOf("recent whatsapp login", "whatsapp web", "whatsapp desktop", "backup", "backing up",
            "new login", "login code", "security notification", "security alert", "linked device", "device linked",
            "checking for new messages", "restoring messages", "connecting to whatsapp").any { text.contains(it) }
    }

    fun latest(items: List<InboxItem>, now: Long, limit: Int = 5): List<InboxItem> = items
        .filter { it.isConversation && !isSystemNotice(it.title) && it.postedAt >= now - 86_400_000L && it.source in setOf(
            InboxSource.Messages, InboxSource.WhatsApp, InboxSource.Telegram, InboxSource.Messenger
        ) }
        .sortedByDescending { it.postedAt }
        .distinctBy { it.packageName to (it.conversationId ?: it.title.trim().lowercase()) }
        .take(limit)
}

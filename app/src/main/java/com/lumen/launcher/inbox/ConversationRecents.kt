package com.lumen.launcher.inbox

object ConversationRecents {
    fun latest(items: List<InboxItem>, now: Long, limit: Int = 5): List<InboxItem> = items
        .filter { it.isConversation && it.postedAt >= now - 86_400_000L && it.source in setOf(
            InboxSource.Messages, InboxSource.WhatsApp, InboxSource.Telegram, InboxSource.Messenger
        ) }
        .sortedByDescending { it.postedAt }
        .distinctBy { it.packageName to (it.conversationId ?: it.title.trim().lowercase()) }
        .take(limit)
}

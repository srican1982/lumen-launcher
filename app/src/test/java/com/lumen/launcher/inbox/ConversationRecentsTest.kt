package com.lumen.launcher.inbox

import org.junit.Assert.*
import org.junit.Test

class ConversationRecentsTest {
    private val now = 200_000_000L
    private fun item(name: String, time: Long, source: InboxSource = InboxSource.WhatsApp, id: String? = null) =
        InboxItem("$name-$time", source, name, "hello", source.packages.first(), time, isConversation = true, conversationId = id)

    @Test fun returnsFiveNewestUniqueChatsAcrossApps() {
        val input = (1..8).map { item("Person $it", now-it) } + item("Person 1", now-90)
        assertEquals((1..5).map { "Person $it" }, ConversationRecents.latest(input.shuffled(), now).map { it.title })
    }
    @Test fun messagesSurviveUnrelatedInboxTrafficAndReadNotifications() {
        val previous = item("WhatsApp friend",now-100)
        val emails = (1..30).map { item("Email $it",now-it,InboxSource.Gmail) }
        assertEquals(listOf(previous), ConversationRecents.latest(emails + previous,now))
    }
    @Test fun removesExpiredAndNonConversationItemsAndUsesShortcutIdentity() {
        val old = item("Old",now-86_400_001)
        val summary = item("WhatsApp",now).copy(isConversation=false)
        val older = item("Name before",now-100,id="chat-id")
        val newer = item("Name now",now-1,id="chat-id")
        assertEquals(listOf(newer),ConversationRecents.latest(listOf(old,summary,older,newer),now))
    }
}

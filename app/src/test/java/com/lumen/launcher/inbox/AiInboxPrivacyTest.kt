package com.lumen.launcher.inbox

import org.junit.Assert.*
import org.junit.Test

class AiInboxPrivacyTest {
    private fun item(pkg: String, secret: Boolean = false) = InboxItem("key", InboxSource.Messages,
        "Your code 123456", "Login 9876 or 87654321", pkg, 0, secret = secret)
    @Test fun stripsCodesBeforePromptConstruction() {
        val safe = AiInboxPrivacy.filter(listOf(item("messages")), emptySet()).single()
        assertFalse(safe.title.contains("123456"))
        assertFalse(safe.preview.contains("9876"))
        assertFalse(safe.preview.contains("87654321"))
    }
    @Test fun skipsSecretHiddenAndPrivateSources() {
        assertTrue(AiInboxPrivacy.filter(listOf(item("private"), item("hidden"), item("secret", true)), setOf("private", "hidden")).isEmpty())
    }
    @Test fun doesNotMutateLocalNotification() {
        val original = item("messages")
        AiInboxPrivacy.filter(listOf(original), emptySet())
        assertTrue(original.title.contains("123456"))
    }
}

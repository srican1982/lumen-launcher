package com.lumen.launcher.inbox

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InboxDigestTest {

    private val items = listOf(
        InboxItem("1", InboxSource.Gmail, "Ali", "Docs are ready", "com.google.android.gm", 10),
        InboxItem("2", InboxSource.WhatsApp, "Mom", "Running late", "com.whatsapp", 11),
        InboxItem("3", InboxSource.Gmail, "2 new emails", "x@y.com", "com.google.android.gm", 12)
    )

    @Test
    fun localSkipsDigestRowsAndCountsTheRest() {
        val text = InboxDigest.local(items)
        assertThat(text).startsWith("2 new.")
        assertThat(text).contains("Ali: Docs are ready")
        assertThat(text).contains("Mom: Running late")
        assertThat(text).doesNotContain("2 new emails")
    }

    @Test
    fun fingerprintChangesWhenMailChanges() {
        val first = InboxDigest.fingerprint(items)
        val second = InboxDigest.fingerprint(items.drop(1))
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun noKeyKeepsTheLocalSummary() {
        assertThat(InboxDigest.summarize("", items)).isEqualTo(InboxDigest.local(items))
    }
}

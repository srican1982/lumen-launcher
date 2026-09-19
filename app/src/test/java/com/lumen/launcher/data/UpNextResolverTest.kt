package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import com.lumen.launcher.inbox.InboxItem
import com.lumen.launcher.inbox.InboxSource
import org.junit.Test

class UpNextResolverTest {

    @Test
    fun meetingWithPlaceBecomesNavigate() {
        val event = CalendarEvent(
            id = 3,
            title = "Sync with Ali",
            begin = 20 * 60_000L,
            end = 80 * 60_000L,
            location = "Building 4"
        )
        val next = UpNextResolver.resolve(0L, listOf(event), emptyList())
        assertThat(next?.kind).isEqualTo(UpNext.Kind.Meeting)
        assertThat(next?.actionLabel).isEqualTo("Navigate")
        assertThat(next?.mapsQuery).isEqualTo("Building 4")
        assertThat(next?.spoken).contains("Sync with Ali")
    }

    @Test
    fun flightEventBeatsMail() {
        val event = CalendarEvent(
            id = 4,
            title = "UA 221",
            begin = 40 * 60_000L,
            end = 4 * 60 * 60_000L,
            location = "SFO Airport"
        )
        val mail = InboxItem("m1", InboxSource.Gmail, "Your order shipped", "On the way", "com.google.android.gm", 1L)
        val next = UpNextResolver.resolve(0L, listOf(event), listOf(mail))
        assertThat(next?.kind).isEqualTo(UpNext.Kind.Flight)
        assertThat(next?.title).isEqualTo("UA 221")
    }

    @Test
    fun deliveryFromInboxWhenCalendarIsEmpty() {
        val mail = InboxItem(
            "d1",
            InboxSource.Gmail,
            "Your package is out for delivery",
            "Arriving today",
            "com.google.android.gm",
            1L
        )
        val next = UpNextResolver.resolve(0L, emptyList(), listOf(mail))
        assertThat(next?.kind).isEqualTo(UpNext.Kind.Delivery)
        assertThat(next?.actionLabel).isEqualTo("Open")
    }

    @Test
    fun recentChatBecomesMessage() {
        val chat = InboxItem(
            "w1",
            InboxSource.WhatsApp,
            "Mom",
            "Call me when you land",
            "com.whatsapp",
            5 * 60_000L
        )
        val next = UpNextResolver.resolve(10 * 60_000L, emptyList(), listOf(chat))
        assertThat(next?.kind).isEqualTo(UpNext.Kind.Message)
        assertThat(next?.title).isEqualTo("Mom")
    }
}

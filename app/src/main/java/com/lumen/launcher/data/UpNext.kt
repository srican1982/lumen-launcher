package com.lumen.launcher.data

import com.lumen.launcher.inbox.InboxItem
import com.lumen.launcher.inbox.InboxSource

data class UpNext(
    val kind: Kind,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val spoken: String,
    val mapsQuery: String = "",
    val event: CalendarEvent? = null,
    val inbox: InboxItem? = null
) {
    enum class Kind { Meeting, Flight, Delivery, Message }
}

object UpNextResolver {

    fun resolve(
        now: Long,
        events: List<CalendarEvent>,
        inbox: List<InboxItem>
    ): UpNext? {
        eventCard(now, events)?.let { return it }
        inbox.filterNot { it.isDigest }.forEach { item ->
            flightCard(item)?.let { return it }
        }
        inbox.filterNot { it.isDigest }.forEach { item ->
            deliveryCard(item)?.let { return it }
        }
        return recentMessage(now, inbox)
    }

    private fun eventCard(now: Long, events: List<CalendarEvent>): UpNext? {
        val event = events.firstOrNull { candidate ->
            candidate.end > now - 60_000L && candidate.begin <= now + 2 * 60 * 60_000L
        } ?: return null
        val whenText = relative(now, event.begin, event.end)
        val travel = travel(event)
        val place = event.location.trim().takeIf { it.isNotBlank() && !it.contains("http", true) }
        return if (travel) {
            UpNext(
                kind = UpNext.Kind.Flight,
                title = event.title,
                detail = listOfNotNull(whenText, place).joinToString(" · "),
                actionLabel = if (place != null) "Navigate" else "Open",
                spoken = "${event.title} is $whenText.",
                mapsQuery = place.orEmpty(),
                event = event
            )
        } else {
            UpNext(
                kind = UpNext.Kind.Meeting,
                title = event.title,
                detail = listOfNotNull(whenText, place).joinToString(" · "),
                actionLabel = if (place != null) "Navigate" else "Open",
                spoken = "${event.title} is $whenText.",
                mapsQuery = place.orEmpty(),
                event = event
            )
        }
    }

    private fun flightCard(item: InboxItem): UpNext? {
        val hay = "${item.title} ${item.preview}".lowercase()
        if (listOf("flight", "boarding", "gate", "boarding pass").none { hay.contains(it) }) return null
        return UpNext(
            kind = UpNext.Kind.Flight,
            title = item.title.ifBlank { "Flight update" },
            detail = item.preview.ifBlank { item.source.title },
            actionLabel = "Open",
            spoken = "Flight update: ${item.title}.",
            inbox = item
        )
    }

    private fun deliveryCard(item: InboxItem): UpNext? {
        val hay = "${item.title} ${item.preview}".lowercase()
        if (
            listOf("out for delivery", "delivered", "shipped", "package arriving", "your order")
                .none { hay.contains(it) }
        ) return null
        return UpNext(
            kind = UpNext.Kind.Delivery,
            title = item.title.ifBlank { "Delivery" },
            detail = item.preview.ifBlank { item.source.title },
            actionLabel = "Open",
            spoken = "Delivery update: ${item.title}.",
            inbox = item
        )
    }

    private fun recentMessage(now: Long, inbox: List<InboxItem>): UpNext? {
        val chats = setOf(
            InboxSource.Messages,
            InboxSource.WhatsApp,
            InboxSource.Telegram,
            InboxSource.Messenger,
            InboxSource.Slack,
            InboxSource.Teams
        )
        val item = inbox.firstOrNull {
            !it.isDigest && it.source in chats && now - it.postedAt <= 30 * 60_000L
        } ?: return null
        return UpNext(
            kind = UpNext.Kind.Message,
            title = item.title.ifBlank { "New message" },
            detail = item.preview.ifBlank { item.source.title },
            actionLabel = "Open",
            spoken = "New message from ${item.title}.",
            inbox = item
        )
    }

    private fun travel(event: CalendarEvent): Boolean {
        val hay = "${event.title} ${event.location}".lowercase()
        return listOf("flight", "gate", "boarding", "airport", "terminal", "train", "station")
            .any { hay.contains(it) }
    }

    private fun relative(now: Long, begin: Long, end: Long): String {
        if (begin <= now && end > now) return "happening now"
        val minutes = ((begin - now) / 60_000L).toInt()
        return when {
            minutes <= 1 -> "in a minute"
            minutes < 60 -> "in $minutes minutes"
            minutes < 120 -> "in about an hour"
            else -> "in ${minutes / 60} hours"
        }
    }
}

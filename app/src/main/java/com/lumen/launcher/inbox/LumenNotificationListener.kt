package com.lumen.launcher.inbox

import com.lumen.launcher.media.NowPlayingRepository
import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.lumen.launcher.badge.NotificationBadgeRepository
import com.lumen.launcher.data.CalendarEvent
import com.lumen.launcher.data.CallLogRepository
import com.lumen.launcher.data.MissedCall

class LumenNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishActive()
        // Same access lets Lumen follow what is playing (TouchPad mini player).
        NowPlayingRepository.start(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBadgeRepository.clear()
        NowPlayingRepository.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publishActive()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publishActive()
    }

    /** Channel importance / badge settings changed: recount badges only (cheap, no inbox reparse). */
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        super.onNotificationRankingUpdate(rankingMap)
        val notes = runCatching { activeNotifications }.getOrNull().orEmpty()
        NotificationBadgeRepository.rebuild(notes.toList(), packageName, rankingMap ?: currentRanking)
    }

    private fun publishActive() {
        val notes = runCatching { activeNotifications }.getOrNull().orEmpty()
        val ranking = runCatching { currentRanking }.getOrNull()
        NotificationBadgeRepository.rebuild(notes.toList(), packageName, ranking)
        // Music apps attach their player to their notification: use it for the TouchPad mini player.
        NowPlayingRepository.updateFromNotifications(this, notes.mapNotNull { mediaToken(it) })
        val items = ArrayList<InboxItem>()
        val intents = LinkedHashMap<String, PendingIntent>()
        val meetings = ArrayList<CalendarEvent>()
        val meetingIntents = LinkedHashMap<String, PendingIntent>()
        notes
            .filterNot { it.isOngoing }
            .sortedByDescending { it.postTime }
            .forEach { sbn ->
                val meeting = MeetingParser.parse(sbn)
                if (meeting != null) {
                    val already = meetings.any {
                        it.title.equals(meeting.title, true) &&
                            kotlin.math.abs(it.begin - meeting.begin) < 120_000L
                    }
                    if (!already) {
                        meetings += meeting
                        sbn.notification.contentIntent?.let { meetingIntents[meeting.noticeKey] = it }
                    }
                    return@forEach
                }
                if (InboxSource.fromPackage(sbn.packageName) == null) return@forEach
                val parsed = parse(sbn) ?: return@forEach
                if (items.none { it.title == parsed.title && it.source == parsed.source }) {
                    items += parsed
                    sbn.notification.contentIntent?.let { intents[parsed.key] = it }
                }
            }
        InboxHub.publish(items, intents)
        MeetingHub.publish(meetings.sortedBy { it.begin }, meetingIntents)
        publishWhatsApp(notes.toList())
    }

    private fun publishWhatsApp(notes: List<StatusBarNotification>) {
        val missed = ArrayList<MissedCall>()
        val outgoing = HashSet<String>()
        val intents = LinkedHashMap<String, PendingIntent>()
        notes
            .filter { it.packageName in WHATSAPP }
            .sortedByDescending { it.postTime }
            .forEach { sbn ->
                val extras = sbn.notification.extras
                val name = extras.char(Notification.EXTRA_CONVERSATION_TITLE)
                    ?: extras.char(Notification.EXTRA_TITLE)
                    ?: return@forEach
                if (name.equals("WhatsApp", true) || name.equals("WhatsApp Business", true)) return@forEach
                val preview = extras.char(Notification.EXTRA_TEXT)
                    ?: extras.char(Notification.EXTRA_BIG_TEXT)
                    ?: extras.char(Notification.EXTRA_SUB_TEXT)
                    ?: ""
                val hay = "$preview ${sbn.notification.category.orEmpty()}".lowercase()
                if (isWhatsAppOutgoing(sbn, hay)) {
                    outgoing += CallLogRepository.missedKey("", name, true)
                    outgoing += CallLogRepository.missedKey("", name, false)
                } else if (isWhatsAppMissed(sbn, preview, sbn.notification.category.orEmpty())) {
                    val call = MissedCall(
                        number = "",
                        name = name,
                        at = sbn.postTime,
                        count = 1,
                        whatsapp = true
                    )
                    if (missed.none { it.key == call.key }) {
                        missed += call
                        sbn.notification.contentIntent?.let { intents[call.key] = it }
                    }
                }
            }
        WhatsAppMissedHub.publish(WhatsAppMissedSnapshot(missed, outgoing), intents)
    }

    private fun isWhatsAppMissed(sbn: StatusBarNotification, preview: String, category: String): Boolean {
        if (sbn.isOngoing) return false
        if (category == Notification.CATEGORY_MISSED_CALL) return true
        val text = preview.lowercase().trim()
        if (text.length > 48) return false
        return text.contains("missed voice call") ||
            text.contains("missed video call") ||
            text.contains("missed group voice") ||
            text.contains("missed group video") ||
            text == "missed call"
    }

    private fun isWhatsAppOutgoing(sbn: StatusBarNotification, hay: String): Boolean {
        val category = sbn.notification.category.orEmpty()
        if (sbn.isOngoing && (category == Notification.CATEGORY_CALL || hay.contains("call"))) return true
        return hay.contains("outgoing voice") ||
            hay.contains("outgoing video") ||
            hay.contains("outgoing call") ||
            hay.contains("calling…") ||
            hay.contains("calling...") ||
            hay.contains("ringing")
    }

    private fun parse(sbn: StatusBarNotification): InboxItem? {
        val source = InboxSource.fromPackage(sbn.packageName) ?: return null
        val extras = sbn.notification.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
        val title = extras.char(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.char(Notification.EXTRA_TITLE)
            ?: style?.conversationTitle?.toString()
            ?: style?.user?.name?.toString()
        val preview = style?.messages?.lastOrNull()?.text?.toString()
            ?: extras.char(Notification.EXTRA_BIG_TEXT)
            ?: extras.char(Notification.EXTRA_TEXT)
            ?: extras.char(Notification.EXTRA_SUB_TEXT)
        if (title.isNullOrBlank() && preview.isNullOrBlank()) return null
        return InboxItem(
            key = sbn.key,
            source = source,
            title = title?.ifBlank { source.title } ?: source.title,
            preview = preview.orEmpty().trim().take(160),
            packageName = sbn.packageName,
            postedAt = sbn.postTime
        )
    }

    private fun android.os.Bundle.char(key: String): String? =
        getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }

    companion object {
        private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
    }

    private fun mediaToken(sbn: StatusBarNotification): MediaSession.Token? = runCatching {
        val extras = sbn.notification.extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        }
    }.getOrNull()
}

package com.lumen.launcher.badge

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Decides which active notifications contribute to launcher icon badge counts.
 * Group summaries are skipped when child notifications exist for the same group.
 */
object NotificationBadgeFilter {

    fun countsByPackage(
        notifications: List<StatusBarNotification>,
        selfPackage: String,
        ranking: NotificationListenerService.RankingMap? = null
    ): Map<String, Int> {
        val eligible = notifications.filter { shouldCount(it, selfPackage, ranking) }
        return eligible
            .groupBy { it.packageName }
            .mapValues { (_, pkgNotes) -> countForPackage(pkgNotes) }
            .filterValues { it > 0 }
    }

    private fun countForPackage(notes: List<StatusBarNotification>): Int {
        val byGroup = notes.groupBy { groupBucket(it) }
        var total = 0
        for (group in byGroup.values) {
            total += countGroup(group)
        }
        return total
    }

    private fun countGroup(group: List<StatusBarNotification>): Int {
        val children = group.filterNot { it.isGroupSummary() }
        if (children.isNotEmpty()) return children.size
        val summaries = group.filter { it.isGroupSummary() }
        if (summaries.isNotEmpty()) {
            return summaries.sumOf { sbn ->
                sbn.notification.number.coerceAtLeast(1)
            }.coerceAtLeast(summaries.size)
        }
        return group.size
    }

    /**
     * Uses Android's groupKey, which is identical for a summary and all its children —
     * including system auto-groups, where children carry an override group but no
     * Notification.group of their own. Ungrouped notifications get their own bucket.
     */
    private fun groupBucket(sbn: StatusBarNotification): String =
        if (sbn.isGroup) sbn.groupKey ?: sbn.key else sbn.key

    private fun StatusBarNotification.isGroupSummary(): Boolean {
        val flags = notification.flags
        return (flags and Notification.FLAG_GROUP_SUMMARY) != 0 ||
            (flags and FLAG_AUTOGROUP_SUMMARY) != 0
    }

    private fun shouldCount(
        sbn: StatusBarNotification,
        selfPackage: String,
        ranking: NotificationListenerService.RankingMap?
    ): Boolean {
        if (sbn.packageName == selfPackage) return false
        if (ranking != null) {
            val rank = NotificationListenerService.Ranking()
            if (ranking.getRanking(sbn.key, rank)) {
                if (rank.importance < NotificationManager.IMPORTANCE_LOW) return false
                if (rank.isSuspended) return false
                // Respect the user's / app's per-channel "Show notification dot" setting.
                if (!rank.canShowBadge()) return false
            }
        }
        val n = sbn.notification
        val category = n.category.orEmpty()
        if (category in PERSISTENT_CATEGORIES) return false
        if (sbn.isOngoing) {
            if (category in PERSISTENT_CATEGORIES) return false
            if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
            if (category == Notification.CATEGORY_CALL) return false
            // Ongoing chat / message notifications should still badge.
            if (category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_EMAIL) {
                return true
            }
            if (!sbn.isClearable) return false
        }
        if (sbn.isGroupSummary() && notificationBodyEmpty(n)) return false
        return true
    }

    private fun notificationBodyEmpty(n: Notification): Boolean {
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        return title.isNullOrEmpty() && text.isNullOrEmpty() && big.isNullOrEmpty()
    }

    /** @see android.app.Notification.FLAG_AUTOGROUP_SUMMARY */
    private const val FLAG_AUTOGROUP_SUMMARY = 0x00000400

    private val PERSISTENT_CATEGORIES = setOf(
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_NAVIGATION
    )
}

package com.lumen.launcher.badge

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Decides which active notifications contribute to launcher icon badge counts.
 * Group summaries are skipped when child notifications exist for the same group.
 */
object NotificationBadgeFilter {

    fun countsByPackage(
        notifications: Array<StatusBarNotification>,
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
            val children = group.filterNot { it.isGroupSummary() }
            val summaries = group.filter { it.isGroupSummary() }
            total += if (children.isNotEmpty()) children.size else summaries.size
        }
        return total
    }

    private fun groupBucket(sbn: StatusBarNotification): String {
        val group = sbn.notification.group?.takeIf { it.isNotBlank() }
            ?: sbn.groupKey?.takeIf { it.isNotBlank() }
        return group ?: sbn.key
    }

    private fun StatusBarNotification.isGroupSummary(): Boolean =
        (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

    private fun shouldCount(
        sbn: StatusBarNotification,
        selfPackage: String,
        ranking: NotificationListenerService.RankingMap?
    ): Boolean {
        if (sbn.packageName == selfPackage) return false
        if (ranking != null) {
            val rank = NotificationListenerService.Ranking()
            if (ranking.getRanking(sbn.key, rank)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !rank.canShowBadge()) return false
                if (rank.isSuspended) return false
            }
        }
        val n = sbn.notification
        if (sbn.isOngoing) {
            val category = n.category.orEmpty()
            if (category in PERSISTENT_CATEGORIES) return false
            if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
            if (category == Notification.CATEGORY_CALL) return false
        }
        if (n.flags and Notification.FLAG_LOCAL_ONLY != 0) return false
        val category = n.category.orEmpty()
        if (category in PERSISTENT_CATEGORIES) return false
        if (NotificationCompat.getLocalOnly(n)) return false
        if (n.extras.getBoolean("android.reduced.images", false)) return false
        return true
    }

    private val PERSISTENT_CATEGORIES = setOf(
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_NAVIGATION
    )
}

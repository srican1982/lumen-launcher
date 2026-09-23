package com.lumen.launcher.badge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canonical badge counts keyed by app package name. Updated only from
 * [com.lumen.launcher.inbox.LumenNotificationListener] — no polling.
 */
object NotificationBadgeRepository {

    private val _state = MutableStateFlow(BadgeState())
    val state: StateFlow<BadgeState> = _state.asStateFlow()

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val countsByPackage: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun rebuild(
        notifications: Array<StatusBarNotification>,
        selfPackage: String,
        ranking: NotificationListenerService.RankingMap? = null
    ) {
        val next = NotificationBadgeFilter.countsByPackage(notifications, selfPackage, ranking)
        publish(next, listenerConnected = true)
    }

    fun clear() {
        publish(emptyMap(), listenerConnected = false)
    }

    fun removePackage(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return
        val next = _counts.value.toMutableMap()
        if (next.remove(trimmed) != null) {
            publish(next, listenerConnected = _state.value.listenerConnected)
        }
    }

    private fun publish(counts: Map<String, Int>, listenerConnected: Boolean) {
        _counts.value = counts
        _state.value = BadgeState(countsByPackage = counts, listenerConnected = listenerConnected)
    }
}

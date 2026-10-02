package com.lumen.launcher.focus

import android.content.Context
import android.content.Intent

/**
 * Compatibility facade — delegates to [FocusSessionManager].
 * Prefer FocusSessionManager / FocusPolicyController for new code.
 */
object FocusSession {
    fun hasAccess(c: Context) = FocusPolicyController(c).hasAccess()

    suspend fun start(c: Context, until: Long, taskId: String): Boolean {
        val minutes = ((until - System.currentTimeMillis()) / 60_000L).toInt().coerceIn(5, 24 * 60)
        return FocusSessionManager.get(c).start(minutes)
    }

    suspend fun pause(c: Context): Long = FocusSessionManager.get(c).pause()

    suspend fun resume(c: Context, taskId: String): Long = FocusSessionManager.get(c).resume()

    suspend fun extend(c: Context, extraMs: Long, taskId: String): Long {
        val minutes = (extraMs / 60_000L).toInt().coerceAtLeast(1)
        return FocusSessionManager.get(c).extend(minutes)
    }

    fun pausedRemaining(c: Context): Long = FocusSessionManager.get(c).snapshotNow().pausedRemainingMs

    fun totalMs(c: Context): Long = FocusSessionManager.get(c).snapshotNow().totalMs

    suspend fun finish(c: Context, expected: Long? = null, announce: Boolean = false): Boolean {
        if (expected != null) {
            FocusSessionManager.get(c).onAlarm(expected)
            return true
        }
        return FocusSessionManager.get(c).end(announce)
    }

    suspend fun recover(c: Context) {
        FocusSessionManager.get(c).recover()
    }

    suspend fun receive(c: Context, intent: Intent) {
        if (intent.action == "com.lumen.launcher.FOCUS_FINISHED") {
            FocusSessionManager.get(c).onAlarm(intent.getLongExtra("until", -1))
        } else {
            FocusSessionManager.get(c).recover()
        }
    }
}

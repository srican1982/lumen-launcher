package com.lumen.launcher.focus

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.core.app.NotificationCompat
import com.lumen.launcher.LauncherActivity
import com.lumen.launcher.R
import com.lumen.launcher.data.LauncherPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns only Lumen's quiet rule. The phone's ringer mode is never overwritten. */
object FocusSession {
    private val mutex = Mutex()
    private const val FIRE = "com.lumen.launcher.FOCUS_FINISHED"
    private const val REQUEST = 81004
    private val conditionUri = Uri.parse("condition://com.lumen.launcher/focus")
    private fun ledger(c: Context) = c.getSharedPreferences("focus_session", Context.MODE_PRIVATE)
    fun hasAccess(c: Context) = c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun pending(c: Context, until: Long) = PendingIntent.getBroadcast(c, REQUEST,
        Intent(c, FocusReceiver::class.java).setAction(FIRE).putExtra("until", until),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(c: Context, until: Long) {
        c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pending(c, until))
    }

    suspend fun start(c: Context, until: Long, taskId: String): Boolean = mutex.withLock {
        val p = ledger(c)
        // A previous session must release its rule before a new one takes ownership.
        if (!restore(c)) return@withLock false
        try {
            check(p.edit().putLong("until", until).commit())
            // Arrange restoration before activating quiet mode.
            schedule(c, until)
            val nm = c.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 29) {
                val policy = ZenPolicy.Builder().disallowAllSounds().allowAlarms(true).allowMedia(true)
                    .hideAllVisualEffects().build()
                val rule = AutomaticZenRule("Lumen Focus", null, ComponentName(c, LauncherActivity::class.java),
                    conditionUri, policy, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
                val id = nm.addAutomaticZenRule(rule)
                if (!p.edit().putString("rule", id).commit()) {
                    nm.removeAutomaticZenRule(id)
                    error("Unable to save quiet-mode state")
                }
                nm.setAutomaticZenRuleState(id, Condition(conditionUri, "Focus session", Condition.STATE_TRUE))
            } else {
                check(p.edit().putInt("previous_filter", nm.currentInterruptionFilter).commit())
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
            LauncherPreferences(c).setFocus(until, taskId)
            true
        } catch (_: Exception) {
            restore(c)
            c.getSystemService(AlarmManager::class.java).cancel(pending(c, until))
            LauncherPreferences(c).setFocus(0, "")
            false
        }
    }

    /** Returns false if Android denied restoration; retain ownership data for retry. */
    private fun restore(c: Context): Boolean {
        val p = ledger(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        return try {
            val id = p.getString("rule", null)
            if (id != null && Build.VERSION.SDK_INT >= 29) {
                if (nm.getAutomaticZenRule(id) != null) check(nm.removeAutomaticZenRule(id))
            } else if (p.contains("previous_filter")) {
                // Preserve a manual DND change made during the session.
                if (FocusSessionRules.restoreLegacy(nm.currentInterruptionFilter, NotificationManager.INTERRUPTION_FILTER_NONE)) {
                    nm.setInterruptionFilter(p.getInt("previous_filter", NotificationManager.INTERRUPTION_FILTER_ALL))
                }
            }
            p.edit().clear().commit()
        } catch (_: Exception) { false }
    }

    suspend fun finish(c: Context, expected: Long? = null, announce: Boolean = false): Boolean = mutex.withLock {
        val until = ledger(c).getLong("until", 0)
        if (!FocusSessionRules.acceptFinish(until, expected, System.currentTimeMillis())) return@withLock true
        val restored = restore(c)
        c.getSystemService(AlarmManager::class.java).cancel(pending(c, until))
        LauncherPreferences(c).setFocus(0, "")
        if (announce && until > 0) notifyFinished(c, restored)
        restored
    }

    suspend fun recover(c: Context) {
        val expired = mutex.withLock {
            val until = ledger(c).getLong("until", 0)
            if (until == 0L) return@withLock null
            if (until <= System.currentTimeMillis()) return@withLock until
            try { schedule(c, until); null }
            catch (_: Exception) {
                // No reliable wakeup remains: release quiet mode immediately.
                val restored = restore(c)
                LauncherPreferences(c).setFocus(0, "")
                notifyFinished(c, restored)
                null
            }
        }
        if (expired != null) finish(c, expired, announce = true)
    }

    suspend fun receive(c: Context, intent: Intent) {
        if (intent.action == FIRE) finish(c, intent.getLongExtra("until", -1), announce = true)
        else recover(c)
    }

    private fun notifyFinished(c: Context, restored: Boolean) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel("focus_complete", "Focus completion", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(c, REQUEST, Intent(c, LauncherActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            nm.notify(REQUEST, NotificationCompat.Builder(c, channel.id).setSmallIcon(R.drawable.ic_alarm_notify)
                .setContentTitle("Focus finished")
                .setContentText(if (restored) "Take a breath. Your previous sound settings are back." else "Open Do Not Disturb settings to turn off Lumen Focus.")
                .setCategory(NotificationCompat.CATEGORY_ALARM).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}

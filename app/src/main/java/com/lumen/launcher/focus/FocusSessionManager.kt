package com.lumen.launcher.focus

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.lumen.launcher.LauncherActivity
import com.lumen.launcher.R
import com.lumen.launcher.data.LauncherPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns Focus timer + coordinates [FocusPolicyController].
 * Session state is independent of which Space is visible.
 */
class FocusSessionManager(
    private val context: Context,
    private val policy: FocusPolicyController = FocusPolicyController(context),
    private val peopleRepo: FocusAllowedPeopleRepository = FocusAllowedPeopleRepository.get(context)
) {
    private val mutex = Mutex()
    private val ledger = context.getSharedPreferences(LEDGER, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<FocusSessionSnapshot> = _snapshot.asStateFlow()

    fun hasPolicyAccess(): Boolean = policy.hasAccess()
    fun snapshotNow(): FocusSessionSnapshot = _snapshot.value
    fun refreshFromDisk() {
        _snapshot.value = readSnapshot()
    }

    suspend fun start(minutes: Int): Boolean = mutex.withLock {
        val mins = minutes.coerceIn(5, 24 * 60)
        // Release any previous ownership first.
        if (!finishLocked(announce = false)) return@withLock false
        val now = System.currentTimeMillis()
        val until = now + mins * 60_000L
        val people = peopleRepo.peopleNow()
        val settings = peopleRepo.settingsNow()
        if (!policy.apply(people, settings)) return@withLock false
        try {
            schedule(until)
            check(
                ledger.edit()
                    .putLong(KEY_UNTIL, until)
                    .putLong(KEY_STARTED, now)
                    .putLong(KEY_TOTAL, until - now)
                    .remove(KEY_PAUSED)
                    .commit()
            )
            peopleRepo.setLastDuration(mins)
            LauncherPreferences(context).setFocus(until, "")
            publish()
            true
        } catch (_: Exception) {
            policy.restore()
            cancelAlarm(until)
            LauncherPreferences(context).setFocus(0, "")
            publish()
            false
        }
    }

    suspend fun pause(): Long = mutex.withLock {
        val until = ledger.getLong(KEY_UNTIL, 0)
        val already = ledger.getLong(KEY_PAUSED, 0)
        if (already > 0) return@withLock already
        if (until <= 0L) return@withLock 0L
        val remaining = (until - System.currentTimeMillis()).coerceAtLeast(0L)
        if (remaining <= 0L) return@withLock 0L
        cancelAlarm(until)
        check(ledger.edit().putLong(KEY_PAUSED, remaining).putLong(KEY_UNTIL, 0).commit())
        LauncherPreferences(context).setFocus(0, "")
        publish()
        remaining
    }

    suspend fun resume(): Long = mutex.withLock {
        val remaining = ledger.getLong(KEY_PAUSED, 0)
        if (remaining <= 0L) return@withLock 0L
        val until = System.currentTimeMillis() + remaining
        return@withLock try {
            schedule(until)
            check(ledger.edit().putLong(KEY_UNTIL, until).remove(KEY_PAUSED).commit())
            LauncherPreferences(context).setFocus(until, "")
            publish()
            until
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun extend(extraMinutes: Int): Long = mutex.withLock {
        val add = extraMinutes.coerceIn(1, 120) * 60_000L
        val paused = ledger.getLong(KEY_PAUSED, 0)
        if (paused > 0L) {
            val next = paused + add
            check(
                ledger.edit()
                    .putLong(KEY_PAUSED, next)
                    .putLong(KEY_TOTAL, ledger.getLong(KEY_TOTAL, next) + add)
                    .commit()
            )
            publish()
            return@withLock 0L
        }
        val until = ledger.getLong(KEY_UNTIL, 0)
        if (until <= System.currentTimeMillis()) return@withLock 0L
        val next = until + add
        return@withLock try {
            cancelAlarm(until)
            schedule(next)
            check(
                ledger.edit()
                    .putLong(KEY_UNTIL, next)
                    .putLong(KEY_TOTAL, ledger.getLong(KEY_TOTAL, next - System.currentTimeMillis()) + add)
                    .commit()
            )
            LauncherPreferences(context).setFocus(next, "")
            publish()
            next
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun end(announce: Boolean = false): Boolean = mutex.withLock {
        finishLocked(announce)
    }

    suspend fun onAlarm(expectedUntil: Long) {
        mutex.withLock {
            val until = ledger.getLong(KEY_UNTIL, 0)
            if (!FocusSessionRules.acceptFinish(until, expectedUntil, System.currentTimeMillis())) return@withLock
            finishLocked(announce = true)
        }
    }

    suspend fun recover() {
        val expired = mutex.withLock {
            val until = ledger.getLong(KEY_UNTIL, 0)
            val paused = ledger.getLong(KEY_PAUSED, 0)
            if (paused > 0L) {
                publish()
                return@withLock null
            }
            if (until == 0L) {
                publish()
                return@withLock null
            }
            if (until <= System.currentTimeMillis()) return@withLock until
            try {
                schedule(until)
                publish()
                null
            } catch (_: Exception) {
                finishLocked(announce = true)
                null
            }
        }
        if (expired != null) {
            mutex.withLock { finishLocked(announce = true) }
        }
    }

    fun capabilityNote(): String =
        policy.capabilityNote(peopleRepo.peopleNow(), peopleRepo.settingsNow())

    private suspend fun finishLocked(announce: Boolean): Boolean {
        val until = ledger.getLong(KEY_UNTIL, 0)
        val hadSession = until > 0 || ledger.getLong(KEY_PAUSED, 0) > 0 || ledger.contains(KEY_STARTED)
        cancelAlarm(until)
        val restored = policy.restore()
        ledger.edit().clear().apply()
        LauncherPreferences(context).setFocus(0, "")
        publish()
        if (announce && hadSession) notifyFinished(restored)
        return restored
    }

    private fun publish() {
        _snapshot.value = readSnapshot()
    }

    private fun readSnapshot(): FocusSessionSnapshot {
        val until = ledger.getLong(KEY_UNTIL, 0)
        val paused = ledger.getLong(KEY_PAUSED, 0)
        val started = ledger.getLong(KEY_STARTED, 0)
        val total = ledger.getLong(KEY_TOTAL, 0)
        return FocusSessionSnapshot(
            until = until,
            startedAt = started,
            totalMs = total,
            pausedRemainingMs = paused,
            active = until > System.currentTimeMillis() || paused > 0L
        )
    }

    private fun pending(until: Long) = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, FocusReceiver::class.java).setAction(FIRE).putExtra("until", until),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(until: Long) {
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pending(until))
    }

    private fun cancelAlarm(until: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(until))
    }

    private fun notifyFinished(restored: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            "focus_complete",
            "Focus completion",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context,
            REQUEST,
            Intent(context, LauncherActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            nm.notify(
                REQUEST,
                NotificationCompat.Builder(context, channel.id)
                    .setSmallIcon(R.drawable.ic_alarm_notify)
                    .setContentTitle("Focus finished")
                    .setContentText(
                        if (restored) "Take a breath. Your previous sound settings are back."
                        else "Open Do Not Disturb settings to turn off Lumen Focus."
                    )
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    companion object {
        private const val LEDGER = "focus_session"
        private const val FIRE = "com.lumen.launcher.FOCUS_FINISHED"
        private const val REQUEST = 81004
        private const val KEY_UNTIL = "until"
        private const val KEY_STARTED = "started"
        private const val KEY_TOTAL = "total"
        private const val KEY_PAUSED = "paused_remaining"

        @Volatile private var instance: FocusSessionManager? = null
        fun get(context: Context): FocusSessionManager =
            instance ?: synchronized(this) {
                instance ?: FocusSessionManager(context.applicationContext).also { instance = it }
            }
    }
}

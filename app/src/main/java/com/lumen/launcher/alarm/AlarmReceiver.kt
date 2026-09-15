package com.lumen.launcher.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lumen.launcher.R
import com.lumen.launcher.data.LauncherPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.UUID

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmIntents.ACTION_FIRE -> onFire(context, intent)
            AlarmIntents.ACTION_DISMISS -> {
                AlarmTonePlayer.stop()
                cancelNotice(context)
            }
            AlarmIntents.ACTION_SNOOZE -> {
                AlarmTonePlayer.stop()
                cancelNotice(context)
                snooze(context, intent)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> AlarmScheduler.rescheduleAll(context)
        }
    }

    private fun onFire(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            val tone = intent.getStringExtra(AlarmIntents.EXTRA_TONE) ?: AlarmTones.AURA
            AlarmTonePlayer.start(context, tone, loop = true)
            runCatching {
                context.startActivity(
                    Intent(context, AlarmActivity::class.java).apply {
                        putExtras(intent)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    }
                )
            }
            notify(context, intent)
            consume(context, intent)
        } finally {
            pending.finish()
        }
    }

    private fun consume(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmIntents.EXTRA_ID) ?: return
        val daily = intent.getBooleanExtra(AlarmIntents.EXTRA_DAILY, false)
        val prefs = LauncherPreferences(context)
        runBlocking {
            val stored = prefs.state.first()
            val next = stored.alarms.map { alarm ->
                if (alarm.id != id) alarm
                else if (daily) alarm
                else alarm.copy(enabled = false)
            }
            prefs.setAlarms(next)
            val still = next.find { it.id == id }
            if (still != null && still.enabled && still.daily) {
                AlarmScheduler.schedule(context, still, stored.alarmTone)
            }
        }
    }

    private fun snooze(context: Context, intent: Intent) {
        val prefs = LauncherPreferences(context)
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 10) }
        val snoozed = LumenAlarm(
            id = UUID.randomUUID().toString(),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            enabled = true,
            daily = false,
            label = "Snooze"
        )
        runBlocking {
            val stored = prefs.state.first()
            prefs.setAlarms(stored.alarms + snoozed)
            AlarmScheduler.schedule(context, snoozed, stored.alarmTone)
        }
    }

    private fun notify(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                }
            )
        }
        val hour = intent.getIntExtra(AlarmIntents.EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(AlarmIntents.EXTRA_MINUTE, 0)
        val title = LumenAlarm(hour = hour, minute = minute).displayTime()
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val ring = PendingIntent.getActivity(
            context,
            71,
            Intent(context, AlarmActivity::class.java).apply {
                putExtras(intent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            piFlags
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            72,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_DISMISS).putExtras(intent),
            piFlags
        )
        val snoozePi = PendingIntent.getBroadcast(
            context,
            73,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_SNOOZE).putExtras(intent),
            piFlags
        )
        nm.notify(
            NOTICE_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm_notify)
                .setContentTitle(title)
                .setContentText(intent.getStringExtra(AlarmIntents.EXTRA_LABEL).orEmpty().ifBlank { "Lumen alarm" })
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(ring, true)
                .setContentIntent(ring)
                .addAction(0, "Snooze", snoozePi)
                .addAction(0, "Dismiss", dismiss)
                .build()
        )
    }

    companion object {
        private const val CHANNEL = "lumen_alarms"
        const val NOTICE_ID = 71001

        fun cancelNotice(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTICE_ID)
        }
    }
}

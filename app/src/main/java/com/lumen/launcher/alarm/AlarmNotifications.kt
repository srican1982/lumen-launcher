package com.lumen.launcher.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lumen.launcher.R

object AlarmNotifications {
    fun notification(context: Context, intent: Intent): android.app.Notification {
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
        return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm_notify)
                .setContentTitle(title)
                .setContentText(intent.getStringExtra(AlarmIntents.EXTRA_LABEL).orEmpty().ifBlank { "Lumen alarm" })
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .apply { if (AlarmScheduler.canFullScreen(context)) setFullScreenIntent(ring, true) }
                .setContentIntent(ring)
                .addAction(0, "Snooze", snoozePi)
                .addAction(0, "Dismiss", dismiss)
                .build()
    }

    const val NOTICE_ID = 71001
    private const val CHANNEL = "lumen_alarms"
    fun cancelNotice(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTICE_ID)
    }
}

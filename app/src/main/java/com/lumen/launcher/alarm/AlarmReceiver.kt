package com.lumen.launcher.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.core.content.ContextCompat
import com.lumen.launcher.data.LauncherPreferences
import kotlinx.coroutines.*
import java.time.ZonedDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    AlarmIntents.ACTION_FIRE -> {
                        val id = intent.getStringExtra(AlarmIntents.EXTRA_ID) ?: return@launch
                        val alarm = AlarmStore.read(context).firstOrNull { it.id == id && it.enabled } ?: return@launch
                        ContextCompat.startForegroundService(context, Intent(context, AlarmRingService::class.java)
                            .setAction(AlarmIntents.ACTION_FIRE).putExtras(intent))
                        val next = AlarmStore.update(context) { list -> list.map { if (it.id == id && !it.daily) it.copy(enabled = false) else it } }
                        if (context.getSystemService(UserManager::class.java).isUserUnlocked) LauncherPreferences(context).setAlarms(next)
                        if (alarm.daily) AlarmScheduler.schedule(context, alarm, AlarmStore.tone(context))
                    }
                    AlarmIntents.ACTION_DISMISS, AlarmIntents.ACTION_SNOOZE -> {
                        context.stopService(Intent(context, AlarmRingService::class.java))
                        AlarmNotifications.cancelNotice(context)
                        if (intent.action == AlarmIntents.ACTION_SNOOZE) {
                            val at = ZonedDateTime.now().plusMinutes(10)
                            val alarm = LumenAlarm(hour = at.hour, minute = at.minute, label = "Snooze")
                            val next = AlarmStore.update(context) { it + alarm }
                            if (context.getSystemService(UserManager::class.java).isUserUnlocked) LauncherPreferences(context).setAlarms(next)
                            AlarmScheduler.schedule(context, alarm, AlarmStore.tone(context))
                        }
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED, android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> AlarmScheduler.rescheduleAll(context)
                }
            } finally { result.finish() }
        }
    }
}

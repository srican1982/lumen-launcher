package com.lumen.launcher.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import com.lumen.launcher.data.LauncherPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object AlarmScheduler {

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun requestExactAccess(context: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    fun schedule(context: Context, alarm: LumenAlarm, tone: String) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java)
        val trigger = alarm.nextTriggerMs()
        val fire = pending(context, alarm, tone, AlarmIntents.ACTION_FIRE)
        val show = PendingIntent.getActivity(
            context,
            alarm.id.hashCode() + 17,
            Intent(context, AlarmActivity::class.java).apply {
                putExtras(fireIntent(context, alarm, tone).extras!!)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), fire)
        }
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val dummy = LumenAlarm(id = id, hour = 0, minute = 0)
        am.cancel(pending(context, dummy, AlarmTones.AURA, AlarmIntents.ACTION_FIRE))
    }

    fun rescheduleAll(context: Context) {
        val stored = runBlocking { LauncherPreferences(context).state.first() }
        stored.alarms.forEach { schedule(context, it, stored.alarmTone) }
    }

    fun fireIntent(context: Context, alarm: LumenAlarm, tone: String): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_FIRE
            putExtra(AlarmIntents.EXTRA_ID, alarm.id)
            putExtra(AlarmIntents.EXTRA_HOUR, alarm.hour)
            putExtra(AlarmIntents.EXTRA_MINUTE, alarm.minute)
            putExtra(AlarmIntents.EXTRA_LABEL, alarm.label)
            putExtra(AlarmIntents.EXTRA_DAILY, alarm.daily)
            putExtra(AlarmIntents.EXTRA_TONE, tone)
        }
    }

    private fun pending(context: Context, alarm: LumenAlarm, tone: String, action: String): PendingIntent {
        val intent = fireIntent(context, alarm, tone).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

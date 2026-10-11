package com.lumen.launcher.alarm

import android.app.Service
import android.app.NotificationManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.lumen.launcher.R

/** Exact-alarm exemption permits this foreground media-playback service to start at the alarm. */
class AlarmRingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeout = Runnable {
        stopForeground(STOP_FOREGROUND_REMOVE)
        AlarmTonePlayer.stop()
        getSystemService(NotificationManager::class.java).notify(AlarmNotifications.NOTICE_ID + 1,
            NotificationCompat.Builder(this, "lumen_alarms").setSmallIcon(R.drawable.ic_alarm_notify)
                .setContentTitle("Missed alarm").setContentText("Your Lumen alarm stopped after 10 minutes.")
                .setAutoCancel(true).build())
        stopSelf()
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        startForeground(AlarmNotifications.NOTICE_ID, AlarmNotifications.notification(this, intent))
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lumen:alarm").apply {
                acquire(10 * 60_000L)
            }
        }
        AlarmTonePlayer.start(this, intent.getStringExtra(AlarmIntents.EXTRA_TONE) ?: AlarmTones.AURA, loop = true)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 10 * 60_000L)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        AlarmTonePlayer.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}

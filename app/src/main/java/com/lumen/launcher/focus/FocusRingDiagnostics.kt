package com.lumen.launcher.focus

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build

object FocusRingDiagnostics {
    fun read(context: Context): String = runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val lines = mutableListOf<String>()
        lines += when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Phone is in Silent mode: calls will not ring."
            AudioManager.RINGER_MODE_VIBRATE -> "Phone is in Vibrate mode: calls will not ring audibly."
            else -> "Phone is in Sound mode."
        }
        lines += "Ring volume: ${audio.getStreamVolume(AudioManager.STREAM_RING)} / ${audio.getStreamMaxVolume(AudioManager.STREAM_RING)}."
        if (!FocusCallAccess.ready(context)) lines += "Lumen call-screening or Contacts access is missing."
        when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE, NotificationManager.INTERRUPTION_FILTER_ALARMS -> lines += "Android DND currently blocks ordinary call sounds. Check other Modes/DND rules."
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> if (Build.VERSION.SDK_INT >= 30) {
                val policy = nm.consolidatedNotificationPolicy
                if (policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS == 0) lines += "The combined Android DND policy blocks calls."
                else if (policy.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED) lines += "The combined Android DND policy allows favorites only. Another rule may block selected non-favorites."
                else lines += "Android DND permits ${if (policy.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS) "contacts" else "callers"}; Lumen screens the selected list separately."
            }
            else -> lines += "Android DND is not blocking call sounds."
        }
        lines.joinToString("\n\n")
    }.getOrElse { "Sound settings could not be read. Check Android Sound and Do Not Disturb settings." }
}
